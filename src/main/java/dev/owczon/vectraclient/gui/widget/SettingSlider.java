package dev.owczon.vectraclient.gui.widget;

import dev.owczon.vectraclient.config.ConfigManager;
import dev.owczon.vectraclient.module.Setting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** A horizontal slider bound to a {@link Setting.DoubleSetting}. */
public class SettingSlider extends AbstractSliderButton {

	private final Setting.DoubleSetting setting;

	public SettingSlider(int x, int y, int width, int height, Setting.DoubleSetting setting) {
		super(x, y, width, height, Component.literal(formatLabel(setting)), normalize(setting));
		this.setting = setting;
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(formatLabel(setting)));
	}

	@Override
	protected void applyValue() {
		double range = setting.getMax() - setting.getMin();
		setting.set(setting.getMin() + this.value * range);
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		super.onRelease(event);
		// The drag is finished, so this is a good moment to make the value stick.
		ConfigManager.save();
	}

	private static double normalize(Setting.DoubleSetting setting) {
		double range = setting.getMax() - setting.getMin();
		if (range <= 0.0) {
			return 0.0;
		}
		return (setting.get() - setting.getMin()) / range;
	}

	private static String formatLabel(Setting.DoubleSetting setting) {
		return setting.getName() + ": " + String.format(Locale.ROOT, "%.2f", setting.get());
	}
}
