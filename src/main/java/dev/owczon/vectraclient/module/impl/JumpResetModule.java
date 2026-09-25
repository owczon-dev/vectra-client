package dev.owczon.vectraclient.module.impl;

import dev.owczon.vectraclient.module.Module;
import dev.owczon.vectraclient.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Automatically jumps when another player hits you.
 *
 * <p>Detects the frame where {@code hurtTime} transitions from 0 to a positive value and calls
 * {@code jumpFromGround()} if the player is on the ground and was hurt by another player.
 * Fall damage, mob hits, and environmental damage are ignored.
 */
public final class JumpResetModule extends Module {

	/** {@code hurtTime} value from the previous tick, used to detect the damage frame. */
	private int prevHurtTime;

	public JumpResetModule() {
		super("Jump Reset",
				"Automatically jumps when you get hit by another player.",
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
		// Only jump if the attacker was another player (not fall damage, mobs, etc.).
		if (hurtTime > 0 && prevHurtTime == 0 && player.onGround()) {
			LivingEntity attacker = player.getLastHurtByMob();
			if (attacker instanceof Player && attacker != player) {
				player.jumpFromGround();
			}
		}

		prevHurtTime = hurtTime;
	}
}