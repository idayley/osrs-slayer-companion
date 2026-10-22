package com.slayercompanion.integration;

import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.PluginManager;

/** Asks the Shortest Path plugin (if installed and enabled) to draw or time a route, via PluginMessage. */
public class ShortestPath
{
	private static final String PLUGIN_NAME = "Shortest Path";
	public static final String NAMESPACE = "shortestpath";
	/** Shortest Path's answer to a {@link #query}; carries the query's "id" and the route "cost" in ticks. */
	public static final String RESULT = "result";

	private final EventBus eventBus;
	private final PluginManager pluginManager;

	public ShortestPath(EventBus eventBus, PluginManager pluginManager)
	{
		this.eventBus = eventBus;
		this.pluginManager = pluginManager;
	}

	/** True when a plugin named "Shortest Path" is loaded and enabled. */
	public boolean available()
	{
		return pluginManager.getPlugins().stream()
			.anyMatch(p -> PLUGIN_NAME.equals(p.getName()) && pluginManager.isPluginEnabled(p));
	}

	/** Requests a path from the player's location to target; false (nothing posted) if unavailable. */
	public boolean pathTo(WorldPoint target)
	{
		if (target == null || !available())
		{
			return false;
		}
		eventBus.post(new PluginMessage(NAMESPACE, "path", Map.of("target", target)));
		return true;
	}

	/**
	 * Finds a route from the player's location to target without drawing it; Shortest Path answers with a
	 * {@link #RESULT} message carrying this id. False (nothing posted) if unavailable.
	 */
	public boolean query(String id, WorldPoint target)
	{
		if (target == null || !available())
		{
			return false;
		}
		eventBus.post(new PluginMessage(NAMESPACE, "query", Map.of("id", id, "target", target)));
		return true;
	}
}
