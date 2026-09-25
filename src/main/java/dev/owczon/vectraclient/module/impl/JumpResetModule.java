package dev.owczon.vectraclient.module.impl;

import dev.owczon.vectraclient.module.Module;
import dev.owczon.vectraclient.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Automatically jumps when the player takes damage.
 *
 * Detects the frame where {@code hurtTime} transitions from 0 to a positive value (meaning the
 * player was just hit) and calls {@code jumpFromGround()} if the player is on the ground.
 * {@code jumpFromGround} directly applies the jump velocity — the same thing vanilla's own
 * space-bar path does — so the hit registers as a proper jump.
 */
public final class JumpResetModule extends Module {

	/** {@code hurtTime} value from the previous tick, used to detect the damage frame. */
	private int prevHurtTime;

	public JumpResetModule() {
		super("Jump Reset",
				"Automatically jumps when you take damage.",
				ModuleCategory.MOVEMENT);
	}

	@Override
	protected void onDisable() {
		prevHurtTime = 0;
	}

	@Override
	protected void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || player.isDeadOrDying()) {
			prevHurtTime = 0;
			return;
		}

		int hurtTime = player.hurtTime;

		// Detect the exact frame the player was hit: hurtTime went from 0 to > 0.
		if (hurtTime > 0 && prevHurtTime == 0 && player.onGround()) {
			player.jumpFromGround();
		}

		prevHurtTime = hurtTime;
	}
}