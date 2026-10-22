package com.slayercompanion.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class GameDataTest
{
	private static final String FIXTURE = "/com/slayercompanion/fixture/";

	@Test
	public void loadsFixture()
	{
		GameData data = GameData.load(FIXTURE);
		Monster m = data.monster(2075).get();
		assertEquals("Fire giant", m.getName());
		assertEquals(111, m.getHitpoints());
		assertEquals("water", m.getElementalWeakness());
		assertEquals(2, data.taskByName("fire GIANTS").get().getLocationIds().size());
		assertEquals(2, data.locations("fire_giants").size());
		assertFalse(data.builtinSafespots().get(0).isVerified());
		assertEquals(1, data.allEquipment().size());
		assertTrue(data.equipment(4151).isPresent());
		assertEquals(3200, data.locations("fire_giants").get(0).getEntrance().toWorldPoint().getX());
	}

	@Test
	public void chargedTridentPricesFromItsUnchargedState()
	{
		GameData data = GameData.load("/com/slayercompanion/");
		java.util.List<Integer> ids = new java.util.ArrayList<>();
		data.otherStates(12899).forEach(e -> ids.add(e.getItemId()));
		assertEquals(java.util.Collections.singletonList(12900), ids); // not the (e) or (o) versions
		assertEquals(78, (int) data.equipment(12899).get().getSkillReqs().get("magic"));
		assertEquals(75, (int) data.equipment(11905).get().getSkillReqs().get("magic"));
	}

	@Test
	public void missingResourceFailsLoudly()
	{
		try
		{
			GameData.load("/com/slayercompanion/nonexistent/");
			fail("expected IllegalStateException");
		}
		catch (IllegalStateException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("monsters.json"));
		}
	}
}
