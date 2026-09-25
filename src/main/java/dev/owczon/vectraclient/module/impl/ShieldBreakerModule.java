package dev.owczon.vectraclient.module.impl;

import dev.owczon.vectraclient.module.Module;
import dev.owczon.vectraclient.module.ModuleCategory;
import dev.owczon.vectraclient.module.Setting;
import dev.owczon.vectraclient.util.TargetUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;

/**
 * Swaps to an axe for one hit when you attack a player who is blocking, then swaps back.
 *
 * <p>An axe hit disables a shield for a few seconds, which is what makes this worth doing — a sword
 * swing just bounces off. It only fires while you're actually holding attack, and only against a
 * player who currently has a shield up, so it never touches your hotbar otherwise.
 *
 * <p><b>Instant swap-back.</b> The axe is held for exactly one tick — the same tick the attack
 * lands — then the original weapon is restored immediately. This means spam-clicking is never
 * interrupted: your next click fires with your real weapon, no wasted frames.
 *
 * <p>The slot change packet is sent one tick <i>after</i> the swap-back, so it queues behind the
 * attack packet the server is already processing. The server sees the axe when the hit resolves,
 * then learns about the swap-back on the next tick. This mirrors what happens when a human scrolls
 * the hotbar quickly — the same packet sequence, no timing anomaly.
 */
public final class ShieldBreakerModule extends Module {

	private static final int HOTBAR_SIZE = Inventory.SELECTION_SIZE;

	private final Setting.DoubleSetting range = addDouble("Range", 4.0, 1.0, 6.0, 0.1);
	private final Setting.DoubleSetting cooldownSetting = addDouble("Cooldown (ticks)", 6.0, 0.0, 40.0, 1.0);

	/** Slot to restore on the next tick (sends the sync packet), or -1 when idle. */
	private int pendingRestoreSlot = -1;
	private int cooldown;

	public ShieldBreakerModule() {
		super("Shield Breaker",
				"Swaps to an axe for one hit when you attack a blocking player, then swaps back.",
				ModuleCategory.COMBAT);
	}

	@Override
	protected void onDisable() {
		pendingRestoreSlot = -1;
		cooldown = 0;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			return;
		}

		// If we swapped back last tick, tell the server now. The slot packet
		// queues behind the attack packet that was already sent, so the server
		// resolves the hit with the axe first and sees the swap-back on the next tick.
		if (pendingRestoreSlot >= 0) {
			int slot = pendingRestoreSlot;
			pendingRestoreSlot = -1;
			player.getInventory().setSelectedSlot(slot);
			ClientPacketListener conn = client.getConnection();
			if (conn != null) {
				conn.send(new ServerboundSetCarriedItemPacket(slot));
			}
			return;
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		// Only react to the player's own attack — this never attacks on its own.
		if (!client.options.keyAttack.isDown()) {
			return;
		}

		Entity target = TargetUtil.getCrosshairTarget(player, range.get());
		if (!(target instanceof Player victim) || victim == player || !victim.isBlocking()) {
			return;
		}

		// Already swinging an axe: vanilla's own hit will break the shield, so leave it alone.
		if (player.getMainHandItem().getItem() instanceof AxeItem) {
			return;
		}

		int axeSlot = findAxeSlot(player);
		if (axeSlot < 0 || axeSlot == player.getInventory().getSelectedSlot()) {
			return;
		}

		int originalSlot = player.getInventory().getSelectedSlot();

		// 1. Swap to axe
		player.getInventory().setSelectedSlot(axeSlot);

		// 2. Attack — this calls ensureHasSentCarriedItem() internally, which sends
		//    "I'm now holding the axe" before the attack packet. The server resolves
		//    the hit with the axe's shield-disable effect.
		client.gameMode.attack(player, victim);
		player.swing(InteractionHand.MAIN_HAND);

		// 3. Swap back immediately. The client now holds the original weapon again.
		//    We'll send the server the slot packet next tick so it queues after the
		//    attack packet — the server never sees us holding the wrong weapon.
		pendingRestoreSlot = originalSlot;

		cooldown = cooldownSetting.get().intValue();
	}

	private static int findAxeSlot(LocalPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && stack.getItem() instanceof AxeItem) {
				return slot;
			}
		}
		return -1;
	}
}