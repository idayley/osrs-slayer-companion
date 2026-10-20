package com.slayercompanion.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.runelite.api.Quest;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Proves the generated bundled monsters.json / equipment.json parse into the Task 1 model. */
public class BundledGeneratedDataTest
{
	private static <T> List<T> read(String file, java.lang.reflect.Type type) throws Exception
	{
		try (InputStreamReader r = new InputStreamReader(
			BundledGeneratedDataTest.class.getResourceAsStream("/com/slayercompanion/" + file), StandardCharsets.UTF_8))
		{
			return new Gson().fromJson(r, type);
		}
	}

	@Test
	public void generatedFilesParse() throws Exception
	{
		List<Monster> monsters = read("monsters.json", new TypeToken<List<Monster>>() {}.getType());
		List<Equipment> equipment = read("equipment.json", new TypeToken<List<Equipment>>() {}.getType());
		assertFalse(monsters.isEmpty());
		assertFalse(equipment.isEmpty());

		Monster fireGiant = monsters.stream().filter(m -> m.getId() == 2075).findFirst().orElseThrow(AssertionError::new);
		assertEquals(111, fireGiant.getHitpoints());
		assertEquals("water", fireGiant.getElementalWeakness());
		assertFalse(fireGiant.getDrops().isEmpty());

		Equipment whip = equipment.stream().filter(e -> e.getItemId() == 4151).findFirst().orElseThrow(AssertionError::new);
		assertEquals("weapon", whip.getSlot());
		assertEquals(82, (int) whip.getBonuses().get("slash"));
		assertEquals(Integer.valueOf(70), whip.getSkillReqs().get("attack"));
		Equipment frozenWhip = equipment.stream().filter(e -> "Frozen abyssal whip".equals(e.getName()))
			.findFirst().orElseThrow(AssertionError::new);
		assertEquals(Integer.valueOf(70), frozenWhip.getSkillReqs().get("attack"));
		assertTrue(equipment.stream().anyMatch(e -> e.getSkillReqs().containsKey("slayer")));

		for (Equipment e : equipment)
		{
			assertNotNull(e.getName());
			for (String q : e.getQuestReqs())
			{
				Quest.valueOf(q); // throws if not a RuneLite Quest constant
			}
		}
		assertTrue(equipment.stream().anyMatch(e -> !e.getQuestReqs().isEmpty()));
	}
}
