package dev.owczon.vectraclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.owczon.vectraclient.module.Module;
import dev.owczon.vectraclient.module.ModuleManager;
import dev.owczon.vectraclient.module.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Saves each module's enabled state and settings to {@code <config dir>/vectra-client.json}.
 *
 * Written on every toggle and on shutdown, and read once during client init. Every failure path is
 * caught and logged: a corrupt or hand-edited config should cost you your settings, not your game.
 */
public final class ConfigManager {

	private static final String FILE_NAME = "vectra-client.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ConfigManager() {
	}

	private static Path getPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = getPath();
		if (!Files.isRegularFile(path)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			if (root == null) {
				return;
			}

			for (Module module : ModuleManager.getInstance().getModules()) {
				JsonObject entry = root.getAsJsonObject(module.getName());
				if (entry == null) {
					continue;
				}

				if (entry.has("enabled")) {
					// Quietly: the client is still starting up, so firing onEnable() here (and the
					// save it would trigger) is the wrong thing to do.
					module.setEnabledQuietly(entry.get("enabled").getAsBoolean());
				}

				JsonObject settings = entry.getAsJsonObject("settings");
				if (settings == null) {
					continue;
				}
				for (Setting<?> setting : module.getSettings()) {
					applySetting(setting, settings);
				}
			}
		} catch (Exception e) {
			System.err.println("[vectra-client] Could not read " + FILE_NAME + "; using defaults.");
			e.printStackTrace();
		}
	}

	private static void applySetting(Setting<?> setting, JsonObject settings) {
		try {
			if (setting instanceof Setting.DoubleSetting doubleSetting) {
				Double value = readDouble(settings, setting.getName());
				if (value != null) {
					doubleSetting.set(value);
				}
			} else if (setting instanceof Setting.BooleanSetting booleanSetting) {
				Boolean value = readBoolean(settings, setting.getName());
				if (value != null) {
					booleanSetting.set(value);
				}
			}
		} catch (RuntimeException e) {
			System.err.println("[vectra-client] Skipping bad value for setting " + setting.getName() + ".");
		}
	}

	private static Double readDouble(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
			return null;
		}
		try {
			return object.get(key).getAsDouble();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static Boolean readBoolean(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
			return null;
		}
		try {
			return object.get(key).getAsBoolean();
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		List<Module> modules = ModuleManager.getInstance().getModules();

		for (Module module : modules) {
			JsonObject entry = new JsonObject();
			entry.addProperty("enabled", module.isEnabled());

			if (!module.getSettings().isEmpty()) {
				JsonObject settings = new JsonObject();
				for (Setting<?> setting : module.getSettings()) {
					if (setting instanceof Setting.DoubleSetting doubleSetting) {
						settings.addProperty(doubleSetting.getName(), doubleSetting.get());
					} else if (setting instanceof Setting.BooleanSetting booleanSetting) {
						settings.addProperty(booleanSetting.getName(), booleanSetting.get());
					}
				}
				entry.add("settings", settings);
			}

			root.add(module.getName(), entry);
		}

		Path path = getPath();
		try {
			Path parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			System.err.println("[vectra-client] Could not write " + FILE_NAME + ".");
			e.printStackTrace();
		}
	}
}
