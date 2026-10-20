package com.slayercompanion.safespot;

import static org.junit.Assert.assertSame;

import com.slayercompanion.data.Location;
import com.slayercompanion.data.WorldPointDto;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class SafespotMenuTest
{
	private static Location loc(String id, int x, int y, int plane)
	{
		Location l = new Location();
		l.setId(id);
		l.setName(id);
		WorldPointDto e = new WorldPointDto();
		e.setX(x);
		e.setY(y);
		e.setPlane(plane);
		l.setEntrance(e);
		return l;
	}

	private static final Location WATERFALL = loc("waterfall", 2575, 9891, 0);
	private static final Location CATACOMBS = loc("catacombs", 1629, 10056, 0);
	private static final Location TOWER = loc("tower", 2580, 9895, 1);
	private static final List<Location> ALL = Arrays.asList(WATERFALL, CATACOMBS, TOWER);

	@Test
	public void picksLocationWhoseEntranceIsNearestThePlayer()
	{
		// Catacombs selected in the panel, but the player stands in the Waterfall Dungeon
		assertSame(WATERFALL, SafespotMenu.locationForMark(ALL, CATACOMBS, new WorldPoint(2600, 9900, 0)));
	}

	@Test
	public void ignoresOtherPlanes()
	{
		assertSame(TOWER, SafespotMenu.locationForMark(ALL, CATACOMBS, new WorldPoint(2581, 9895, 1)));
		assertSame(CATACOMBS, SafespotMenu.locationForMark(Collections.singletonList(TOWER), CATACOMBS,
			new WorldPoint(2581, 9895, 0)));
	}

	@Test
	public void fallsBackToSelectedWhenNothingWithin150Tiles()
	{
		assertSame(CATACOMBS, SafespotMenu.locationForMark(ALL, CATACOMBS, new WorldPoint(3200, 3200, 0)));
		assertSame(WATERFALL, SafespotMenu.locationForMark(ALL, CATACOMBS, new WorldPoint(2575 + 150, 9891, 0)));
		assertSame(CATACOMBS, SafespotMenu.locationForMark(ALL, CATACOMBS, new WorldPoint(2575 + 151, 9891, 0)));
		assertSame(CATACOMBS, SafespotMenu.locationForMark(ALL, CATACOMBS, null));
	}
}
