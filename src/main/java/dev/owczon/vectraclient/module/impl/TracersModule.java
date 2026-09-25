package dev.owczon.vectraclient.module.impl;

import dev.owczon.vectraclient.VectraClient;
import dev.owczon.vectraclient.module.Module;
import dev.owczon.vectraclient.module.ModuleCategory;
import dev.owczon.vectraclient.module.ModuleManager;
import dev.owczon.vectraclient.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a line from the player's crosshair to every other player.
 *
 * Colour lerps from <b>green</b> (far) through <b>yellow</b> to <b>red</b> (close).
 * Lines are rendered as gizmos via {@code DrawableGizmoPrimitives} — world-space coordinates,
 * the camera transform is handled by the gizmo submit pipeline.
 */
public final class TracersModule extends Module {

	private final Setting.DoubleSetting maxDistance = addDouble("Max Distance", 64.0, 8.0, 256.0, 1.0);
	private final Setting.DoubleSetting lineWidth = addDouble("Line Width", 1.5, 0.5, 5.0, 0.5);

	public TracersModule() {
		super("Tracers",
				"Draws lines from your crosshair to other players, coloured by distance.",
				ModuleCategory.RENDER);
	}

	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> {
			TracersModule mod = ModuleManager.getInstance().get(TracersModule.class);
			if (mod != null && mod.isEnabled()) {
				mod.renderTracers(ctx);
			}
		});
	}

	private void renderTracers(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer self = client.player;
		if (self == null || client.level == null) {
			return;
		}

		float pt = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);

		// Line origin: the camera's world-space position (crosshair).
		Vec3 cameraPos = ctx.levelState().cameraRenderState.pos;
		Vec3 from = cameraPos;

		DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
		double maxDist = maxDistance.get();
		float width = lineWidth.get().floatValue();

		for (AbstractClientPlayer player : client.level.players()) {
			if (player == self || !player.isAlive()) {
				continue;
			}

			// World-space interpolated position.
			double x = Mth.lerp(pt, player.xOld, player.getX());
			double y = Mth.lerp(pt, player.yOld, player.getY()) + player.getBbHeight() / 2.0;
			double z = Mth.lerp(pt, player.zOld, player.getZ());

			double dx = x - cameraPos.x;
			double dy = y - cameraPos.y;
			double dz = z - cameraPos.z;
			double distSq = dx * dx + dy * dy + dz * dz;
			if (distSq > maxDist * maxDist) {
				continue;
			}

			double dist = Math.sqrt(distSq);
			float fraction = Mth.clamp((float) (dist / maxDist), 0.0F, 1.0F);

			// Green (far) → yellow (mid) → red (close).
			int r, g;
			if (fraction > 0.5F) {
				float t = (fraction - 0.5F) * 2.0F;
				r = (int) (255 * (1.0F - t));
				g = 255;
			} else {
				float t = fraction * 2.0F;
				r = 255;
				g = (int) (255 * t);
			}
			int color = 0xFF000000 | (r << 16) | (g << 8);

			gizmos.addLine(from, new Vec3(x, y, z), color, width);
		}

		gizmos.submit(ctx.submitNodeCollector(), ctx.levelState().cameraRenderState, false);
	}
}