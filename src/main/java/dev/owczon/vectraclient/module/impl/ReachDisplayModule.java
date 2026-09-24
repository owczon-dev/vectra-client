package dev.owczon.vectraclient.module.impl;

import dev.owczon.vectraclient.module.DisplayModule;
import dev.owczon.vectraclient.util.TargetUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/**
 * Draws the distance to whatever entity is currently under the crosshair.
 *
 * The target is resolved once per tick (not per frame) - at 20Hz it tracks smoothly and keeps the
 * ray-march off the render thread's critical path. When nothing is under the crosshair the line
 * reads {@code Reach --} rather than vanishing and making the other lines jump.
 */
public final class ReachDisplayModule extends DisplayModule {

	/** Furthest the readout will look; matches vanilla's entity reach plus a little headroom. */
	private static final double MAX_RANGE = 6.0;

	private static final double NO_TARGET = -1.0;

	private double reach = NO_TARGET;

	public ReachDisplayModule() {
		super("Reach Display", "Shows the distance to the entity under your crosshair.");
	}

	@Override
	protected void onDisable() {
		reach = NO_TARGET;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			reach = NO_TARGET;
			return;
		}

		Entity target = TargetUtil.getCrosshairTarget(player, MAX_RANGE);
		reach = target == null ? NO_TARGET : Math.sqrt(player.distanceToSqr(target));
	}

	@Override
	public String getHudText(Minecraft client) {
		if (reach < 0.0) {
			return "Reach --";
		}
		return String.format(Locale.ROOT, "Reach %.2f", reach);
	}
}
