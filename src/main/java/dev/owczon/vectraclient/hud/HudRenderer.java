package dev.owczon.vectraclient.hud;

import dev.owczon.vectraclient.VectraClient;
import dev.owczon.vectraclient.module.DisplayModule;
import dev.owczon.vectraclient.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the enabled display modules as a small stack of lines in the top-left corner.
 *
 * Registered as a single {@code HudElement} and layered last, so it sits above the vanilla HUD
 * rather than fighting with it for z-order.
 */
public final class HudRenderer {

	private static final int MARGIN = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int TEXT_COLOR = 0xFFE6EDF3;
	private static final int PANEL_COLOR = 0x50000000;

	private HudRenderer() {
	}

	public static void register() {
		HudElementRegistry.addLast(VectraClient.id("hud"), HudRenderer::extractRenderState);
	}

	private static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		// The F3 overlay already reports fps/ping/etc; stay out of its way.
		if (client.getDebugOverlay().showDebugScreen()) {
			return;
		}

		List<String> lines = new ArrayList<>();
		for (DisplayModule module : ModuleManager.getInstance().getDisplayModules()) {
			if (!module.isEnabled()) {
				continue;
			}
			String text = module.getHudText(client);
			if (text != null && !text.isEmpty()) {
				lines.add(text);
			}
		}
		if (lines.isEmpty()) {
			return;
		}

		int widest = 0;
		for (String line : lines) {
			widest = Math.max(widest, client.font.width(line));
		}

		graphics.fill(MARGIN - 2, MARGIN - 2,
				MARGIN + widest + 2, MARGIN + lines.size() * LINE_HEIGHT + 1, PANEL_COLOR);

		int y = MARGIN;
		for (String line : lines) {
			graphics.text(client.font, line, MARGIN, y, TEXT_COLOR, true);
			y += LINE_HEIGHT;
		}
	}
}
