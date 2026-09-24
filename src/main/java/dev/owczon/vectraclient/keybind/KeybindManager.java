package dev.owczon.vectraclient.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import dev.owczon.vectraclient.VectraClient;
import dev.owczon.vectraclient.gui.ClickGuiScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Registers the client's keybinds and opens the ClickGUI when the GUI key is pressed.
 *
 * The binding defaults to P and shows up in Options > Controls under its own category, so it can be
 * rebound or unbound from vanilla's menu without touching this file.
 */
public final class KeybindManager {

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(VectraClient.id("general"));

	private KeyMapping openGuiKey;

	public void register() {
		openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.vectra-client.opengui",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_P,
				CATEGORY
		));
	}

	/** Must run once per client tick; opens or closes the ClickGUI on a press of the GUI key. */
	public void onClientTick(Minecraft client) {
		if (openGuiKey == null) {
			return;
		}

		while (openGuiKey.consumeClick()) {
			if (client.gui.screen() instanceof ClickGuiScreen) {
				client.gui.setScreen(null);
			} else if (client.gui.screen() == null) {
				// Only from in-game: don't replace the pause menu or an open inventory.
				client.gui.setScreen(new ClickGuiScreen());
			}
		}
	}
}
