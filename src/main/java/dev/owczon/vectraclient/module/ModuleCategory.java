package dev.owczon.vectraclient.module;

/**
 * The categories shown down the left-hand side of the ClickGUI.
 *
 * New categories can be added here; the ClickGUI renders one button per entry automatically, so
 * a category with no modules in it yet simply shows as an empty panel rather than disappearing.
 */
public enum ModuleCategory {

	RENDER("Render"),
	COMBAT("Combat"),
	MOVEMENT("Movement"),
	WORLD("World"),
	UTILITY("Utility"),
	UNCATEGORIZED("Uncategorized");

	private final String displayName;

	ModuleCategory(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
