package com.slayercompanion.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Integrity checks on the bundled Chaeldar task, location and safespot data. */
public class BundledDataTest
{
	private static final String DIR = "/com/slayercompanion/";
	private static final Set<String> ALLOWED_TAGS = new HashSet<>(java.util.Arrays.asList(
		"Safespot", "Multi-combat", "Single-combat", "Catacombs bonus drops", "Wilderness", "Cannon allowed"));

	private static <T> List<T> read(String file, java.lang.reflect.Type type) throws Exception
	{
		try (InputStreamReader r = new InputStreamReader(
			BundledDataTest.class.getResourceAsStream(DIR + file), StandardCharsets.UTF_8))
		{
			return new Gson().fromJson(r, type);
		}
	}

	private static List<SlayerTask> tasks() throws Exception
	{
		return read("tasks.json", new TypeToken<List<SlayerTask>>() {}.getType());
	}

	private static List<Location> locations() throws Exception
	{
		return read("locations.json", new TypeToken<List<Location>>() {}.getType());
	}

	private static List<Safespot> safespots() throws Exception
	{
		return read("safespots.json", new TypeToken<List<Safespot>>() {}.getType());
	}

	@Test
	public void everyChaeldarTaskIsCovered() throws Exception
	{
		GameData data = GameData.load(DIR);
		Map<String, Location> byId = new HashMap<>();
		for (Location l : locations())
		{
			assertNotNull(l.getId());
			assertTrue("duplicate location id " + l.getId(), byId.put(l.getId(), l) == null);
		}
		int chaeldar = 0;
		for (SlayerTask t : tasks())
		{
			if (!t.getMasters().contains("Chaeldar"))
			{
				continue;
			}
			chaeldar++;
			assertFalse(t.getName() + " has no monsters", t.getMonsterIds().isEmpty());
			for (int id : t.getMonsterIds())
			{
				assertTrue(t.getName() + " monster " + id, data.monster(id).isPresent());
			}
			assertFalse(t.getName() + " has no locations", t.getLocationIds().isEmpty());
			for (String lid : t.getLocationIds())
			{
				Location l = byId.get(lid);
				assertNotNull(t.getName() + " location " + lid, l);
				assertEquals(t.getId(), l.getTaskId());
				assertTrue(l.getName() + " monster " + l.getMonsterId(), data.monster(l.getMonsterId()).isPresent());
				assertNotNull(l.getEntrance());
				for (String q : l.getQuestReqs())
				{
					Quest.valueOf(q);
				}
			}
			for (ItemNeed n : t.getRequiredItems())
			{
				assertFalse(n.getItemIds().isEmpty());
			}
			assertTrue(data.taskByName(t.getName()).isPresent());
		}
		assertTrue(chaeldar > 0);
	}

	@Test
	public void safespotsReferenceKnownLocations() throws Exception
	{
		Set<String> ids = new HashSet<>();
		for (Location l : locations())
		{
			ids.add(l.getId());
		}
		for (Safespot s : safespots())
		{
			assertTrue("unknown location " + s.getLocationId(), ids.contains(s.getLocationId()));
			assertNotNull(s.getTile());
		}
	}

	@Test
	public void tagsAreFromAllowedSet() throws Exception
	{
		for (Location l : locations())
		{
			for (String tag : l.getTags())
			{
				assertTrue(l.getId() + " has tag " + tag, ALLOWED_TAGS.contains(tag));
			}
			assertTrue(l.getId(), l.getTags().contains("Multi-combat") ^ l.getTags().contains("Single-combat"));
		}
	}

	@Test
	public void builtinSafespotsAreUnverified() throws Exception
	{
		for (Safespot s : safespots())
		{
			assertEquals("builtin", s.getSource());
			assertFalse(s.isVerified());
			assertFalse(s.getNote() == null || s.getNote().isEmpty());
		}
	}
}
