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
 * The colour lerps from <b>green</b> (far) through <b>yellow</b> to <b>red</b> (close), based on
 * the configured max distance. Lines are rendered as gizmos via {@code DrawableGizmoPrimitives},
 * the same debug-line system vanilla uses, so they clip through terrain and are visible at any
 * distance.
 */
public final class TracersModule extends Module {

	private final Setting.DoubleSetting maxDistance = addDouble("Max Distance", 64.0, 8.0, 256.0, 1.0);
	private final Setting.DoubleSetting lineWidth = addDouble("Line Width", 1.5, 0.5, 5.0, 0.5);

	public TracersModule() {
		super("Tracers",
				"Draws lines from your crosshair to other players, coloured by distance.",
				ModuleCategory.RENDER);
	}

	/**
	 * Registers the world-render hook. Called once from {@link VectraClient#onInitializeClient}.
	 */
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

		// Line origin: camera position (crosshair is at the camera, not at the hand).
		Vec3 from = Vec3.ZERO; // gizmo system uses camera-relative coords via cameraRenderState

		DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
		double maxDist = maxDistance.get();
		float width = lineWidth.get().floatValue();

		for (AbstractClientPlayer player : client.level.players()) {
			if (player == self || !player.isAlive()) {
				continue;
			}

			double x = Mth.lerp(pt, player.xOld, player.getX()) - camera.x;
			double y = Mth.lerp(pt, player.yOld, player.getY()) - camera.y;
			double z = Mth.lerp(pt, player.zOld, player.getZ()) - camera.z;

			// Aim at the centre of the player model.
			y += player.getBbHeight() / 2.0;

			double distSq = x * x + y * y + z * z;
			if (distSq > maxDist * maxDist) {
				continue;
			}

			double dist = Math.sqrt(distSq);
			float fraction = Mth.clamp((float) (dist / maxDist), 0.0F, 1.0F);

			// Green (far) → yellow (mid) → red (close).
			int r, g;
			if (fraction > 0.5F) {
				// green → yellow
				float t = (fraction - 0.5F) * 2.0F;
				r = (int) (255 * (1.0F - t));
				g = 255;
			} else {
				// yellow → red
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