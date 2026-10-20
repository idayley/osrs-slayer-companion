package com.slayercompanion.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Immutable-after-load bundle of the static game data shipped in the plugin jar. */
public class GameData
{
	private static final Gson GSON = new Gson();

	private final Map<Integer, Monster> monsters = new HashMap<>();
	private final Map<Integer, Equipment> equipment = new HashMap<>();
	private final Map<String, SlayerTask> tasksByName = new HashMap<>();
	private final Map<String, List<Location>> locationsByTask = new HashMap<>();
	private final List<Safespot> safespots;

	private GameData(List<Monster> m, List<Equipment> e, List<SlayerTask> t, List<Location> l, List<Safespot> s)
	{
		m.forEach(x -> monsters.put(x.getId(), x));
		e.forEach(x -> equipment.put(x.getItemId(), x));
		t.forEach(x -> tasksByName.put(x.getName().toLowerCase(Locale.ROOT), x));
		l.forEach(x -> locationsByTask.computeIfAbsent(x.getTaskId(), k -> new ArrayList<>()).add(x));
		safespots = Collections.unmodifiableList(new ArrayList<>(s));
	}

	/**
	 * Loads all data files from a classpath directory.
	 *
	 * @throws IllegalStateException naming the file if one is missing or unreadable
	 */
	public static GameData load(String resourceDir)
	{
		String dir = resourceDir.endsWith("/") ? resourceDir : resourceDir + "/";
		return new GameData(
			read(dir, "monsters.json", new TypeToken<List<Monster>>() {}.getType()),
			read(dir, "equipment.json", new TypeToken<List<Equipment>>() {}.getType()),
			read(dir, "tasks.json", new TypeToken<List<SlayerTask>>() {}.getType()),
			read(dir, "locations.json", new TypeToken<List<Location>>() {}.getType()),
			read(dir, "safespots.json", new TypeToken<List<Safespot>>() {}.getType()));
	}

	private static <T> List<T> read(String dir, String file, Type type)
	{
		String path = dir + file;
		try (InputStream in = GameData.class.getResourceAsStream(path))
		{
			if (in == null)
			{
				throw new IllegalStateException("Missing game data resource: " + file + " (" + path + ")");
			}
			List<T> result = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
			return result == null ? Collections.emptyList() : result;
		}
		catch (IOException | RuntimeException e)
		{
			if (e instanceof IllegalStateException)
			{
				throw (IllegalStateException) e;
			}
			throw new IllegalStateException("Failed to read game data file " + file + ": " + e.getMessage(), e);
		}
	}

	public Optional<Monster> monster(int id)
	{
		return Optional.ofNullable(monsters.get(id));
	}

	public Optional<Equipment> equipment(int itemId)
	{
		return Optional.ofNullable(equipment.get(itemId));
	}

	public Collection<Equipment> allEquipment()
	{
		return Collections.unmodifiableCollection(equipment.values());
	}

	public Optional<SlayerTask> taskByName(String name)
	{
		return name == null ? Optional.empty() : Optional.ofNullable(tasksByName.get(name.toLowerCase(Locale.ROOT)));
	}

	public List<Location> locations(String taskId)
	{
		return Collections.unmodifiableList(locationsByTask.getOrDefault(taskId, Collections.emptyList()));
	}

	public List<Safespot> builtinSafespots()
	{
		return safespots;
	}
}
