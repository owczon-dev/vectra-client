package dev.owczon.vectraclient.module;

import dev.owczon.vectraclient.module.impl.FpsDisplayModule;
import dev.owczon.vectraclient.module.impl.PingDisplayModule;
import dev.owczon.vectraclient.module.impl.ReachDisplayModule;
import dev.owczon.vectraclient.module.impl.TBotModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns every module instance and drives their ticks.
 *
 * Modules are registered here (declaration order is the order they show up in the ClickGUI), and
 * are grouped by {@link ModuleCategory} on demand rather than being stored in per-category maps.
 */
public final class ModuleManager {

	private static final ModuleManager INSTANCE = new ModuleManager();

	private final List<Module> modules = new ArrayList<>();
	private final List<DisplayModule> displays = new ArrayList<>();
	private final Set<Module> alreadyReported = new HashSet<>();

	private ModuleManager() {
		// -- Render --
		register(new FpsDisplayModule());
		register(new PingDisplayModule());
		register(new ReachDisplayModule());

		// -- Combat --
		register(new TBotModule());

		// Movement, World, Utility and Uncategorized have no modules yet; the ClickGUI still
		// lists them so it's obvious where future modules will land.
	}

	public static ModuleManager getInstance() {
		return INSTANCE;
	}

	private void register(Module module) {
		modules.add(module);
		if (module instanceof DisplayModule display) {
			displays.add(display);
		}
	}

	public List<Module> getModules() {
		return modules;
	}

	/** Display modules in registration order, which is the order their HUD lines are drawn. */
	public List<DisplayModule> getDisplayModules() {
		return displays;
	}

	public List<Module> getInCategory(ModuleCategory category) {
		List<Module> result = new ArrayList<>();
		for (Module module : modules) {
			if (module.getCategory() == category) {
				result.add(module);
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T get(Class<T> type) {
		for (Module module : modules) {
			if (type.isInstance(module)) {
				return (T) module;
			}
		}
		return null;
	}

	/**
	 * Ticks every enabled module.
	 *
	 * A module that throws is logged once and then skipped for the rest of the session: a broken
	 * readout shouldn't take the rest of the client's tick handling (or the game) down with it.
	 */
	public void tickAll() {
		for (Module module : modules) {
			try {
				module.tick();
			} catch (RuntimeException e) {
				if (alreadyReported.add(module)) {
					System.err.println("[vectra-client] Module " + module.getName() + " threw while ticking:");
					e.printStackTrace();
				}
			}
		}
	}

	/** Turns everything off, running each module's cleanup hook. Used when the client shuts down. */
	public void disableAll() {
		for (Module module : modules) {
			if (module.isEnabled()) {
				module.setEnabled(false);
			}
		}
	}
}
