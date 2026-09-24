package dev.owczon.vectraclient.module;

import dev.owczon.vectraclient.config.ConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every feature in the client.
 *
 * Lifecycle contract:
 * <ul>
 *     <li>{@link #onEnable()} / {@link #onDisable()} run exactly once per state change.</li>
 *     <li>{@link #onTick()} runs once per client tick, but only while the module is enabled.</li>
 * </ul>
 *
 * Toggling a module persists it straight away, so a crash or an alt-F4 can't lose the change.
 */
public abstract class Module {

	private final String name;
	private final String description;
	private final ModuleCategory category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;

	protected Module(String name, String description, ModuleCategory category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	protected final Setting.DoubleSetting addDouble(String name, double value, double min, double max, double step) {
		Setting.DoubleSetting setting = new Setting.DoubleSetting(name, value, min, max, step);
		settings.add(setting);
		return setting;
	}

	protected final Setting.BooleanSetting addBoolean(String name, boolean value) {
		Setting.BooleanSetting setting = new Setting.BooleanSetting(name, value);
		settings.add(setting);
		return setting;
	}

	public final String getName() {
		return name;
	}

	public final String getDescription() {
		return description;
	}

	public final ModuleCategory getCategory() {
		return category;
	}

	public final List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(settings);
	}

	public final boolean isEnabled() {
		return enabled;
	}

	public final void toggle() {
		setEnabled(!enabled);
	}

	public final void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
		ConfigManager.save();
	}

	/**
	 * Flips the flag without firing {@link #onEnable()} / {@link #onDisable()} or writing the
	 * config. Used while loading a saved profile, where running the lifecycle hooks (and the save
	 * they'd trigger) would be both wrong and recursive.
	 */
	public final void setEnabledQuietly(boolean enabled) {
		this.enabled = enabled;
	}

	/** Called once per client tick; does nothing unless this module is enabled. */
	public final void tick() {
		if (enabled) {
			onTick();
		}
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	protected void onTick() {
	}
}
