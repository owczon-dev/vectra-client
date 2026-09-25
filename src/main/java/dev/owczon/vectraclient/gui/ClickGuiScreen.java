package dev.owczon.vectraclient.gui;

import dev.owczon.vectraclient.VectraClient;
import dev.owczon.vectraclient.config.ConfigManager;
import dev.owczon.vectraclient.module.Module;
import dev.owczon.vectraclient.module.ModuleCategory;
import dev.owczon.vectraclient.module.ModuleManager;
import dev.owczon.vectraclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Modern ClickGUI inspired by Feather Client.
 *
 * <p>Layout:
 * <ul>
 *   <li><b>Sidebar</b> (left) — Vectra logo + category buttons</li>
 *   <li><b>Main</b> (right) — module cards with toggle switches, or a settings panel
 *       when a module is selected</li>
 * </ul>
 *
 * <p>Everything is rendered manually via {@link GuiGraphicsExtractor} (no vanilla widgets) so we
 * have full control over the look. Click detection, hover states, slider drags and toggle
 * switches are all handled in {@link #mouseClicked} / {@link #mouseDragged} and the render pass.
 *
 * <p>Open with <b>P</b>; the world keeps ticking behind it ({@link #isPauseScreen()} is false).
 */
public class ClickGuiScreen extends Screen {

	// ── Colours (ARGB) ────────────────────────────────────────────────────────────
	private static final int COL_BACKDROP       = 0x80000000;
	private static final int COL_PANEL          = 0xFF14171E;
	private static final int COL_SIDEBAR        = 0xFF0F1118;
	private static final int COL_ACCENT         = 0xFF3B82F6;   // blue
	private static final int COL_ACCENT_DIM     = 0xAA3B82F6;
	private static final int COL_GREEN          = 0xFF22C55E;
	private static final int COL_TEXT           = 0xFFE2E8F0;
	private static final int COL_TEXT_DIM       = 0xFF64748B;
	private static final int COL_TEXT_MUTED     = 0xFF475569;
	private static final int COL_BORDER         = 0xFF2A2D38;
	private static final int COL_CARD           = 0xFF1A1D27;
	private static final int COL_CARD_HOVER     = 0xFF1F2330;
	private static final int COL_CARD_SELECTED  = 0xFF1E293B;
	private static final int COL_TOGGLE_OFF     = 0xFF334155;
	private static final int COL_CLOSE_HOVER    = 0xFFEF4444;

	// ── Layout ────────────────────────────────────────────────────────────────────
	private static final int SIDEBAR_W     = 140;
	private static final int CARD_H        = 46;
	private static final int TOGGLE_W      = 36;
	private static final int TOGGLE_H      = 18;
	private static final int CORNER_RADIUS = 4;
	private static final int LOGO_H        = 60;
	private static final int CLOSE_BTN_SZ  = 16;
	private static final int GEAR_BTN_W    = 20;

	// ── Persistent state ──────────────────────────────────────────────────────────
	private static int panelX = -1;
	private static int panelY = -1;

	// ── Per-open state ────────────────────────────────────────────────────────────
	private ModuleCategory selectedCategory = ModuleCategory.RENDER;
	private Module selectedModule;      // null → show module list; non-null → settings panel

	private boolean dragging;
	private int dragOffX, dragOffY;

	private boolean sliderDragging;
	private Setting.DoubleSetting sliderTarget;

	// ── Geometry (recomputed every frame) ─────────────────────────────────────────
	private int pX, pY, pW, pH;        // panel rect
	private int mainX, mainY, mainW;   // main area (right of sidebar)
	private int contentY, contentH;    // scrollable content inside main

	public ClickGuiScreen() {
		super(Component.literal(VectraClient.MOD_NAME));
	}

	// =======================================================================================
	// Init
	// =======================================================================================

	@Override
	protected void init() {
		pW = SIDEBAR_W + 2 + Math.min(340, Math.max(260, width / 2));
		int maxRows = Math.max(6, (height - 80) / CARD_H);
		int catCount = ModuleManager.getInstance().getInCategory(selectedCategory).size();
		int rows = Math.min(maxRows, Math.max(6, catCount));
		pH = 40 + rows * CARD_H + 20;

		if (panelX < 0) {
			panelX = (width - pW) / 2;
			panelY = (height - pH) / 2;
		}
		panelX = clamp(panelX, 0, width - pW);
		panelY = clamp(panelY, 0, height - pH);

		pX = panelX;
		pY = panelY;
		mainX = pX + SIDEBAR_W + 2;
		mainY = pY;
		mainW = pW - SIDEBAR_W - 2;
		contentY = mainY + 36;
		contentH = pH - 36 - 20;
	}

	// =======================================================================================
	// Render
	// =======================================================================================

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		Font font = Minecraft.getInstance().font;
		init();

		// ── Backdrop ──
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), COL_BACKDROP);

		// ── Sidebar ──
		drawSidebar(g, font, mx, my);

		// ── Divider ──
		g.fill(pX + SIDEBAR_W, pY, pX + SIDEBAR_W + 2, pY + pH, COL_BORDER);

		// ── Main background ──
		g.fill(mainX, mainY, mainX + mainW, mainY + pH, COL_PANEL);

		// ── Header ──
		g.fill(mainX, mainY, mainX + mainW, mainY + 36, 0xFF10131A);
		String heading = selectedModule != null
				? selectedModule.getName()
				: selectedCategory.getDisplayName();
		drawText(g, font, heading, mainX + 12, mainY + 13, COL_TEXT, true);

		// Close button
		int cx = mainX + mainW - CLOSE_BTN_SZ - 8;
		int cy = mainY + (36 - CLOSE_BTN_SZ) / 2;
		boolean closeHover = mx >= cx && mx < cx + CLOSE_BTN_SZ && my >= cy && my < cy + CLOSE_BTN_SZ;
		drawText(g, font, "✕", cx + 3, cy + 3, closeHover ? COL_CLOSE_HOVER : COL_TEXT_DIM, false);

		// ── Content ──
		g.enableScissor(mainX + 1, contentY, mainX + mainW - 1, contentY + contentH);

		if (selectedModule != null) {
			drawSettingsPanel(g, font, mx, my);
		} else {
			drawModuleCards(g, font, mx, my);
		}

		g.disableScissor();

		// ── Footer ──
		String footer = selectedModule != null
				? selectedModule.getDescription()
				: "Click to toggle  ·  ⚙ for settings";
		drawText(g, font, footer, mainX + 10, pY + pH - 16, COL_TEXT_MUTED, false);
	}

	// ── Sidebar ───────────────────────────────────────────────────────────────────

	private void drawSidebar(GuiGraphicsExtractor g, Font font, int mx, int my) {
		g.fill(pX, pY, pX + SIDEBAR_W, pY + pH, COL_SIDEBAR);

		// Logo
		int logoY = pY + 14;
		drawText(g, font, "VECTRA", pX + 16, logoY, COL_ACCENT, true);
		drawText(g, font, "CLIENT", pX + 16, logoY + 14, COL_TEXT_DIM, false);

		// Accent line
		g.fill(pX + 16, pY + LOGO_H - 4, pX + SIDEBAR_W - 16, pY + LOGO_H - 3, COL_ACCENT_DIM);

		// Categories
		int y = pY + LOGO_H + 8;
		for (ModuleCategory cat : ModuleCategory.values()) {
			boolean selected = cat == selectedCategory;
			boolean hover = mx >= pX + 8 && mx < pX + SIDEBAR_W - 8
					&& my >= y && my < y + 24;

			if (selected) {
				g.fill(pX + 4, y + 4, pX + 6, y + 20, COL_ACCENT);
				g.fill(pX + 8, y, pX + SIDEBAR_W - 8, y + 24, 0x183B82F6);
			} else if (hover) {
				g.fill(pX + 8, y, pX + SIDEBAR_W - 8, y + 24, 0x0CFFFFFF);
			}

			int textCol = selected ? COL_ACCENT : (hover ? COL_TEXT : COL_TEXT_DIM);
			drawText(g, font, cat.getDisplayName(), pX + 16, y + 7, textCol, false);
			y += 28;
		}
	}

	// ── Module cards ──────────────────────────────────────────────────────────────

	private void drawModuleCards(GuiGraphicsExtractor g, Font font, int mx, int my) {
		List<Module> modules = ModuleManager.getInstance().getInCategory(selectedCategory);
		if (modules.isEmpty()) {
			drawText(g, font, "No modules in this category", mainX + 14, contentY + 12, COL_TEXT_MUTED, false);
			return;
		}

		int y = contentY + 4;
		for (Module mod : modules) {
			boolean hover = mx >= mainX + 8 && mx < mainX + mainW - 8
					&& my >= y && my < y + CARD_H
					&& my >= contentY && my < contentY + contentH;
			boolean on = mod.isEnabled();

			// Card background
			drawRoundRect(g, mainX + 8, y, mainX + mainW - 8, y + CARD_H - 2,
					hover ? COL_CARD_HOVER : COL_CARD);

			// Green accent stripe when enabled
			if (on) {
				g.fill(mainX + 8, y + 6, mainX + 10, y + CARD_H - 8, COL_GREEN);
			}

			// Module name
			drawText(g, font, mod.getName(), mainX + 18, y + 10, on ? COL_TEXT : COL_TEXT_DIM, false);

			// Description
			String desc = mod.getDescription();
			if (desc != null && !desc.isEmpty()) {
				int maxDescW = mainW - 18 - TOGGLE_W - GEAR_BTN_W - 24;
				drawText(g, font, ellipsis(font, desc, maxDescW), mainX + 18, y + 24, COL_TEXT_MUTED, false);
			}

			// Settings gear button
			int gearX = mainX + mainW - TOGGLE_W - GEAR_BTN_W - 20;
			int gearY = y + (CARD_H - 2 - 14) / 2;
			boolean gearHover = mx >= gearX && mx < gearX + GEAR_BTN_W
					&& my >= gearY && my < gearY + 14;
			if (!mod.getSettings().isEmpty()) {
				drawText(g, font, "⚙", gearX + 4, gearY + 2,
						gearHover ? COL_ACCENT : COL_TEXT_MUTED, false);
			}

			// Toggle switch
			int tx = mainX + mainW - TOGGLE_W - 16;
			int ty = y + (CARD_H - 2 - TOGGLE_H) / 2;
			drawToggle(g, tx, ty, on);

			y += CARD_H;
		}
	}

	// ── Settings panel ────────────────────────────────────────────────────────────

	private void drawSettingsPanel(GuiGraphicsExtractor g, Font font, int mx, int my) {
		if (selectedModule == null) return;

		// Back button
		boolean backHover = mx >= mainX + 8 && mx < mainX + 68
				&& my >= contentY + 4 && my < contentY + 24;
		drawText(g, font, "← Back", mainX + 12, contentY + 8, backHover ? COL_ACCENT : COL_TEXT_DIM, false);

		int y = contentY + 32;
		List<Setting<?>> settings = selectedModule.getSettings();

		if (settings.isEmpty()) {
			drawText(g, font, "No settings available", mainX + 14, y, COL_TEXT_MUTED, false);
			return;
		}

		for (Setting<?> setting : settings) {
			if (setting instanceof Setting.BooleanSetting bool) {
				boolean hover = mx >= mainX + 8 && mx < mainX + mainW - 8
						&& my >= y && my < y + CARD_H;

				drawRoundRect(g, mainX + 8, y, mainX + mainW - 8, y + CARD_H - 2,
						hover ? COL_CARD_HOVER : COL_CARD);

				drawText(g, font, setting.getName(), mainX + 18, y + 14, COL_TEXT, false);

				int tx = mainX + mainW - TOGGLE_W - 16;
				int ty = y + (CARD_H - 2 - TOGGLE_H) / 2;
				drawToggle(g, tx, ty, bool.get());

				y += CARD_H;

			} else if (setting instanceof Setting.DoubleSetting dbl) {
				boolean hover = mx >= mainX + 8 && mx < mainX + mainW - 8
						&& my >= y && my < y + CARD_H + 10;

				drawRoundRect(g, mainX + 8, y, mainX + mainW - 8, y + CARD_H + 10,
						hover ? COL_CARD_HOVER : COL_CARD);

				String label = setting.getName();
				String value = String.format(java.util.Locale.ROOT, "%.2f", dbl.get());
				drawText(g, font, label, mainX + 18, y + 8, COL_TEXT, false);
				drawText(g, font, value, mainX + mainW - font.width(value) - 18, y + 8, COL_ACCENT, false);

				// Track
				int trackX = mainX + 18;
				int trackW = mainW - 36;
				int trackY = y + CARD_H;
				g.fill(trackX, trackY, trackX + trackW, trackY + 4, COL_TOGGLE_OFF);

				// Fill
				double norm = (dbl.get() - dbl.getMin()) / Math.max(0.001, dbl.getMax() - dbl.getMin());
				norm = Math.max(0, Math.min(1, norm));
				int fillW = (int) (trackW * norm);
				g.fill(trackX, trackY, trackX + fillW, trackY + 4, COL_ACCENT);

				// Handle
				int handleX = trackX + fillW - 5;
				g.fill(handleX, trackY - 3, handleX + 10, trackY + 7, COL_TEXT);

				y += CARD_H + 14;
			}
		}
	}

	// =======================================================================================
	// Input
	// =======================================================================================

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		int mx = (int) event.x();
		int my = (int) event.y();

		// ── Close button ──
		int cx = mainX + mainW - CLOSE_BTN_SZ - 8;
		int cy = mainY + (36 - CLOSE_BTN_SZ) / 2;
		if (mx >= cx && mx < cx + CLOSE_BTN_SZ && my >= cy && my < cy + CLOSE_BTN_SZ) {
			onClose();
			return true;
		}

		// ── Drag from header bar or sidebar title area ──
		if (my >= pY && my < pY + 36 && mx >= mainX && mx < cx) {
			dragging = true;
			dragOffX = mx - pX;
			dragOffY = my - pY;
			return true;
		}
		if (mx >= pX && mx < pX + SIDEBAR_W && my >= pY && my < pY + LOGO_H) {
			dragging = true;
			dragOffX = mx - pX;
			dragOffY = my - pY;
			return true;
		}

		// ── Sidebar categories ──
		int y = pY + LOGO_H + 8;
		for (ModuleCategory cat : ModuleCategory.values()) {
			if (mx >= pX + 8 && mx < pX + SIDEBAR_W - 8 && my >= y && my < y + 24) {
				selectedCategory = cat;
				selectedModule = null;
				recalcPanelHeight();
				return true;
			}
			y += 28;
		}

		// ── Back button (settings) ──
		if (selectedModule != null && mx >= mainX + 8 && mx < mainX + 68
				&& my >= contentY + 4 && my < contentY + 24) {
			selectedModule = null;
			return true;
		}

		// ── Content area ──
		if (mx >= mainX && mx < mainX + mainW && my >= contentY && my < contentY + contentH) {
			if (selectedModule != null) {
				return settingsClick(mx, my);
			} else {
				return moduleCardClick(mx, my);
			}
		}

		return super.mouseClicked(event, doubled);
	}

	private boolean moduleCardClick(int mx, int my) {
		List<Module> modules = ModuleManager.getInstance().getInCategory(selectedCategory);
		int y = contentY + 4;

		for (Module mod : modules) {
			if (my >= y && my < y + CARD_H) {
				// Settings gear
				int gearX = mainX + mainW - TOGGLE_W - GEAR_BTN_W - 20;
				int gearY = y + (CARD_H - 2 - 14) / 2;
				if (mx >= gearX && mx < gearX + GEAR_BTN_W && my >= gearY && my < gearY + 14
						&& !mod.getSettings().isEmpty()) {
					selectedModule = mod;
					recalcPanelHeight();
					return true;
				}

				// Toggle switch
				int tx = mainX + mainW - TOGGLE_W - 16;
				int ty = y + (CARD_H - 2 - TOGGLE_H) / 2;
				if (mx >= tx && mx < tx + TOGGLE_W && my >= ty && my < ty + TOGGLE_H) {
					mod.toggle();
					ConfigManager.save();
					return true;
				}

				// Click on card body: toggle
				mod.toggle();
				ConfigManager.save();
				return true;
			}
			y += CARD_H;
		}
		return false;
	}

	private boolean settingsClick(int mx, int my) {
		if (selectedModule == null) return false;

		int y = contentY + 32;
		for (Setting<?> setting : selectedModule.getSettings()) {
			if (setting instanceof Setting.BooleanSetting bool) {
				if (my >= y && my < y + CARD_H) {
					bool.set(!bool.get());
					ConfigManager.save();
					return true;
				}
				y += CARD_H;

			} else if (setting instanceof Setting.DoubleSetting dbl) {
				int trackX = mainX + 18;
				int trackW = mainW - 36;
				int trackY = y + CARD_H;

				if (my >= trackY - 6 && my < trackY + 10 && mx >= trackX && mx < trackX + trackW) {
					sliderDragging = true;
					sliderTarget = dbl;
					updateSlider(mx, trackX, trackW);
					return true;
				}
				y += CARD_H + 14;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		int mx = (int) event.x();
		int my = (int) event.y();

		if (dragging) {
			panelX = clamp(mx - dragOffX, 0, width - pW);
			panelY = clamp(my - dragOffY, 0, height - pH);
			return true;
		}

		if (sliderDragging && sliderTarget != null) {
			int trackX = pX + SIDEBAR_W + 2 + 18;
			int trackW = pW - SIDEBAR_W - 2 - 36;
			updateSlider(mx, trackX, trackW);
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			return true;
		}
		if (sliderDragging) {
			sliderDragging = false;
			sliderTarget = null;
			ConfigManager.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	private void updateSlider(int mx, int trackX, int trackW) {
		if (sliderTarget == null) return;
		double norm = (double) (mx - trackX) / trackW;
		norm = Math.max(0, Math.min(1, norm));
		double range = sliderTarget.getMax() - sliderTarget.getMin();
		sliderTarget.set(sliderTarget.getMin() + norm * range);
	}

	// =======================================================================================
	// Drawing helpers
	// =======================================================================================

	private static void drawRoundRect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		g.fill(x1, y1, x2, y2, color);
		int ca = 0x18FFFFFF;
		// Top-left
		g.fill(x1, y1, x1 + CORNER_RADIUS, y1 + 1, ca);
		g.fill(x1, y1, x1 + 1, y1 + CORNER_RADIUS, ca);
		// Top-right
		g.fill(x2 - CORNER_RADIUS, y1, x2, y1 + 1, ca);
		g.fill(x2 - 1, y1, x2, y1 + CORNER_RADIUS, ca);
		// Bottom-left
		g.fill(x1, y2 - 1, x1 + CORNER_RADIUS, y2, ca);
		g.fill(x1, y2 - CORNER_RADIUS, x1 + 1, y2, ca);
		// Bottom-right
		g.fill(x2 - CORNER_RADIUS, y2 - 1, x2, y2, ca);
		g.fill(x2 - 1, y2 - CORNER_RADIUS, x2, y2, ca);
	}

	private static void drawToggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
		int r = TOGGLE_H / 2;
		int trackCol = on ? COL_GREEN : COL_TOGGLE_OFF;

		// Track body
		g.fill(x + r, y, x + TOGGLE_W - r, y + TOGGLE_H, trackCol);
		// Left cap
		g.fill(x, y + 2, x + 2, y + TOGGLE_H - 2, trackCol);
		// Right cap
		g.fill(x + TOGGLE_W - 2, y + 2, x + TOGGLE_W, y + TOGGLE_H - 2, trackCol);

		// Knob
		int knobX = on ? x + TOGGLE_W - r - 4 : x + r - 4;
		g.fill(knobX, y + 2, knobX + 8, y + TOGGLE_H - 2, COL_TEXT);
	}

	private static void drawText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
		g.text(font, text, x, y, color, shadow);
	}

	private static String ellipsis(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) return text;
		String suffix = "…";
		int sw = font.width(suffix);
		for (int i = text.length() - 1; i > 0; i--) {
			if (font.width(text.substring(0, i)) + sw <= maxWidth) {
				return text.substring(0, i) + suffix;
			}
		}
		return suffix;
	}

	private void recalcPanelHeight() {
		int maxRows = Math.max(6, (height - 80) / CARD_H);
		int count;
		if (selectedModule != null) {
			count = selectedModule.getSettings().size();
			for (Setting<?> s : selectedModule.getSettings()) {
				if (s instanceof Setting.DoubleSetting) count++;
			}
		} else {
			count = ModuleManager.getInstance().getInCategory(selectedCategory).size();
		}
		int rows = Math.min(maxRows, Math.max(6, count));
		pH = 40 + rows * CARD_H + 20;

		panelX = clamp(panelX, 0, width - pW);
		panelY = clamp(panelY, 0, height - pH);
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		selectedModule = null;
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}