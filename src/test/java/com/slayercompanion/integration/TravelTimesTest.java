package com.slayercompanion.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.slayercompanion.data.Location;
import com.slayercompanion.data.WorldPointDto;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class TravelTimesTest
{
	private static final WorldPoint ARDOUGNE = new WorldPoint(2662, 3305, 0);

	private static Location loc(String id, int x, int y)
	{
		Location l = new Location();
		l.setId(id);
		WorldPointDto e = new WorldPointDto();
		e.setX(x);
		e.setY(y);
		l.setEntrance(e);
		return l;
	}

	private static final List<Location> LOCS = Arrays.asList(loc("chasm", 1435, 3671), loc("catacombs", 1636, 3673));

	private static ShortestPath installed()
	{
		ShortestPath sp = mock(ShortestPath.class);
		when(sp.available()).thenReturn(true);
		when(sp.query(anyString(), any())).thenReturn(true);
		return sp;
	}

	private static Map<String, Object> result(String id, boolean reached, int cost)
	{
		Map<String, Object> m = new HashMap<>();
		m.put("id", id);
		m.put("reached", reached);
		m.put("cost", cost);
		return m;
	}

	private static String idFor(ShortestPath sp, String locationId)
	{
		ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
		verify(sp, org.mockito.Mockito.atLeastOnce()).query(ids.capture(), any());
		String found = null;
		for (String id : ids.getAllValues())
		{
			if (id.endsWith(":" + locationId))
			{
				found = id; // the latest query for it
			}
		}
		return found;
	}

	@Test
	public void routeCostInTicksBecomesSeconds()
	{
		TravelTimes t = new TravelTimes();
		ShortestPath sp = installed();
		t.refresh(LOCS, ARDOUGNE, sp);
		verify(sp, times(2)).query(anyString(), any());
		assertFalse(t.seconds("chasm").isPresent());

		assertTrue(t.onResult(result(idFor(sp, "chasm"), true, 150)));
		assertEquals(90, t.seconds("chasm").getAsDouble(), 0.001); // 150 ticks x 0.6 s
	}

	@Test
	public void unreachedAndForeignResultsAreIgnored()
	{
		TravelTimes t = new TravelTimes();
		ShortestPath sp = installed();
		t.refresh(LOCS, ARDOUGNE, sp);
		assertFalse(t.onResult(result(idFor(sp, "chasm"), false, 150)));
		assertFalse(t.onResult(result("other plugin", true, 10)));
		assertFalse(t.seconds("chasm").isPresent());
	}

	@Test
	public void asksAgainOnlyAfterMovingFar()
	{
		TravelTimes t = new TravelTimes();
		ShortestPath sp = installed();
		t.refresh(LOCS, ARDOUGNE, sp);
		String first = idFor(sp, "chasm");
		t.refresh(LOCS, ARDOUGNE.dx(10), sp);
		verify(sp, times(2)).query(anyString(), any());
		assertFalse(t.isStale(ARDOUGNE.dx(10)));

		assertTrue(t.isStale(ARDOUGNE.dx(200)));
		t.refresh(LOCS, ARDOUGNE.dx(200), sp);
		verify(sp, times(4)).query(anyString(), any());
		assertFalse("answers to the old position are dropped", t.onResult(result(first, true, 500)));
	}

	@Test
	public void withoutShortestPathNothingIsAsked()
	{
		TravelTimes t = new TravelTimes();
		ShortestPath sp = mock(ShortestPath.class);
		t.refresh(LOCS, ARDOUGNE, sp);
		verify(sp, never()).query(anyString(), any());
		assertFalse(t.seconds("chasm").isPresent());
	}
}
