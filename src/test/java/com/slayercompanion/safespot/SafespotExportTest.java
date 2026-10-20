package com.slayercompanion.safespot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.slayercompanion.data.Safespot;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class SafespotExportTest
{
	private static List<Safespot> parse(String json)
	{
		return new Gson().fromJson(json, new TypeToken<List<Safespot>>() {}.getType());
	}

	@Test
	public void exportMatchesBundledFormat()
	{
		SafespotStore store = new SafespotStore(Collections.emptyList(), null);
		store.mark("loc", 5, new WorldPoint(10, 20, 1), "behind rock");
		List<Safespot> parsed = parse(SafespotExport.export(store));
		assertEquals(1, parsed.size());
		assertEquals(store.forLocation("loc"), parsed);
		Safespot s = parsed.get(0);
		assertEquals("player", s.getSource());
		assertTrue(s.isVerified());
		assertEquals(1, s.getTile().getPlane());
	}

	@Test
	public void exportExcludesUntouchedBuiltin()
	{
		SafespotStore store = new SafespotStore(List.of(SafespotStoreTest.builtin("loc", 1, 3, 4)), null);
		assertEquals(0, parse(SafespotExport.export(store)).size());
	}

	@Test
	public void exportIncludesConfirmedBuiltin()
	{
		SafespotStore store = new SafespotStore(List.of(SafespotStoreTest.builtin("loc", 1, 3, 4)), null);
		store.confirm(new WorldPoint(3, 4, 0));
		List<Safespot> parsed = parse(SafespotExport.export(store));
		assertEquals(1, parsed.size());
		assertTrue(parsed.get(0).isVerified());
		assertEquals("builtin", parsed.get(0).getSource());
	}
}
