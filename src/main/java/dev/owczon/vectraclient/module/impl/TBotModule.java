package dev.owczon.vectraclient.module.impl;

import dev.owczon.vectraclient.module.Module;
import dev.owczon.vectraclient.module.ModuleCategory;
import dev.owczon.vectraclient.module.Setting;
import dev.owczon.vectraclient.util.TargetUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Trigger-bot: swings on, and hits, the nearest other player that comes within range.
 *
 * The attack goes through {@code MultiPlayerGameMode#attack}, which is what vanilla's own left-click
 * uses - it sends the attack packet and then plays the swing animation locally, so the hit actually
 * lands rather than only animating.
 *
 * By default it also waits for the weapon's attack cooldown ({@code Player#getAttackStrengthScale})
 * before hitting. Attacking early still sends the packet, but vanilla scales damage by
 * {@code 0.2 + charge^2 * 0.8}, so a hit at half charge does a fraction of the damage - waiting for
 * full charge is what makes continuously hitting actually worth doing.
 */
public final class TBotModule extends Module {

	private final Setting.DoubleSetting range = addDouble("Range", 3.0, 1.0, 6.0, 0.1);
	private final Setting.BooleanSetting respectWeaponCooldown = addBoolean("Weapon Cooldown", true);
	private final Setting.DoubleSetting minCharge = addDouble("Min Charge (%)", 100.0, 0.0, 100.0, 5.0);
	private final Setting.DoubleSetting attackDelay = addDouble("Attack Delay (ticks)", 0.0, 0.0, 40.0, 1.0);
	private final Setting.BooleanSetting requireCrosshair = addBoolean("Require Crosshair", false);

	/** Extra ticks - on top of the weapon cooldown - before another attack is allowed. */
	private int delay;

	public TBotModule() {
		super("TBot", "Attacks and swings at another player when they come within range.", ModuleCategory.COMBAT);
	}

	@Override
	protected void onDisable() {
		delay = 0;
	}

	@Override
	protected void onTick() {
		if (delay > 0) {
			delay--;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || player.isDeadOrDying()) {
			return;
		}

		// Attacking before the weapon is charged still sends the packet, but vanilla scales the
		// damage by (0.2 + charge^2 * 0.8) - so a spammed hit at 30% charge does a third of the
		// damage. Waiting for the cooldown is what makes "spam hitting" actually hurt.
		if (respectWeaponCooldown.get() && player.getAttackStrengthScale(0.0F) < minCharge.get() / 100.0D) {
			return;
		}

		Player target = findTarget(player);
		if (target == null) {
			return;
		}

		client.gameMode.attack(player, target);
		player.swing(InteractionHand.MAIN_HAND);
		delay = attackDelay.get().intValue();
	}

	/** Nearest other player inside the configured range, or {@code null} if there isn't one. */
	private Player findTarget(LocalPlayer player) {
		double maxRange = range.get();
		AABB area = player.getBoundingBox().inflate(maxRange);
		List<Entity> candidates = player.level().getEntities(player, area,
				entity -> entity instanceof Player other && other != player && other.isAlive() && !other.isSpectator());

		Player closest = null;
		double closestDistanceSq = Double.MAX_VALUE;
		for (Entity entity : candidates) {
			if (!(entity instanceof Player other)) {
				continue;
			}
			double distanceSq = player.distanceToSqr(other);
			if (distanceSq > maxRange * maxRange || distanceSq >= closestDistanceSq) {
				continue;
			}
			closestDistanceSq = distanceSq;
			closest = other;
		}

		if (closest == null) {
			return null;
		}
		// Optional extra condition: only fire when the target is also the thing you're aiming at.
		if (requireCrosshair.get() && TargetUtil.getCrosshairTarget(player, maxRange) != closest) {
			return null;
		}
		return closest;
	}
}
