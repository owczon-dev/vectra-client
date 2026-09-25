package dev.owczon.vectraclient.module.impl;

import dev.owczon.vectraclient.module.Module;
import dev.owczon.vectraclient.module.ModuleCategory;
import dev.owczon.vectraclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Places a totem of undying into the offhand when the player's health drops below a threshold.
 *
 * <h3>Auto mode</h3>
 * Swaps the totem into the offhand via a single {@code SWAP} container action on
 * {@code containerId=0} (the always-available player inventory menu). No GUI opens — the swap
 * is instant and invisible. Works whether the totem is in the hotbar or the main inventory.
 *
 * <h3>Silent mode</h3>
 * Opens the actual inventory screen, performs the same swap, and leaves the screen open briefly
 * so the player sees it happen — exactly what a manual interaction looks like.
 *
 * <p>The swap goes through {@code MultiPlayerGameMode#handleContainerInput}, the same code path
 * that vanilla's own inventory screen uses for every click. The server sees a normal inventory
 * SWAP action on {@code containerId=0}, which is always valid without needing an explicit
 * {@code OPEN_INVENTORY} packet first — the player's inventory container is always open
 * server-side.
 */
public final class OffhandModule extends Module {

	private static final int SLOT_OFFHAND = Inventory.SLOT_OFFHAND; // 40
	private static final int HOTBAR_MENU_OFFSET = 36; // inv 0-8 → menu 36-44

	private final Setting.DoubleSetting healthThreshold = addDouble("Health Threshold", 4.0, 1.0, 20.0, 0.5);
	private final Setting.DoubleSetting delaySetting = addDouble("Delay (ticks)", 2.0, 0.0, 20.0, 1.0);
	private final Setting.BooleanSetting silentMode = addBoolean("Silent", false);

	private int ticksUntilNextSwap;

	public OffhandModule() {
		super("Offhand",
				"Swaps a totem of undying into your offhand when your health drops low.",
				ModuleCategory.UTILITY);
	}

	@Override
	protected void onDisable() {
		ticksUntilNextSwap = 0;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || player.isDeadOrDying()) {
			return;
		}

		if (ticksUntilNextSwap > 0) {
			ticksUntilNextSwap--;
			return;
		}

		// Already holding a totem — nothing to do.
		if (isTotem(player.getInventory().getItem(SLOT_OFFHAND))) {
			return;
		}

		// Not low enough yet.
		if (player.getHealth() >= healthThreshold.get()) {
			return;
		}

		int invSlot = findTotemSlot(player);
		if (invSlot < 0) {
			return;
		}

		// Map inventory slot → InventoryMenu slot.
		// Hotbar (0-8) → menu slots 36-44.  Main inventory (9-35) → menu slots 9-35.
		int menuSlot = invSlot <= 8 ? invSlot + HOTBAR_MENU_OFFSET : invSlot;

		if (silentMode.get()) {
			// Open the inventory screen first so the player sees the swap.
			player.sendOpenInventory();
		}

		// handleContainerInput validates containerId, calls menu.clicked() for the local swap,
		// diffs the slot states, and sends the full ServerboundContainerClickPacket with the
		// correct stateId — the exact same path vanilla's inventory screen uses for every click.
		client.gameMode.handleContainerInput(
				player.containerMenu.containerId,
				menuSlot,
				SLOT_OFFHAND,
				ContainerInput.SWAP,
				player);

		if (silentMode.get()) {
			// Leave the screen open for a moment so it's visible, then close.
			// The swap is already done; the screen is just cosmetic.
			player.closeContainer();
		}

		ticksUntilNextSwap = delaySetting.get().intValue();
	}

	/** Returns the inventory slot index of the first totem of undying, or -1. */
	private static int findTotemSlot(LocalPlayer player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i <= 35; i++) {
			if (isTotem(inv.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isTotem(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING;
	}
}