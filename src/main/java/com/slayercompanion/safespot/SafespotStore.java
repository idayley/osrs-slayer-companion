package com.slayercompanion.safespot;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.slayercompanion.data.Safespot;
import com.slayercompanion.data.WorldPointDto;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

/** Builtin safespots plus the player's own marks and verify/reject decisions. */
public class SafespotStore
{
	private static final Gson GSON = new Gson();

	/** Persisted form: only what the player changed. */
	private static class Saved
	{
		List<Safespot> player = new ArrayList<>();
		List<WorldPointDto> confirmed = new ArrayList<>();
		List<WorldPointDto> rejected = new ArrayList<>();
	}

	private final List<Safespot> builtin;
	private final List<Safespot> player = new ArrayList<>();
	private final Set<WorldPoint> confirmed = new LinkedHashSet<>();
	private final Set<WorldPoint> rejected = new LinkedHashSet<>();

	public SafespotStore(List<Safespot> builtin, String savedJson)
	{
		this.builtin = new ArrayList<>(builtin);
		if (savedJson == null || savedJson.trim().isEmpty())
		{
			return;
		}
		try
		{
			Saved saved = GSON.fromJson(savedJson, Saved.class);
			if (saved == null)
			{
				return;
			}
			if (saved.player != null)
			{
				saved.player.stream().filter(s -> s != null && s.getTile() != null).forEach(player::add);
			}
			if (saved.confirmed != null)
			{
				saved.confirmed.stream().filter(Objects::nonNull).map(WorldPointDto::toWorldPoint).forEach(confirmed::add);
			}
			if (saved.rejected != null)
			{
				saved.rejected.stream().filter(Objects::nonNull).map(WorldPointDto::toWorldPoint).forEach(rejected::add);
			}
		}
		catch (JsonParseException e)
		{
			// corrupt saved data: fall back to builtin only
			player.clear();
			confirmed.clear();
			rejected.clear();
		}
	}

	private static WorldPoint point(Safespot s)
	{
		return s.getTile().toWorldPoint();
	}

	private static WorldPointDto dto(WorldPoint p)
	{
		WorldPointDto d = new WorldPointDto();
		d.setX(p.getX());
		d.setY(p.getY());
		d.setPlane(p.getPlane());
		return d;
	}

	private Safespot effective(Safespot b)
	{
		if (!confirmed.contains(point(b)))
		{
			return b;
		}
		Safespot copy = new Safespot();
		copy.setLocationId(b.getLocationId());
		copy.setMonsterId(b.getMonsterId());
		copy.setTile(b.getTile());
		copy.setNote(b.getNote());
		copy.setSource(b.getSource());
		copy.setVerified(true);
		return copy;
	}

	/** Visible (non-rejected) spots for a location, builtin first. */
	public List<Safespot> forLocation(String locationId)
	{
		List<Safespot> out = new ArrayList<>();
		for (Safespot b : builtin)
		{
			if (Objects.equals(b.getLocationId(), locationId) && !rejected.contains(point(b)))
			{
				out.add(effective(b));
			}
		}
		player.stream().filter(s -> Objects.equals(s.getLocationId(), locationId)).forEach(out::add);
		return out;
	}

	public boolean hasAny(String locationId)
	{
		return !forLocation(locationId).isEmpty();
	}

	public void mark(String locationId, int monsterId, WorldPoint tile, String note)
	{
		rejected.remove(tile);
		player.removeIf(s -> point(s).equals(tile) && Objects.equals(s.getLocationId(), locationId));
		Safespot s = new Safespot();
		s.setLocationId(locationId);
		s.setMonsterId(monsterId);
		s.setTile(dto(tile));
		s.setNote(note == null ? "" : note.trim());
		s.setVerified(true);
		s.setSource("player");
		player.add(s);
	}

	/** Marks a builtin spot at this tile as verified. */
	public void confirm(WorldPoint tile)
	{
		rejected.remove(tile);
		if (builtin.stream().anyMatch(b -> point(b).equals(tile)))
		{
			confirmed.add(tile);
		}
	}

	/** Hides the spot at this tile (removes a player mark, hides a builtin). */
	public void reject(WorldPoint tile)
	{
		player.removeIf(s -> point(s).equals(tile));
		confirmed.remove(tile);
		if (builtin.stream().anyMatch(b -> point(b).equals(tile)))
		{
			rejected.add(tile);
		}
	}

	/** Player marks plus builtin spots the player confirmed, as they should appear in safespots.json. */
	List<Safespot> exportable()
	{
		List<Safespot> out = builtin.stream()
			.filter(b -> confirmed.contains(point(b)) && !rejected.contains(point(b)))
			.map(this::effective)
			.collect(Collectors.toList());
		out.addAll(player);
		return out;
	}

	public String toJson()
	{
		Saved saved = new Saved();
		saved.player = new ArrayList<>(player);
		confirmed.forEach(p -> saved.confirmed.add(dto(p)));
		rejected.forEach(p -> saved.rejected.add(dto(p)));
		return GSON.toJson(saved);
	}
}
