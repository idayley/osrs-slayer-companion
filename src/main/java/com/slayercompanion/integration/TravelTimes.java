package com.slayercompanion.integration;

import com.slayercompanion.data.Location;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Route times to each location from Shortest Path's "query" API, so travel accounts for the player's
 * teleports, fairy rings and transport. Until an answer arrives (or without Shortest Path) callers fall
 * back to a straight-line estimate. Client thread only.
 */
public class TravelTimes
{
	static final double SECONDS_PER_TICK = 0.6;
	/** Ask again once the player is this many tiles from where the last routes started. */
	static final int REQUERY_TILES = 30;
	private static final String ID_PREFIX = "slayercompanion:";

	private final Map<String, Double> seconds = new HashMap<>();
	private Set<String> askedFor = Collections.emptySet();
	private WorldPoint askedFrom;
	private int generation;

	/**
	 * Queries routes when the locations changed or the player moved far (or teleported) since the last ask.
	 * Earlier answers stay in use until the new ones arrive.
	 */
	public void refresh(List<Location> locations, WorldPoint player, ShortestPath shortestPath)
	{
		Set<String> ids = new HashSet<>();
		locations.forEach(l -> ids.add(l.getId()));
		if (player == null || ids.isEmpty() || (ids.equals(askedFor) && !movedFar(player)))
		{
			return;
		}
		if (!shortestPath.available())
		{
			seconds.clear();
			askedFor = Collections.emptySet();
			return;
		}
		askedFor = ids;
		askedFrom = player;
		generation++;
		for (Location l : locations)
		{
			if (l.routeTarget() != null)
			{
				shortestPath.query(ID_PREFIX + generation + ":" + l.getId(), l.routeTarget().toWorldPoint());
			}
		}
	}

	/**
	 * Stores the route time from a Shortest Path "result" message if it answers this class's latest queries.
	 * @return true when a time was stored, so the ranking should be rebuilt
	 */
	public boolean onResult(Map<String, Object> data)
	{
		Object id = data == null ? null : data.get("id");
		String prefix = ID_PREFIX + generation + ":";
		if (!(id instanceof String) || !((String) id).startsWith(prefix))
		{
			return false;
		}
		Object cost = data.get("cost");
		if (!Boolean.TRUE.equals(data.get("reached")) || !(cost instanceof Number) || ((Number) cost).intValue() < 0)
		{
			return false;
		}
		seconds.put(((String) id).substring(prefix.length()), ((Number) cost).intValue() * SECONDS_PER_TICK);
		return true;
	}

	/** The routed travel time to this location, if Shortest Path has answered. */
	public OptionalDouble seconds(String locationId)
	{
		Double s = seconds.get(locationId);
		return s == null ? OptionalDouble.empty() : OptionalDouble.of(s);
	}

	/** The player has moved far (or teleported) since routes were last asked for, so they should be asked again. */
	public boolean isStale(WorldPoint player)
	{
		return askedFrom != null && player != null && movedFar(player);
	}

	private boolean movedFar(WorldPoint player)
	{
		return askedFrom == null || player.getPlane() != askedFrom.getPlane()
			|| Math.max(Math.abs(player.getX() - askedFrom.getX()), Math.abs(player.getY() - askedFrom.getY())) > REQUERY_TILES;
	}
}
