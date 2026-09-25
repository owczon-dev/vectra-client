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
 * <p>An axe hit disables a shield for a few seconds — a sword swing just bounces off. It only
 * fires while you're actually holding attack and the crosshair is on a blocking player.
 *
 * <p><b>Instant swap-back, no cooldown.</b> The axe is held for exactly one tick, the attack
 * packet goes out with the axe, and the original weapon is restored on the client immediately.
 * The server-side slot sync packet is sent in the same Netty write (right after the attack
 * packet), so the server resolves the hit with the axe and learns about the swap-back on the
 * next tick. There is no Shield Breaker cooldown — the weapon's own attack speed is the only
 * throttle. Spam-clicking works without interruption.
 */
public final class ShieldBreakerModule extends Module {

	private static final int HOTBAR_SIZE = Inventory.SELECTION_SIZE;

	private final Setting.DoubleSetting range = addDouble("Range", 4.0, 1.0, 6.0, 0.1);

	public ShieldBreakerModule() {
		super("Shield Breaker",
				"Swaps to an axe for one hit when you attack a blocking player, then swaps back.",
				ModuleCategory.COMBAT);
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
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

		// 1. Swap to axe (client-side only)
		player.getInventory().setSelectedSlot(axeSlot);

		// 2. Attack — this calls ensureHasSentCarriedItem() internally, which queues
		//    "I'm now holding the axe" before the attack packet. The server resolves
		//    the hit with the axe's shield-disable effect.
		client.gameMode.attack(player, victim);
		player.swing(InteractionHand.MAIN_HAND);

		// 3. Swap back immediately on the client.
		player.getInventory().setSelectedSlot(originalSlot);

		// 4. Tell the server about the swap-back. The packet is queued right after the
		//    attack packet in the same Netty write, so the server processes the attack
		//    with the axe first, then sees the slot change next tick.
		ClientPacketListener conn = client.getConnection();
		if (conn != null) {
			conn.send(new ServerboundSetCarriedItemPacket(originalSlot));
		}
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