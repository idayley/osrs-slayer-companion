package com.slayercompanion.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BagPickupsTest
{
	private final BagPickups bags = new BagPickups();

	@Test
	public void herbSackNamesTheHerb()
	{
		BagPickups.Bagged b = bags.parse("You put the Grimy ranarr weed herb into your herb sack.");
		assertEquals("Grimy ranarr weed", b.name);
		assertEquals(1, b.qty);
		assertEquals("Grimy kwuarm", bags.parse("You put the Grimy kwuarm into your herb sack.").name);
	}

	@Test
	public void seedBoxNamesTheSeedAndCount()
	{
		BagPickups.Bagged b = bags.parse("You put 3 x Ranarr seed straight into your open seed box.");
		assertEquals("Ranarr seed", b.name);
		assertEquals(3, b.qty);
	}

	@Test
	public void gemBagUsesTheLastItemTaken()
	{
		assertNull(bags.parse("You put it straight into your open gem bag.")); // nothing taken yet
		bags.onTake(1619);
		BagPickups.Bagged b = bags.parse("You put it straight into your open gem bag.");
		assertEquals(1619, b.itemId);
		assertNull(b.name);
		assertEquals(1, b.qty);
		assertNull(bags.parse("You put it straight into your open gem bag.")); // each take used once
	}

	@Test
	public void otherMessagesAreIgnored()
	{
		assertNull(bags.parse("You look in your herb sack and see:"));
		assertNull(bags.parse("Stored 3 x Ranarr seed in your seed box.")); // filled from the inventory
		assertNull(bags.parse("Your Slayer task is complete."));
	}
}
