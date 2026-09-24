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
 * An axe hit disables a shield for a few seconds, which is what makes this worth doing - a sword
 * swing just bounces off. It only fires while you're actually holding attack, and only against a
 * player who currently has a shield up, so it never touches your hotbar otherwise.
 *
 * The swap is synced to the server by hand: {@code MultiPlayerGameMode} only re-sends the carried
 * item when it next attacks or interacts, so without an explicit packet the server would go on
 * believing you're holding the axe after we've swapped back.
 */
public final class ShieldBreakerModule extends Module {

	private static final int HOTBAR_SIZE = Inventory.SELECTION_SIZE;

	private final Setting.DoubleSetting range = addDouble("Range", 4.0, 1.0, 6.0, 0.1);
	private final Setting.DoubleSetting swapBackDelay = addDouble("Swap Back (ticks)", 4.0, 1.0, 20.0, 1.0);
	private final Setting.DoubleSetting cooldownSetting = addDouble("Cooldown (ticks)", 10.0, 0.0, 40.0, 1.0);

	/** Slot to return to once the axe hit is done, or -1 when we're not mid-swap. */
	private int previousSlot = -1;
	private int swapBackTimer;
	private int cooldown;

	public ShieldBreakerModule() {
		super("Shield Breaker",
				"Swaps to an axe for one hit when you attack a blocking player, then swaps back.",
				ModuleCategory.COMBAT);
	}

	@Override
	protected void onDisable() {
		if (previousSlot >= 0) {
			restore(previousSlot);
		}
		previousSlot = -1;
		swapBackTimer = 0;
		cooldown = 0;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			return;
		}

		// Mid-swap: hold the axe just long enough for the hit to land, then hand the old item back.
		if (previousSlot >= 0) {
			if (--swapBackTimer <= 0) {
				int slot = previousSlot;
				previousSlot = -1;
				restore(slot);
			}
			return;
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		// Only react to the player's own attack - this never attacks on its own.
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

		previousSlot = player.getInventory().getSelectedSlot();
		swapToAxe(player, axeSlot);

		// attack() calls ensureHasSentCarriedItem() internally, so the "I'm now holding slot N"
		// packet goes out before the attack packet and the server resolves the hit with the axe.
		client.gameMode.attack(player, victim);
		player.swing(InteractionHand.MAIN_HAND);

		swapBackTimer = swapBackDelay.get().intValue();
		cooldown = cooldownSetting.get().intValue();
	}

	/** Puts the axe in hand. {@code gameMode.attack} is what tells the server about the new slot. */
	private static void swapToAxe(LocalPlayer player, int axeSlot) {
		player.getInventory().setSelectedSlot(axeSlot);
	}

	/** Returns to the originally held slot, telling the server about it ourselves. */
	private void restore(int slot) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		player.getInventory().setSelectedSlot(slot);
		ClientPacketListener connection = client.getConnection();
		if (connection != null) {
			connection.send(new ServerboundSetCarriedItemPacket(slot));
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
