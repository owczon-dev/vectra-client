package dev.owczon.vectraclient.module.impl;

import dev.owczon.vectraclient.module.DisplayModule;
import net.minecraft.client.Minecraft;

/** Draws the client's current frame rate in the top-left corner of the HUD. */
public final class FpsDisplayModule extends DisplayModule {

	public FpsDisplayModule() {
		super("FPS Display", "Shows your current frame rate.");
	}

	@Override
	public String getHudText(Minecraft client) {
		return "FPS " + client.getFps();
	}
}
