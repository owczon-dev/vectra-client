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
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ESP overlay: draws a wireframe box around every other player and optionally their
 * health above their head.
 *
 * <h3>Box</h3>
 * Draws 12 lines forming a rectangular wireframe around each player's bounding box.
 * Uses the same {@code DrawableGizmoPrimitives} system as vanilla's debug renderers.
 * Colour matches the health gradient (green → red).
 *
 * <h3>Health</h3>
 * Submits a floating health number above each player via {@code submitNameTag}.
 */
public final class OverlayModule extends Module {

	private static final int FULL_BRIGHT = 15728880;
	private static final float BOX_LINE_WIDTH = 2.0F;

	private final Setting.BooleanSetting showBox = addBoolean("Box", true);
	private final Setting.BooleanSetting showHealth = addBoolean("Health", true);

	public OverlayModule() {
		super("Overlay",
				"Draws a wireframe box around other players and optionally their health.",
				ModuleCategory.RENDER);
	}

	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> {
			OverlayModule mod = ModuleManager.getInstance().get(OverlayModule.class);
			if (mod != null && mod.isEnabled()) {
				mod.render(ctx);
			}
		});
	}

	@Override
	protected void onDisable() {
		// Nothing to clean up — gizmos are per-frame, no persistent state.
	}

	private void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer self = client.player;
		if (self == null || client.level == null) {
			return;
		}

		float pt = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);

		DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();

		for (AbstractClientPlayer player : client.level.players()) {
			if (player == self || !player.isAlive()) {
				continue;
			}

			float health = player.getHealth();
			float maxHealth = player.getMaxHealth();
			float pct = Mth.clamp(health / maxHealth, 0.0F, 1.0F);

			int r = (int) (255 * (1 - pct));
			int g = (int) (255 * pct);
			int color = 0xFF000000 | (r << 16) | (g << 8);

			if (showBox.get()) {
				drawBox(gizmos, player, pt, color);
			}

			if (showHealth.get()) {
				renderHealth(ctx, player, pt, color);
			}
		}

		gizmos.submit(ctx.submitNodeCollector(), ctx.levelState().cameraRenderState, false);
	}

	/** Draws 12 lines forming a wireframe box around the player's bounding box. */
	private void drawBox(DrawableGizmoPrimitives gizmos, AbstractClientPlayer player, float pt, int color) {
		AABB box = player.getBoundingBox();

		// Interpolated camera-relative positions for the 8 corners.
		Vec3 nbl = new Vec3(box.minX, box.minY, box.minZ); // near-bottom-left
		Vec3 nbr = new Vec3(box.maxX, box.minY, box.minZ); // near-bottom-right
		Vec3 ntl = new Vec3(box.minX, box.maxY, box.minZ); // near-top-left
		Vec3 ntr = new Vec3(box.maxX, box.maxY, box.minZ); // near-top-right
		Vec3 fbl = new Vec3(box.minX, box.minY, box.maxZ); // far-bottom-left
		Vec3 fbr = new Vec3(box.maxX, box.minY, box.maxZ); // far-bottom-right
		Vec3 ftl = new Vec3(box.minX, box.maxY, box.maxZ); // far-top-left
		Vec3 ftr = new Vec3(box.maxX, box.maxY, box.maxZ); // far-top-right

		// Bottom face
		gizmos.addLine(nbl, nbr, color, BOX_LINE_WIDTH);
		gizmos.addLine(nbr, fbr, color, BOX_LINE_WIDTH);
		gizmos.addLine(fbr, fbl, color, BOX_LINE_WIDTH);
		gizmos.addLine(fbl, nbl, color, BOX_LINE_WIDTH);

		// Top face
		gizmos.addLine(ntl, ntr, color, BOX_LINE_WIDTH);
		gizmos.addLine(ntr, ftr, color, BOX_LINE_WIDTH);
		gizmos.addLine(ftr, ftl, color, BOX_LINE_WIDTH);
		gizmos.addLine(ftl, ntl, color, BOX_LINE_WIDTH);

		// Vertical edges
		gizmos.addLine(nbl, ntl, color, BOX_LINE_WIDTH);
		gizmos.addLine(nbr, ntr, color, BOX_LINE_WIDTH);
		gizmos.addLine(fbl, ftl, color, BOX_LINE_WIDTH);
		gizmos.addLine(fbr, ftr, color, BOX_LINE_WIDTH);
	}

	private void renderHealth(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx,
			AbstractClientPlayer player, float pt, int color) {
		Vec3 camera = ctx.levelState().cameraRenderState.pos;

		double x = Mth.lerp(pt, player.xOld, player.getX()) - camera.x;
		double y = Mth.lerp(pt, player.yOld, player.getY()) - camera.y;
		double z = Mth.lerp(pt, player.zOld, player.getZ()) - camera.z;

		String text = String.format("%.1f", player.getHealth());
		Component component = Component.literal(text);

		ctx.poseStack().pushPose();
		ctx.poseStack().translate(x, y + player.getBbHeight() + 0.5, z);

		ctx.submitNodeCollector().submitNameTag(
				ctx.poseStack(),
				Vec3.ZERO,
				color,
				component,
				true,
				FULL_BRIGHT,
				ctx.levelState().cameraRenderState);

		ctx.poseStack().popPose();
	}
}