package dev.owczon.vectraclient.module;

import net.minecraft.client.Minecraft;

/**
 * A module whose whole job is to draw one line of text on the HUD.
 *
 * Every display module lives in the Render category. Returning {@code null} from
 * {@link #getHudText(Minecraft)} skips the line for that frame (for example, when the value isn't
 * available yet) instead of printing a placeholder.
 */
public abstract class DisplayModule extends Module {

	protected DisplayModule(String name, String description) {
		super(name, description, ModuleCategory.RENDER);
	}

	/**
	 * @return the line to draw, or {@code null} to draw nothing this frame
	 */
	public abstract String getHudText(Minecraft client);
}
