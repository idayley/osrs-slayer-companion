package com.slayercompanion.safespot;

import com.google.gson.Gson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.data.Safespot;
import com.slayercompanion.data.WorldPointDto;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class SafespotStoreTest
{
	private static final Gson GSON = new Gson();

	static Safespot builtin(String loc, int monster, int x, int y)
	{
		Safespot s = new Safespot();
		s.setLocationId(loc);
		s.setMonsterId(monster);
		WorldPointDto t = new WorldPointDto();
		t.setX(x);
		t.setY(y);
		t.setPlane(0);
		s.setTile(t);
		s.setNote("wiki");
		s.setVerified(false);
		s.setSource("builtin");
		return s;
	}

	@Test
	public void markedSpotIsReturnedForLocation()
	{
		SafespotStore store = new SafespotStore(GSON, Collections.emptyList(), null);
		assertFalse(store.hasAny("loc"));
		store.mark("loc", 5, new WorldPoint(10, 20, 0), "behind rock");
		List<Safespot> spots = store.forLocation("loc");
		assertEquals(1, spots.size());
		assertTrue(spots.get(0).isVerified());
		assertEquals("player", spots.get(0).getSource());
		assertEquals("behind rock", spots.get(0).getNote());
		assertTrue(store.hasAny("loc"));
		assertTrue(store.forLocation("other").isEmpty());
	}

	@Test
	public void confirmFlipsVerified()
	{
		SafespotStore store = new SafespotStore(GSON, List.of(builtin("loc", 1, 3, 4)), "");
		assertFalse(store.forLocation("loc").get(0).isVerified());
		store.confirm(new WorldPoint(3, 4, 0));
		assertTrue(store.forLocation("loc").get(0).isVerified());
	}

	@Test
	public void rejectHidesBuiltin()
	{
		SafespotStore store = new SafespotStore(GSON, List.of(builtin("loc", 1, 3, 4)), null);
		store.reject(new WorldPoint(3, 4, 0));
		assertTrue(store.forLocation("loc").isEmpty());
		assertFalse(store.hasAny("loc"));
	}

	@Test
	public void persistsAcrossReload()
	{
		List<Safespot> b = List.of(builtin("loc", 1, 3, 4), builtin("loc", 1, 7, 8));
		SafespotStore store = new SafespotStore(GSON, b, null);
		store.mark("loc2", 9, new WorldPoint(1, 2, 1), "n");
		store.confirm(new WorldPoint(3, 4, 0));
		store.reject(new WorldPoint(7, 8, 0));
		SafespotStore reloaded = new SafespotStore(GSON, b, store.toJson());
		assertEquals(store.forLocation("loc"), reloaded.forLocation("loc"));
		assertEquals(1, reloaded.forLocation("loc").size());
		assertTrue(reloaded.forLocation("loc").get(0).isVerified());
		assertEquals(store.forLocation("loc2"), reloaded.forLocation("loc2"));
	}

	@Test
	public void badSavedJsonIsIgnored()
	{
		SafespotStore store = new SafespotStore(GSON, List.of(builtin("loc", 1, 3, 4)), "{not json");
		assertEquals(1, store.forLocation("loc").size());
	}

	@Test
	public void spotAtMatchesOnlyExactTileInLocation()
	{
		SafespotStore store = new SafespotStore(GSON, List.of(builtin("loc", 1, 3, 4)), null);
		assertEquals(3, store.spotAt("loc", new WorldPoint(3, 4, 0)).getTile().getX());
		assertEquals(null, store.spotAt("loc", new WorldPoint(3, 5, 0)));
		assertEquals(null, store.spotAt("loc", new WorldPoint(3, 4, 1)));
		assertEquals(null, store.spotAt("other", new WorldPoint(3, 4, 0)));
		store.reject(new WorldPoint(3, 4, 0));
		assertEquals(null, store.spotAt("loc", new WorldPoint(3, 4, 0)));
	}

	@Test
	public void loadReplacesPlayerStateAndCountsExportable()
	{
		SafespotStore a = new SafespotStore(GSON, List.of(builtin("loc", 1, 3, 4)), null);
		a.mark("loc", 1, new WorldPoint(10, 10, 0), "");
		a.confirm(new WorldPoint(3, 4, 0));
		assertEquals(2, a.exportableCount());

		SafespotStore b = new SafespotStore(GSON, List.of(builtin("loc", 1, 3, 4)), null);
		b.mark("loc", 1, new WorldPoint(20, 20, 0), "");
		b.load(a.toJson());
		assertEquals(2, b.exportableCount());
		assertEquals(null, b.spotAt("loc", new WorldPoint(20, 20, 0)));
		b.load(null);
		assertEquals(0, b.exportableCount());
		assertEquals(1, b.forLocation("loc").size());
	}
}
