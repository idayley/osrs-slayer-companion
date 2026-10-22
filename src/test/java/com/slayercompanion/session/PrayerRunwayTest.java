package com.slayercompanion.session;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import net.runelite.api.ItemID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrayerRunwayTest
{
	private PrayerRunway r;

	@Before
	public void setUp()
	{
		r = new PrayerRunway();
	}

	@Test
	public void restoreFormulas()
	{
		assertEquals(7 + 17, PrayerRunway.restorePerDose("Prayer potion", 70));
		assertEquals(8 + 17, PrayerRunway.restorePerDose("Super restore", 70));
		assertEquals(8 + 24, PrayerRunway.restorePerDose("Sanfew serum", 99));
		assertEquals(8 + 10, PrayerRunway.restorePerDose("Blighted super restore", 43));
		assertEquals(0, PrayerRunway.restorePerDose("Ranging potion", 70));
	}

	@Test
	public void drainWithNoPotions()
	{
		r.onPrayerPoints(70);
		r.onPrayerPoints(65);
		r.onKill(1);
		// drain before the first kill is not counted
		r.onPrayerPoints(60);
		r.onPrayerPoints(55);
		r.onKill(1);
		r.onPrayerPoints(50);
		r.onKill(1);
		assertEquals(15, r.drained());
		assertEquals(7.5, r.drainPerKill().getAsDouble(), 1e-9);
		// 50 points / 7.5 = 6.67 -> 6
		assertEquals(OptionalInt.of(6), r.killsLeft(50, 0, 70));
		// plus 2 prayer potion doses at 70: 50 + 2 * 24 = 98 / 7.5 = 13
		assertEquals(OptionalInt.of(13), r.killsLeft(50, 2, 70));
	}

	@Test
	public void drinkingADoseIsNotDrain()
	{
		r.onPrayerPoints(40);
		r.onKill(1);
		r.onPrayerPoints(30);
		r.onKill(1);
		r.onPrayerPoints(54); // drank a dose
		r.onPrayerPoints(44);
		r.onKill(1);
		assertEquals(20, r.drained());
		assertEquals(10.0, r.drainPerKill().getAsDouble(), 1e-9);
	}

	@Test
	public void notPrayingIsEmpty()
	{
		r.onPrayerPoints(70);
		r.onKill(1);
		r.onKill(1);
		r.onKill(1);
		OptionalInt left = r.killsLeft(70, 4, 70);
		assertFalse(left.isPresent());
		assertEquals("70 prayer points", PrayerRunway.line(left, 70));
		assertFalse(PrayerRunway.warning(left, 30).isPresent());
	}

	@Test
	public void emptyBeforeAMeasuredKill()
	{
		r.onPrayerPoints(70);
		r.onKill(1);
		r.onPrayerPoints(60);
		assertFalse(r.killsLeft(60, 0, 70).isPresent());
	}

	@Test
	public void resetClearsDrain()
	{
		r.onPrayerPoints(70);
		r.onKill(1);
		r.onPrayerPoints(60);
		r.onKill(1);
		assertTrue(r.drainPerKill().isPresent());
		r.reset();
		assertEquals(0, r.drained());
		assertFalse(r.drainPerKill().isPresent());
		// drain before the new task's first kill is not counted; the last reading stays the baseline
		r.onPrayerPoints(55);
		r.onKill(1);
		r.onPrayerPoints(50);
		r.onKill(1);
		assertEquals(5, r.drained());
	}

	@Test
	public void linesAndWarning()
	{
		assertEquals("Lasts ~25 kills", PrayerRunway.line(OptionalInt.of(25), 40));
		assertEquals("Lasts ~1 kill", PrayerRunway.line(OptionalInt.of(1), 40));
		assertEquals("Prayer runs out ~15 kills before task end",
			PrayerRunway.warning(OptionalInt.of(25), 40).get());
		assertFalse(PrayerRunway.warning(OptionalInt.of(40), 40).isPresent());
	}

	@Test
	public void killsLeftFromSupplyTracker()
	{
		Map<Integer, String> names = new HashMap<>();
		names.put(ItemID.PRAYER_POTION4, "Prayer potion(4)");
		names.put(ItemID.SUPER_RESTORE2, "Super restore(2)");
		SupplyTracker s = new SupplyTracker(id -> 0, names::get);
		Map<Integer, Integer> inv = new HashMap<>();
		inv.put(ItemID.PRAYER_POTION4, 1);
		inv.put(ItemID.SUPER_RESTORE2, 1);
		s.onSnapshot(inv);

		r.onPrayerPoints(70);
		r.onKill(1);
		r.onPrayerPoints(60);
		r.onKill(1);
		// 10 + 4 * 24 + 2 * 25 = 156 / 10 per kill
		assertEquals(OptionalInt.of(15), r.killsLeft(10, s, 70));
	}
}
