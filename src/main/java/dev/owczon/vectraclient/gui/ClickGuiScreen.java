package dev.owczon.vectraclient.gui;

import dev.owczon.vectraclient.VectraClient;
import dev.owczon.vectraclient.config.ConfigManager;
import dev.owczon.vectraclient.gui.widget.SettingSlider;
import dev.owczon.vectraclient.module.Module;
import dev.owczon.vectraclient.module.ModuleCategory;
import dev.owczon.vectraclient.module.ModuleManager;
import dev.owczon.vectraclient.module.Setting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The ClickGUI: categories down the left, that category's modules in the middle, and the selected
 * module's settings on the right.
 *
 * Clicking a module toggles it and selects it at the same time, so its settings are immediately
 * available. The panel can be dragged by its title bar and remembers where you left it between
 * openings. {@link #isPauseScreen()} is false, so the world keeps running behind it - which matters
 * for combat modules like TBot.
 *
 * Rebuilds triggered by a click are deferred until the next render: vanilla is still iterating its
 * child list when a button's handler runs, and clearing widgets mid-iteration is not safe.
 */
public class ClickGuiScreen extends Screen {

	private static final int HEADER_HEIGHT = 20;
	private static final int PADDING = 6;
	private static final int CATEGORY_WIDTH = 96;
	private static final int MODULE_WIDTH = 156;
	private static final int SETTINGS_WIDTH = 150;
	private static final int ROW_HEIGHT = 20;
	private static final int FOOTER_HEIGHT = 14;
	private static final int CLOSE_SIZE = 14;

	/** Panel height grows with content between these row counts, so short lists don't get a huge box. */
	private static final int MIN_CONTENT_ROWS = 6;
	private static final int MAX_CONTENT_ROWS = 12;

	private static final int COLOR_OVERLAY = 0x30000000;
	private static final int COLOR_BACKGROUND = 0xD00E1117;
	private static final int COLOR_HEADER = 0xF0161B22;
	private static final int COLOR_BORDER = 0xFF30363D;
	private static final int COLOR_DIVIDER = 0xFF21262D;
	private static final int COLOR_TITLE = 0xFFE6EDF3;
	private static final int COLOR_HINT = 0xFF8B949E;

	/** Remembered between openings; -1 until the panel is first dragged. */
	private static int lastX = -1;
	private static int lastY = -1;

	private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();

	private ModuleCategory selectedCategory = ModuleCategory.RENDER;
	private Module selectedModule;

	private boolean pendingRebuild = true;
	private boolean dragging;
	private int grabOffsetX;
	private int grabOffsetY;

	private int originX;
	private int originY;
	private int panelWidth;
	private int panelHeight;
	private int contentTop;
	private int categoryX;
	private int moduleX;
	private int settingsX;
	private int contentBottom;

	public ClickGuiScreen() {
		super(Component.literal(VectraClient.MOD_NAME));
	}

	// ---------------------------------------------------------------------------------------------
	// Layout
	// ---------------------------------------------------------------------------------------------

	@Override
	protected void init() {
		rebuild();
		pendingRebuild = false;
	}

	/**
	 * Recomputes the panel geometry and recreates every widget from the current selection.
	 * Safe to call at any time (it never runs from inside a click handler - see
	 * {@link #pendingRebuild}).
	 */
	private void rebuild() {
		clearDynamicWidgets();

		List<Module> inCategory = ModuleManager.getInstance().getInCategory(selectedCategory);

		int rows = Math.min(MAX_CONTENT_ROWS, Math.max(MIN_CONTENT_ROWS, inCategory.size()));
		panelWidth = PADDING + CATEGORY_WIDTH + PADDING + MODULE_WIDTH + PADDING + SETTINGS_WIDTH + PADDING;
		panelHeight = HEADER_HEIGHT + PADDING + rows * ROW_HEIGHT + PADDING + FOOTER_HEIGHT;

		applyOrigin();

		contentTop = originY + HEADER_HEIGHT + PADDING;
		contentBottom = originY + panelHeight - FOOTER_HEIGHT;
		categoryX = originX + PADDING;
		moduleX = categoryX + CATEGORY_WIDTH + PADDING;
		settingsX = moduleX + MODULE_WIDTH + PADDING;

		addCategoryButtons();
		addModuleButtons(inCategory);
		addSettingsWidgets();
		addCloseButton();
	}

	/** Positions the panel: centred the first time, otherwise wherever it was last dragged to. */
	private void applyOrigin() {
		if (lastX < 0) {
			originX = (width - panelWidth) / 2;
			originY = (height - panelHeight) / 2;
		} else {
			originX = lastX;
			originY = lastY;
		}
		originX = clamp(originX, 0, width - panelWidth);
		originY = clamp(originY, 0, height - panelHeight);
	}

	private void addCategoryButtons() {
		int y = contentTop;
		for (ModuleCategory category : ModuleCategory.values()) {
			boolean selected = category == selectedCategory;
			Component label = Component.literal((selected ? "> " : "  ") + category.getDisplayName())
					.withStyle(selected ? ChatFormatting.AQUA : ChatFormatting.GRAY);

			Button button = Button.builder(label, pressed -> {
				selectedCategory = category;
				selectedModule = null;
				pendingRebuild = true;
			}).bounds(categoryX, y, CATEGORY_WIDTH, ROW_HEIGHT - 2).build();

			addDynamic(button);
			y += ROW_HEIGHT;
		}
	}

	private void addModuleButtons(List<Module> modules) {
		int y = contentTop;
		for (Module module : modules) {
			Button button = Button.builder(moduleLabel(module), pressed -> {
				module.toggle();
				selectedModule = module;
				pendingRebuild = true;
			}).bounds(moduleX, y, MODULE_WIDTH, ROW_HEIGHT - 2).build();

			addDynamic(button);
			y += ROW_HEIGHT;
		}
	}

	private void addSettingsWidgets() {
		Module module = selectedModule;
		if (module == null) {
			return;
		}

		int y = contentTop;
		for (Setting<?> setting : module.getSettings()) {
			if (setting instanceof Setting.DoubleSetting doubleSetting) {
				addDynamic(new SettingSlider(settingsX, y, SETTINGS_WIDTH, ROW_HEIGHT - 2, doubleSetting));
			} else if (setting instanceof Setting.BooleanSetting booleanSetting) {
				Button button = Button.builder(booleanLabel(booleanSetting), pressed -> {
					booleanSetting.set(!booleanSetting.get());
					pressed.setMessage(booleanLabel(booleanSetting));
					ConfigManager.save();
				}).bounds(settingsX, y, SETTINGS_WIDTH, ROW_HEIGHT - 2).build();
				addDynamic(button);
			}
			y += ROW_HEIGHT;
		}
	}

	private void addCloseButton() {
		Button close = Button.builder(Component.literal("x"), pressed -> onClose())
				.bounds(originX + panelWidth - PADDING - CLOSE_SIZE,
						originY + (HEADER_HEIGHT - CLOSE_SIZE) / 2,
						CLOSE_SIZE, CLOSE_SIZE)
				.build();
		addDynamic(close);
	}

	private void addDynamic(AbstractWidget widget) {
		addRenderableWidget(widget);
		dynamicWidgets.add(widget);
	}

	private void clearDynamicWidgets() {
		for (AbstractWidget widget : dynamicWidgets) {
			removeWidget(widget);
		}
		dynamicWidgets.clear();
	}

	// ---------------------------------------------------------------------------------------------
	// Rendering
	// ---------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (pendingRebuild) {
			pendingRebuild = false;
			rebuild();
		}

		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), COLOR_OVERLAY);

		graphics.fill(originX, originY, originX + panelWidth, originY + panelHeight, COLOR_BACKGROUND);
		graphics.fill(originX, originY, originX + panelWidth, originY + HEADER_HEIGHT, COLOR_HEADER);
		graphics.outline(originX, originY, panelWidth, panelHeight, COLOR_BORDER);

		int columnTop = originY + HEADER_HEIGHT;
		graphics.verticalLine(moduleX - PADDING / 2, columnTop, contentBottom, COLOR_DIVIDER);
		graphics.verticalLine(settingsX - PADDING / 2, columnTop, contentBottom, COLOR_DIVIDER);
		graphics.fill(originX, contentBottom, originX + panelWidth, contentBottom + 1, COLOR_DIVIDER);

		graphics.text(font, VectraClient.MOD_NAME + "  |  drag the title bar to move",
				originX + PADDING, originY + 6, COLOR_TITLE, true);

		List<Module> inCategory = ModuleManager.getInstance().getInCategory(selectedCategory);
		if (inCategory.isEmpty()) {
			graphics.text(font, "No modules yet", moduleX, contentTop + 3, COLOR_HINT, true);
		}

		if (selectedModule != null && selectedModule.getSettings().isEmpty()) {
			graphics.text(font, "(no settings)", settingsX, contentTop + 3, COLOR_HINT, true);
		} else if (selectedModule == null) {
			graphics.text(font, "Settings", settingsX, contentTop + 3, COLOR_HINT, true);
		}

		String footer = selectedModule == null
				? "Click a module to toggle it"
				: selectedModule.getName() + " - " + selectedModule.getDescription();
		graphics.text(font, footer, originX + PADDING, contentBottom + 3, COLOR_HINT, true);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	// ---------------------------------------------------------------------------------------------
	// Input
	// ---------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (isInHeaderDragArea(event.x(), event.y())) {
			dragging = true;
			grabOffsetX = (int) event.x() - originX;
			grabOffsetY = (int) event.y() - originY;
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			moveTo((int) event.x() - grabOffsetX, (int) event.y() - grabOffsetY);
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
		return super.mouseReleased(event);
	}

	/** Moves the panel (and every widget with it), keeping it fully on screen. */
	private void moveTo(int x, int y) {
		int newX = clamp(x, 0, width - panelWidth);
		int newY = clamp(y, 0, height - panelHeight);
		int dx = newX - originX;
		int dy = newY - originY;
		if (dx == 0 && dy == 0) {
			return;
		}

		originX = newX;
		originY = newY;
		lastX = newX;
		lastY = newY;
		contentTop += dy;
		contentBottom += dy;
		categoryX += dx;
		moduleX += dx;
		settingsX += dx;

		for (AbstractWidget widget : dynamicWidgets) {
			widget.setX(widget.getX() + dx);
			widget.setY(widget.getY() + dy);
		}
	}

	private boolean isInHeaderDragArea(double x, double y) {
		if (y < originY || y > originY + HEADER_HEIGHT) {
			return false;
		}
		// Leave the close button alone.
		int closeLeft = originX + panelWidth - PADDING - CLOSE_SIZE - 4;
		return x >= originX && x < closeLeft;
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		super.onClose();
	}

	/** Keep the world ticking behind the GUI, so modules stay live while you configure them. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	private static int clamp(int value, int min, int max) {
		if (min > max) {
			return min;
		}
		return Math.max(min, Math.min(value, max));
	}

	private static Component moduleLabel(Module module) {
		boolean on = module.isEnabled();
		return Component.literal((on ? "[ON]  " : "[OFF] ") + module.getName())
				.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private static Component booleanLabel(Setting.BooleanSetting setting) {
		boolean on = setting.get();
		return Component.literal(setting.getName() + ": " + (on ? "ON" : "OFF"))
				.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
	}
}
