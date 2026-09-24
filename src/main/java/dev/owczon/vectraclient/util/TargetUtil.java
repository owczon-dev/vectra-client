package dev.owczon.vectraclient.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Works out which entity the player is currently aiming at.
 *
 * Rather than reading the client's own pick result - whose name and shape have moved around
 * between recent versions - this walks a short ray along the look vector and tests it against
 * candidate bounding boxes. It only leans on long-stable API (position, rotation, AABBs) and is
 * well past accurate enough for a reach readout or a "am I looking at them" check.
 */
public final class TargetUtil {

	/** How far the ray advances per sample, in blocks. */
	private static final double STEP = 0.05;

	/** Extra slack added to each bounding box, so grazing hits still register. */
	private static final double HIT_PADDING = 0.1;

	private TargetUtil() {
	}

	/**
	 * @return the closest entity under the crosshair within {@code maxRange}, or {@code null}
	 */
	public static Entity getCrosshairTarget(LocalPlayer player, double maxRange) {
		if (player == null || maxRange <= 0.0) {
			return null;
		}

		double eyeY = player.getY() + player.getEyeHeight();
		float yaw = player.getYRot();
		float pitch = player.getXRot();

		// Vanilla's look vector, rebuilt from angles: yaw 0 faces +Z, pitch is positive downwards.
		double yawRad = Math.toRadians(yaw);
		double pitchRad = Math.toRadians(pitch);
		double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
		double dirY = -Math.sin(pitchRad);
		double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);

		AABB area = player.getBoundingBox().inflate(maxRange);
		List<Entity> candidates = player.level().getEntities(player, area,
				entity -> entity != player && entity.isAlive() && !entity.isSpectator());
		if (candidates.isEmpty()) {
			return null;
		}

		for (double distance = 0.0; distance <= maxRange; distance += STEP) {
			double x = player.getX() + dirX * distance;
			double y = eyeY + dirY * distance;
			double z = player.getZ() + dirZ * distance;

			for (Entity candidate : candidates) {
				AABB box = candidate.getBoundingBox().inflate(HIT_PADDING);
				if (x >= box.minX && x <= box.maxX
						&& y >= box.minY && y <= box.maxY
						&& z >= box.minZ && z <= box.maxZ) {
					return candidate;
				}
			}
		}
		return null;
	}
}
