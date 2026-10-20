package com.slayercompanion.locations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.data.Location;
import com.slayercompanion.data.WorldPointDto;
import com.slayercompanion.gear.Style;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class LocationRankerTest
{
	private static final WorldPoint PLAYER = new WorldPoint(3000, 3000, 0);

	private static Location loc(String id, String name, int x, int y, int plane)
	{
		Location l = new Location();
		l.setId(id);
		l.setName(name);
		WorldPointDto e = new WorldPointDto();
		e.setX(x);
		e.setY(y);
		e.setPlane(plane);
		l.setEntrance(e);
		return l;
	}

	private static List<RankedLocation> rank(List<Location> locs, Style style, Function<Location, Optional<String>> lock,
		Optional<String> konar, Set<String> safe)
	{
		return LocationRanker.rank(locs, style, PLAYER, lock, konar, l -> safe.contains(l.getId()), l -> 0, l -> 0);
	}

	private static final Function<Location, Optional<String>> OPEN = l -> Optional.empty();

	@Test
	public void lockedLocationsGoLastWithReason()
	{
		Location near = loc("near", "Near", 3001, 3000, 0);
		Location far = loc("far", "Far", 3050, 3000, 0);
		List<RankedLocation> r = rank(Arrays.asList(near, far), Style.MELEE,
			l -> l == near ? Optional.of("Needs boots of stone") : Optional.empty(),
			Optional.empty(), Collections.emptySet());
		assertEquals("far", r.get(0).getLocation().getId());
		assertTrue(r.get(0).isUsable());
		assertFalse(r.get(1).isUsable());
		assertEquals("Needs boots of stone", r.get(1).getLockedReason());
		assertFalse(r.get(1).isRecommended());
	}

	@Test
	public void konarOnlyAllowsAssignedArea()
	{
		Location a = loc("a", "Kourend Catacombs", 3001, 3000, 0);
		Location b = loc("b", "Slayer Tower", 3002, 3000, 0);
		List<RankedLocation> r = rank(Arrays.asList(b, a), Style.MELEE, OPEN,
			Optional.of("kourend catacombs"), Collections.emptySet());
		assertEquals("a", r.get(0).getLocation().getId());
		assertTrue(r.get(0).isUsable());
		assertFalse(r.get(1).isUsable());
		assertEquals("Konar sent you elsewhere", r.get(1).getLockedReason());
	}

	@Test
	public void konarWithNoMatchLocksNothing()
	{
		Location a = loc("a", "Slayer Tower", 3001, 3000, 0);
		List<RankedLocation> r = rank(Arrays.asList(a), Style.MELEE, OPEN,
			Optional.of("Nowhere Land"), Collections.emptySet());
		assertTrue(r.get(0).isUsable());
	}

	@Test
	public void safespotFirstForRanged()
	{
		Location near = loc("near", "Near", 3001, 3000, 0);
		Location safe = loc("safe", "Safe", 3100, 3000, 0);
		List<RankedLocation> r = rank(Arrays.asList(near, safe), Style.RANGED, OPEN, Optional.empty(),
			new HashSet<>(Arrays.asList("safe")));
		assertEquals("safe", r.get(0).getLocation().getId());
		assertTrue(r.get(0).isHasSafespot());
	}

	@Test
	public void safespotIgnoredForMelee()
	{
		Location near = loc("near", "Near", 3001, 3000, 0);
		Location safe = loc("safe", "Safe", 3100, 3000, 0);
		List<RankedLocation> r = rank(Arrays.asList(safe, near), Style.MELEE, OPEN, Optional.empty(),
			new HashSet<>(Arrays.asList("safe")));
		assertEquals("near", r.get(0).getLocation().getId());
	}

	@Test
	public void differentPlaneOrFarRegionIsFixed120Seconds()
	{
		Location plane = loc("p", "P", 3000, 3000, 1);
		Location far = loc("f", "F", 3300, 3000, 0);
		List<RankedLocation> r = rank(Arrays.asList(plane, far), Style.MELEE, OPEN, Optional.empty(),
			Collections.emptySet());
		assertEquals(120, r.get(0).getTravelSeconds(), 0.001);
		assertEquals(120, r.get(1).getTravelSeconds(), 0.001);
	}

	@Test
	public void travelIsChebyshevTimesPointThree()
	{
		Location l = loc("a", "A", 3010, 3004, 0);
		List<RankedLocation> r = rank(Arrays.asList(l), Style.MELEE, OPEN, Optional.empty(), Collections.emptySet());
		assertEquals(3.0, r.get(0).getTravelSeconds(), 0.001);
	}

	@Test
	public void recommendedWhyText()
	{
		Location near = loc("near", "Near", 3001, 3000, 0);
		Location safe = loc("safe", "Safe", 3002, 3000, 0);
		List<RankedLocation> r = rank(Arrays.asList(near, safe), Style.RANGED, OPEN, Optional.empty(),
			new HashSet<>(Arrays.asList("safe", "near")));
		assertTrue(r.get(0).isRecommended());
		assertEquals("Picked because it has a safespot and is closest.", r.get(0).getWhy());
		assertFalse(r.get(1).isRecommended());
	}

	@Test
	public void recommendedWhySingleAndFallback()
	{
		Location a = loc("a", "A", 3001, 3000, 0);
		List<RankedLocation> melee = rank(Arrays.asList(a), Style.MELEE, OPEN, Optional.empty(), Collections.emptySet());
		assertEquals("Picked because it is closest.", melee.get(0).getWhy());

		// Safespot chosen but farther than a locked-out... usable but not closest
		Location close = loc("close", "Close", 3001, 3000, 0);
		Location safe = loc("safe", "Safe", 3100, 3000, 0);
		List<RankedLocation> r = rank(Arrays.asList(close, safe), Style.MAGIC, OPEN, Optional.empty(),
			new HashSet<>(Arrays.asList("safe")));
		assertEquals("Picked because it has a safespot.", r.get(0).getWhy());
	}

	@Test
	public void noLocationsReturnsEmptyList()
	{
		assertTrue(rank(Collections.emptyList(), Style.MELEE, OPEN, Optional.empty(), Collections.emptySet()).isEmpty());
	}

	@Test
	public void allLockedMeansNoRecommendation()
	{
		Location a = loc("a", "A", 3001, 3000, 0);
		List<RankedLocation> r = rank(Arrays.asList(a), Style.MELEE, l -> Optional.of("x"), Optional.empty(),
			Collections.emptySet());
		assertFalse(r.get(0).isRecommended());
	}
}
