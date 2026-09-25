package dev.owczon.vectraclient.module.impl;

import dev.owczon.vectraclient.module.Module;
import dev.owczon.vectraclient.module.ModuleCategory;
import dev.owczon.vectraclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;

/**
 * Smoothly pulls the crosshair towards the nearest player within reach.
 *
 * <h3>Weapon-only</h3>
 * Only activates when the player is holding a melee weapon (an item whose {@code TOOL}
 * component grants attack-damage bonus — swords, axes, maces, tridents, etc.).
 *
 * <h3>Potion / projectile safety</h3>
 * If the player swaps to a throwable item (splash potion, ender pearl, snowball, etc.)
 * the aimbot immediately stops, with a short cooldown after the swap to avoid accidentally
 * throwing a potion at an enemy during a fast hotbar swap.
 *
 * <h3>Smooth rotation</h3>
 * The angular delta is applied per-tick using a configurable smooth factor. Pitch (vertical)
 * movement is intentionally dampened to 30% of the yaw factor, keeping the crosshair at
 * body height and avoiding unnecessary up/down jitter.
 */
public final class AimModule extends Module {

	private final Setting.DoubleSetting reach = addDouble("Reach", 4.5, 1.0, 6.0, 0.1);
	private final Setting.DoubleSetting smooth = addDouble("Smooth", 0.5, 0.05, 1.0, 0.05);
	private final Setting.BooleanSetting requireAim = addBoolean("Require Aim Key", false);

	/** Ticks to stay idle after the held item changes (to survive fast hotbar swaps). */
	private static final int ITEM_SWAP_COOLDOWN_TICKS = 4;

	/** Pitch smooth is multiplied by this fraction of the yaw smooth. */
	private static final float PITCH_DAMPEN = 0.3F;

	private int swapCooldown;
	private ItemStack lastMainHand = ItemStack.EMPTY;

	public AimModule() {
		super("Aim",
				"Smoothly pulls your crosshair towards the nearest player within reach.",
				ModuleCategory.COMBAT);
	}

	@Override
	protected void onDisable() {
		swapCooldown = 0;
		lastMainHand = ItemStack.EMPTY;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || player.isDeadOrDying()) {
			return;
		}

		// --- Item-swap cooldown ---
		// Detect fast hotbar swaps. If the player just switched items, wait a few ticks
		// so we don't track onto a target while a potion ender pearl is in hand.
		ItemStack current = player.getMainHandItem();
		if (!ItemStack.isSameItemSameComponents(current, lastMainHand)) {
			lastMainHand = current.copy();
			swapCooldown = ITEM_SWAP_COOLDOWN_TICKS;
		}
		if (swapCooldown > 0) {
			swapCooldown--;
			return;
		}

		// --- Weapon-only gate ---
		if (!isWeapon(current)) {
			return;
		}

		// --- Projectile safety ---
		// If holding a throwable (potion, pearl, snowball, bow, etc.), don't aim.
		if (isThrowable(current)) {
			return;
		}

		if (requireAim.get() && !client.options.keyAttack.isDown()) {
			return;
		}

		Player target = findTarget(player);
		if (target == null) {
			return;
		}

		// Aim at mid-torso (40% of height — slightly below centre to further reduce pitch).
		double targetX = target.getX();
		double targetY = target.getY() + target.getBbHeight() * 0.4;
		double targetZ = target.getZ();

		double dx = targetX - player.getX();
		double dy = targetY - (player.getY() + player.getEyeHeight());
		double dz = targetZ - player.getZ();

		double horizontalDist = Math.sqrt(dx * dx + dz * dz);

		float desiredYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		float desiredPitch = (float) -(Mth.atan2(dy, horizontalDist) * (180.0 / Math.PI));

		float currentYaw = player.getYRot();
		float currentPitch = player.getXRot();

		float deltaYaw = Mth.wrapDegrees(desiredYaw - currentYaw);
		float deltaPitch = desiredPitch - currentPitch;

		float yawFactor = smooth.get().floatValue();
		float pitchFactor = yawFactor * PITCH_DAMPEN;

		// Minimum step: if the delta is tiny, skip it entirely to avoid micro-jitter.
		if (Math.abs(deltaYaw) < 0.1F && Math.abs(deltaPitch) < 0.1F) {
			return;
		}

		player.setYRot(currentYaw + deltaYaw * yawFactor);
		player.setXRot(Mth.clamp(currentPitch + deltaPitch * pitchFactor, -90.0F, 90.0F));
	}

	/* ---------- helpers ---------- */

	/**
	 * Returns true if the item is a melee weapon — sword, axe, mace, or trident.
	 *
	 * In 26.2, {@code SwordItem} was merged into a generic item with a {@code TOOL}
	 * component, so we check class names for axe/mace/trident and the TOOL component
	 * for everything else, filtering out mining tools by class name.
	 */
	private static boolean isWeapon(ItemStack stack) {
		if (stack.isEmpty()) return false;
		Item item = stack.getItem();
		String className = item.getClass().getSimpleName();
		// Direct class checks for known weapon types.
		if (item instanceof net.minecraft.world.item.AxeItem) return true;
		if (item instanceof net.minecraft.world.item.MaceItem) return true;
		if (item instanceof net.minecraft.world.item.TridentItem) return true;
		// For swords and future weapons: have a TOOL component but aren't mining tools.
		if (stack.has(DataComponents.TOOL)) {
			String lower = className.toLowerCase();
			if (lower.contains("pickaxe") || lower.contains("shovel")
					|| lower.contains("hoe") || lower.contains("shears")) {
				return false;
			}
			return true;
		}
		return false;
	}

	/**
	 * Returns true if the item is a throwable projectile — potions, pearls, snowballs,
	 * eggs, bows, crossbows, etc. We want to avoid aiming with these so the player
	 * doesn't accidentally throw/shoot at an enemy during a fast hotbar swap.
	 */
	private static boolean isThrowable(ItemStack stack) {
		if (stack.isEmpty()) return false;
		Item item = stack.getItem();
		// ProjectileItem covers splash/lingering potions, ender pearls, snowballs, eggs, etc.
		if (item instanceof ProjectileItem) return true;
		// Bows and crossbows have the PROJECTILE weapon tag or are identifiable by component.
		String name = item.getClass().getName().toLowerCase();
		return name.contains("bow") || name.contains("crossbow");
	}

	private Player findTarget(LocalPlayer player) {
		double maxDist = reach.get();
		Player closest = null;
		double closestDistSq = Double.MAX_VALUE;

		for (Player other : player.level().players()) {
			if (other == player || !other.isAlive() || other.isSpectator()) {
				continue;
			}
			double distSq = player.distanceToSqr(other);
			if (distSq > maxDist * maxDist || distSq >= closestDistSq) {
				continue;
			}
			closestDistSq = distSq;
			closest = other;
		}
		return closest;
	}
}