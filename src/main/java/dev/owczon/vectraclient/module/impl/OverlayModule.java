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
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ESP overlay: shows a coloured outline around every other player and optionally their
 * health above their head.
 *
 * <h3>Outline</h3>
 * Sets the vanilla glowing flag ({@code entityData} bit 6) on every other player each tick
 * and clears it again when the module is disabled or the setting is off. The built-in
 * Minecraft outline renderer handles the rest — coloured outlines through walls, post-process
 * bloom, the works.
 *
 * <h3>Health</h3>
 * Submits a floating health number above each player during the gizmo render pass
 * ({@code LevelRenderEvents#BEFORE_GIZMOS}), using the same {@code submitNameTag} code path
 * that vanilla's own nametags use. Colour lerps from green (full) to red (low).
 */
public final class OverlayModule extends Module {

	/**
	 * The entity data accessor for the shared flags byte. Vanilla declares this as
	 * {@code protected static final} on {@code Entity}, but the accessor's contract is
	 * just (id=0, serializer=BYTE) — constructing an equivalent one avoids reflection.
	 */
	private static final EntityDataAccessor<Byte> DATA_SHARED_FLAGS =
			new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);

	/** Bit 6 = the glowing flag. */
	private static final int GLOWING_BIT = 1 << 6;

	/** Full-bright light coords ({@code LightTexture.pack(15, 15)}). */
	private static final int FULL_BRIGHT = 15728880;

	private final Setting.BooleanSetting showOutline = addBoolean("Outline", true);
	private final Setting.BooleanSetting showHealth = addBoolean("Health", true);

	/** Players whose glowing flag we've set, so we can clear them on disable. */
	private final List<AbstractClientPlayer> glowingPlayers = new ArrayList<>();

	public OverlayModule() {
		super("Overlay",
				"Shows an outline around other players and optionally their health.",
				ModuleCategory.RENDER);
	}

	/**
	 * Registers the world-render hook. Called once from {@link VectraClient#onInitializeClient}.
	 */
	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> {
			OverlayModule mod = ModuleManager.getInstance().get(OverlayModule.class);
			if (mod != null && mod.isEnabled()) {
				mod.renderHealth(ctx);
			}
		});
	}

	@Override
	protected void onDisable() {
		clearAllGlowing();
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}

		// Clear last tick's glow state first.
		clearAllGlowing();

		if (!showOutline.get()) {
			return;
		}

		for (AbstractClientPlayer player : client.level.players()) {
			if (player == client.player || !player.isAlive()) {
				continue;
			}
			setGlowing(player, true);
			glowingPlayers.add(player);
		}
	}

	/* ---------- world-space health text ---------- */

	private void renderHealth(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx) {
		if (!showHealth.get()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer self = client.player;
		if (self == null || client.level == null) {
			return;
		}

		float pt = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 camera = ctx.levelState().cameraRenderState.pos;

		for (AbstractClientPlayer player : client.level.players()) {
			if (player == self || !player.isAlive()) {
				continue;
			}

			double x = Mth.lerp(pt, player.xOld, player.getX()) - camera.x;
			double y = Mth.lerp(pt, player.yOld, player.getY()) - camera.y;
			double z = Mth.lerp(pt, player.zOld, player.getZ()) - camera.z;

			float health = player.getHealth();
			float maxHealth = player.getMaxHealth();
			float pct = Mth.clamp(health / maxHealth, 0.0F, 1.0F);

			// Lerp from green (100%) to red (0%).
			int r = (int) (255 * (1 - pct));
			int g = (int) (255 * pct);
			int color = 0xFF000000 | (r << 16) | (g << 8);

			String text = String.format("%.1f", health);
			Component component = Component.literal(text);

			ctx.poseStack().pushPose();
			ctx.poseStack().translate(x, y + player.getBbHeight() + 0.5, z);

			ctx.submitNodeCollector().submitNameTag(
					ctx.poseStack(),
					Vec3.ZERO,
					color,
					component,
					true,           // see-through (visible through walls)
					FULL_BRIGHT,
					ctx.levelState().cameraRenderState);

			ctx.poseStack().popPose();
		}
	}

	/* ---------- glowing helpers ---------- */

	private static void setGlowing(AbstractClientPlayer player, boolean glow) {
		byte flags = player.getEntityData().get(DATA_SHARED_FLAGS);
		if (glow) {
			player.getEntityData().set(DATA_SHARED_FLAGS, (byte) (flags | GLOWING_BIT));
		} else {
			player.getEntityData().set(DATA_SHARED_FLAGS, (byte) (flags & ~GLOWING_BIT));
		}
	}

	private void clearAllGlowing() {
		for (AbstractClientPlayer player : glowingPlayers) {
			if (player != null) {
				setGlowing(player, false);
			}
		}
		glowingPlayers.clear();
	}
}