package dev.owczon.vectraclient.module.impl;

import dev.owczon.vectraclient.module.Module;
import dev.owczon.vectraclient.module.ModuleCategory;
import dev.owczon.vectraclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Smoothly pulls the crosshair towards the nearest player within reach.
 *
 * Unlike a hard snap, this calculates the delta between the current look direction and the
 * target direction, then applies a configurable fraction of that delta each tick. The result
 * is a smooth, human-looking rotation that tracks the target.
 *
 * <h3>Minimal Y-axis movement</h3>
 * The target point is placed at the target's mid-torso (50% of bounding-box height) rather
 * than at head level. Combined with the smooth factor this keeps vertical movement minimal —
 * the crosshair drifts towards body centre rather than snapping up to the head.
 */
public final class AimModule extends Module {

	private final Setting.DoubleSetting reach = addDouble("Reach", 4.5, 1.0, 6.0, 0.1);
	private final Setting.DoubleSetting smooth = addDouble("Smooth", 0.5, 0.05, 1.0, 0.05);
	private final Setting.BooleanSetting requireAim = addBoolean("Require Aim Key", false);

	public AimModule() {
		super("Aim",
				"Smoothly pulls your crosshair towards the nearest player within reach.",
				ModuleCategory.COMBAT);
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || player.isDeadOrDying()) {
			return;
		}

		if (requireAim.get() && !client.options.keyAttack.isDown()) {
			return;
		}

		Player target = findTarget(player);
		if (target == null) {
			return;
		}

		// Aim at mid-torso to minimise vertical movement.
		double targetX = target.getX();
		double targetY = target.getY() + target.getBbHeight() * 0.5;
		double targetZ = target.getZ();

		double dx = targetX - player.getX();
		double dy = targetY - (player.getY() + player.getEyeHeight());
		double dz = targetZ - player.getZ();

		double horizontalDist = Math.sqrt(dx * dx + dz * dz);

		// Desired angles (degrees).
		float desiredYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		float desiredPitch = (float) -(Mth.atan2(dy, horizontalDist) * (180.0 / Math.PI));

		float currentYaw = player.getYRot();
		float currentPitch = player.getXRot();

		// Wrap yaw delta to [-180, 180] so we rotate the short way around.
		float deltaYaw = Mth.wrapDegrees(desiredYaw - currentYaw);
		float deltaPitch = desiredPitch - currentPitch;

		float factor = smooth.get().floatValue();

		player.setYRot(currentYaw + deltaYaw * factor);
		player.setXRot(Mth.clamp(currentPitch + deltaPitch * factor, -90.0F, 90.0F));
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