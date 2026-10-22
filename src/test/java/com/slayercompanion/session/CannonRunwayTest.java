package com.slayercompanion.session;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import net.runelite.api.ItemID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CannonRunwayTest
{
	private static final int STEEL = ItemID.STEEL_CANNONBALL;
	private static final int GRANITE = ItemID.GRANITE_CANNONBALL;

	private CannonRunway c;

	@Before
	public void setUp()
	{
		c = new CannonRunway();
	}

	@Test
	public void varpIdsMatchRuneLiteCannonPlugin()
	{
		assertEquals(3, CannonRunway.AMMO_VARP);
		assertEquals(2, CannonRunway.STATE_VARP);
		assertTrue(CannonRunway.isPlaced(4));
		assertFalse(CannonRunway.isPlaced(0));
	}

	@Test
	public void loadingIsNotFiring()
	{
		c.update(true, 0, 500, 0);
		c.onKill(1);
		assertTrue(c.update(true, 30, 470, 0).isEmpty());
		assertEquals(0, c.ballsUsed());
		assertEquals(500, c.ballsLeft());
	}

	@Test
	public void firedBallsCountAfterFirstKill()
	{
		c.update(true, 30, 470, 0);
		assertTrue(c.update(true, 25, 470, 0).isEmpty());
		c.onKill(1);
		assertEquals(Map.of(STEEL, 5), c.update(true, 20, 470, 0));
		c.onKill(1);
		c.update(true, 15, 470, 0);
		assertEquals(10, c.ballsUsed());
		assertEquals(5.0, c.ballsPerKill().getAsDouble(), 1e-9);
		assertEquals(OptionalInt.of(97), c.killsLeft());
	}

	@Test
	public void refillMidTaskKeepsCounting()
	{
		c.update(true, 2, 100, 0);
		c.onKill(1);
		c.update(true, 0, 100, 0);
		c.update(true, 30, 70, 0);
		c.update(true, 28, 70, 0);
		assertEquals(4, c.ballsUsed());
	}

	@Test
	public void graniteIsCostedAsGranite()
	{
		c.update(true, 0, 0, 100);
		c.onKill(1);
		c.update(true, 30, 0, 70);
		assertEquals(Map.of(GRANITE, 3), c.update(true, 27, 0, 70));
	}

	@Test
	public void pickupAndPlacingResetBaseline()
	{
		c.update(true, 30, 70, 0);
		c.onKill(1);
		c.update(false, 0, 100, 0);
		c.update(true, 0, 100, 0);
		c.update(true, 30, 70, 0);
		assertEquals(0, c.ballsUsed());
	}

	@Test
	public void bankingIsNotFiring()
	{
		c.update(true, 30, 470, 0);
		c.onKill(1);
		c.setBankOpen(true);
		c.update(true, 30, 0, 0);
		c.setBankOpen(false);
		c.update(true, 30, 0, 0);
		assertEquals(0, c.ballsUsed());
	}

	@Test
	public void emptyWithNoCannon()
	{
		c.update(false, 0, 500, 0);
		c.onKill(3);
		assertFalse(c.placed());
		assertFalse(c.killsLeft().isPresent());
		assertFalse(c.summary().isPresent());
		assertFalse(c.warning(100).isPresent());
	}

	@Test
	public void resetClearsUsage()
	{
		c.update(true, 30, 0, 0);
		c.onKill(1);
		c.update(true, 20, 0, 0);
		c.reset();
		assertEquals(0, c.ballsUsed());
		assertEquals(0, c.kills());
		c.update(true, 10, 0, 0);
		assertEquals(0, c.ballsUsed());
	}

	@Test
	public void lineAndWarningWording()
	{
		assertEquals("~30 kills · 412 balls", CannonRunway.line(OptionalInt.of(30), 412));
		assertEquals("412 balls", CannonRunway.line(OptionalInt.empty(), 412));
		assertEquals("~1 kill · 1 ball", CannonRunway.line(OptionalInt.of(1), 1));
		assertEquals("Cannonballs run out ~12 kills before task end",
			CannonRunway.warning(OptionalInt.of(30), 42).get());
		assertFalse(CannonRunway.warning(OptionalInt.of(42), 42).isPresent());
		assertFalse(CannonRunway.warning(OptionalInt.empty(), 42).isPresent());
	}

	@Test
	public void summaryAndWarningFromState()
	{
		c.update(true, 30, 0, 0);
		c.onKill(1);
		c.update(true, 20, 0, 0);
		assertEquals("~2 kills · 20 balls", c.summary().get());
		assertEquals("Cannonballs run out ~3 kills before task end", c.warning(5).get());
	}

	@Test
	public void ballsCostedOnceInSupplyTracker()
	{
		Map<Integer, Long> prices = new HashMap<>();
		prices.put(STEEL, 200L);
		SupplyTracker s = new SupplyTracker(id -> prices.getOrDefault(id, 0L), id -> null);
		s.ignore(CannonRunway.BALL_IDS);

		s.onSnapshot(Map.of(STEEL, 500));
		c.update(true, 0, 500, 0);
		s.onKill(1);
		c.onKill(1);
		// load 30: inventory drops, cannon fills
		s.onSnapshot(Map.of(STEEL, 470));
		c.update(true, 30, 470, 0).forEach(s::addUsed);
		assertEquals(0.0, s.costTotal(), 1e-9);
		// fire 10
		s.onSnapshot(Map.of(STEEL, 470));
		c.update(true, 20, 470, 0).forEach(s::addUsed);
		assertEquals(10, s.usedTotal(STEEL));
		assertEquals(2000.0, s.costTotal(), 1e-9);
	}
}
