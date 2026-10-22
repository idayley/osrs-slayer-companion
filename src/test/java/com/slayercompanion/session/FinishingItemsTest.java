package com.slayercompanion.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.runelite.api.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class FinishingItemsTest
{
	@Test
	public void tasksMapToTheirItem()
	{
		assertSame(FinishingItems.ICE_COOLER, FinishingItems.forTask("Lizards").get());
		assertSame(FinishingItems.ICE_COOLER, FinishingItems.forMonster("Desert Lizard").get());
		assertSame(FinishingItems.ICE_COOLER, FinishingItems.forMonster("Small Lizard").get());
		assertSame(FinishingItems.BAG_OF_SALT, FinishingItems.forTask("Rockslugs").get());
		assertSame(FinishingItems.BAG_OF_SALT, FinishingItems.forMonster("Giant rockslug").get());
		assertSame(FinishingItems.FUNGICIDE, FinishingItems.forTask("Mutated Zygomites").get());
		assertSame(FinishingItems.FUNGICIDE, FinishingItems.forMonster("Ancient Zygomite").get());
	}

	@Test
	public void toolsAndOtherTasksHaveNone()
	{
		assertFalse(FinishingItems.forTask("Gargoyles").isPresent());
		assertFalse(FinishingItems.forTask("Lizardmen").isPresent());
		assertFalse(FinishingItems.forTask("Kurask").isPresent());
		assertFalse(FinishingItems.forTask(null).isPresent());
	}

	@Test
	public void countsSimpleItems()
	{
		Map<Integer, Integer> c = new HashMap<>();
		c.put(ItemID.ICE_COOLER, 23);
		c.put(ItemID.SHARK, 5);
		assertEquals(23, FinishingItems.ICE_COOLER.usesCarried(c));
		assertEquals(0, FinishingItems.BAG_OF_SALT.usesCarried(c));
	}

	@Test
	public void countsSprayChargesAndRefills()
	{
		Map<Integer, Integer> c = new HashMap<>();
		c.put(ItemID.FUNGICIDE_SPRAY_7, 1);
		c.put(ItemID.FUNGICIDE_SPRAY_10, 1);
		c.put(ItemID.FUNGICIDE_SPRAY_0, 1);
		c.put(ItemID.FUNGICIDE, 3);
		assertEquals(7 + 10 + 30, FinishingItems.FUNGICIDE.usesCarried(c));
	}

	@Test
	public void emptySprayStillHoldsRefills()
	{
		Map<Integer, Integer> c = new HashMap<>();
		c.put(ItemID.FUNGICIDE_SPRAY_0, 1);
		c.put(ItemID.FUNGICIDE, 2);
		assertEquals(20, FinishingItems.FUNGICIDE.usesCarried(c));
	}

	@Test
	public void refillsWithoutSprayCountNothing()
	{
		Map<Integer, Integer> c = new HashMap<>();
		c.put(ItemID.FUNGICIDE, 4);
		assertEquals(0, FinishingItems.FUNGICIDE.usesCarried(c));
	}

	@Test
	public void countsFromSupplyTracker()
	{
		SupplyTracker t = new SupplyTracker(id -> 0L, id -> null);
		assertEquals(0, FinishingItems.BAG_OF_SALT.usesCarried(t));
		Map<Integer, Integer> c = new HashMap<>();
		c.put(ItemID.BAG_OF_SALT, 12);
		t.onSnapshot(c);
		assertEquals(12, FinishingItems.BAG_OF_SALT.usesCarried(t));
	}

	@Test
	public void lineText()
	{
		assertEquals(Optional.of("23 ice coolers · 40 left"), FinishingItems.ICE_COOLER.line(23, 40));
		assertEquals(Optional.of("1 bag of salt · 3 left"), FinishingItems.BAG_OF_SALT.line(1, 3));
		assertEquals(Optional.of("No fungicide charges carried · 9 left"), FinishingItems.FUNGICIDE.line(0, 9));
		assertFalse(FinishingItems.ICE_COOLER.line(5, 0).isPresent());
	}

	@Test
	public void warningWhenShort()
	{
		assertEquals(Optional.of("Ice coolers run out ~17 kills before task end"),
			FinishingItems.ICE_COOLER.warning(23, 40));
		assertEquals(Optional.of("Bags of salt run out ~1 kill before task end"),
			FinishingItems.BAG_OF_SALT.warning(9, 10));
		assertEquals(Optional.of("No fungicide charges carried · needed to finish kills"),
			FinishingItems.FUNGICIDE.warning(0, 10));
		assertFalse(FinishingItems.ICE_COOLER.warning(40, 40).isPresent());
		assertFalse(FinishingItems.ICE_COOLER.warning(0, 0).isPresent());
	}
}
