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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a line from the player's crosshair to every other player.
 *
 * Colour lerps from <b>green</b> (far) through <b>yellow</b> to <b>red</b> (close).
 * Lines are rendered via {@code submitCustomGeometry} with {@code RenderTypes.LINES} —
 * the same render pipeline that vanilla's block-outline and hit-outline use.
 *
 * <p>The PoseStack in the level render context has its origin at the camera position,
 * so vertex (0,0,0) is the camera and player positions are passed as camera-relative offsets.
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
		Vec3 camera = ctx.levelState().cameraRenderState.pos;
		double maxDist = maxDistance.get();
		float width = lineWidth.get().floatValue();

		// PoseStack origin is at the camera. Vertices are in camera-relative space.
		ctx.poseStack().pushPose();

		ctx.submitNodeCollector().submitCustomGeometry(
				ctx.poseStack(),
				RenderTypes.LINES,
				(pose, vertexConsumer) -> {
					for (AbstractClientPlayer player : client.level.players()) {
						if (player == self || !player.isAlive()) {
							continue;
						}

						// Camera-relative interpolated position.
						float rx = (float) (Mth.lerp(pt, player.xOld, player.getX()) - camera.x);
						float ry = (float) (Mth.lerp(pt, player.yOld, player.getY()) + player.getBbHeight() / 2.0 - camera.y);
						float rz = (float) (Mth.lerp(pt, player.zOld, player.getZ()) - camera.z);

						double distSq = rx * rx + ry * ry + rz * rz;
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

						// Line from camera (0,0,0) to the player.
						vertexConsumer.setColor(color);
						vertexConsumer.setLineWidth(width);
						vertexConsumer.addVertex(0, 0, 0);
						vertexConsumer.addVertex(rx, ry, rz);
					}
				});

		ctx.poseStack().popPose();
	}
}