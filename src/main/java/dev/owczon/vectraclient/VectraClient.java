package dev.owczon.vectraclient;

import dev.owczon.vectraclient.config.ConfigManager;
import dev.owczon.vectraclient.hud.HudRenderer;
import dev.owczon.vectraclient.keybind.KeybindManager;
import dev.owczon.vectraclient.module.ModuleManager;
import dev.owczon.vectraclient.module.impl.OverlayModule;
import dev.owczon.vectraclient.module.impl.TracersModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;

/**
 * Client entrypoint: wires the module system, keybinds, HUD and config into Fabric's events.
 */
public class VectraClient implements ClientModInitializer {

	public static final String MOD_ID = "vectra-client";
	public static final String MOD_NAME = "Vectra Client";

	private final KeybindManager keybinds = new KeybindManager();

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		keybinds.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			keybinds.onClientTick(client);
			ModuleManager.getInstance().tickAll();
		});

		HudRenderer.register();
		OverlayModule.register();
		TracersModule.register();

		// Safety net: nothing should stay enabled - or unsaved - past the client shutting down.
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ModuleManager.getInstance().disableAll();
			ConfigManager.save();
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
