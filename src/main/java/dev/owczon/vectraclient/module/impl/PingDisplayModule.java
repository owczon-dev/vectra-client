package dev.owczon.vectraclient.module.impl;

import dev.owczon.vectraclient.module.DisplayModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Draws your latency to the current server.
 *
 * Vanilla already tracks this per player list entry, so the value matches the ping bars you'd see
 * in the tab list. Returns nothing in singleplayer until the integrated server reports an entry.
 */
public final class PingDisplayModule extends DisplayModule {

	public PingDisplayModule() {
		super("Ping Display", "Shows your latency to the server in milliseconds.");
	}

	@Override
	public String getHudText(Minecraft client) {
		if (client.player == null || client.getConnection() == null) {
			return null;
		}

		PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
		if (info == null) {
			return null;
		}
		return "Ping " + info.getLatency() + " ms";
	}
}
