package com.slayercompanion.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class OwnedItemsTest
{
	@Test
	public void ownedItemsRoundTripJson()
	{
		OwnedItems o = new OwnedItems();
		Instant t = Instant.parse("2026-01-02T03:04:05Z");
		o.updateBank(Map.of(4151, 1, 995, 500), t);
		o.updateInventory(Map.of(4151, 1));
		o.updateWorn(Map.of(1234, 1));
		OwnedItems back = OwnedItems.fromJson(o.toJson());
		assertEquals(2, back.count(4151));
		assertEquals(500, back.count(995));
		assertEquals(1, back.count(1234));
		assertTrue(back.bankSeen());
		assertEquals(t, back.bankSeenAt().get());
	}

	@Test
	public void notedBankItemCountsAsOwned()
	{
		Map<Integer, Integer> canon = new HashMap<>();
		canon.put(4152, 4151);
		OwnedItems o = new OwnedItems(id -> canon.getOrDefault(id, id));
		o.updateBank(Map.of(4152, 3), Instant.now());
		assertEquals(3, o.count(4151));
	}

	@Test
	public void bankNotSeenByDefault()
	{
		assertFalse(new OwnedItems().bankSeen());
	}
}
