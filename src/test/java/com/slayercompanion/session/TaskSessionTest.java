package com.slayercompanion.session;

import com.slayercompanion.data.Drop;
import com.slayercompanion.data.Monster;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.task.CurrentTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import net.runelite.api.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskSessionTest
{
	private static class MutableClock extends Clock
	{
		Instant now = Instant.parse("2026-01-01T00:00:00Z");

		void advance(long seconds)
		{
			now = now.plus(Duration.ofSeconds(seconds));
		}

		@Override
		public ZoneId getZone()
		{
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone)
		{
			return this;
		}

		@Override
		public Instant instant()
		{
			return now;
		}
	}

	private final MutableClock clock = new MutableClock();
	private final TaskSession session = new TaskSession(clock);

	private static CurrentTask task(String name, int remaining, int initial)
	{
		SlayerTask t = new SlayerTask();
		t.setId(name.toLowerCase());
		t.setName(name);
		return new CurrentTask(t, remaining, initial, Optional.empty(), Optional.empty(), 0);
	}

	@Test
	public void killsComeFromGameCounter()
	{
		session.onTaskUpdate(task("Abyssal demons", 142, 142));
		session.onTaskUpdate(task("Abyssal demons", 139, 142));
		assertEquals(139, session.remaining());
		assertEquals(142, session.total());
		assertEquals(3, session.kills());
	}

	@Test
	public void secondsPerKillAndTimeLeft()
	{
		session.onTaskUpdate(task("Abyssal demons", 142, 142));
		session.onTaskUpdate(task("Abyssal demons", 141, 142)); // kill 1 starts the clock
		clock.advance(96);
		session.onTaskUpdate(task("Abyssal demons", 138, 142)); // 4 kills over 96 s -> 3 timed kills
		assertEquals(32.0, session.secondsPerKill().getAsDouble(), 1e-9);
		assertEquals(32.0 * 138, session.secondsLeft().getAsDouble(), 1e-9);
		assertEquals(112.5, session.killsPerHour().getAsDouble(), 1e-9);
	}

	@Test
	public void timingHiddenAfterOnlyOneKill()
	{
		session.onTaskUpdate(task("Abyssal demons", 142, 142));
		session.onTaskUpdate(task("Abyssal demons", 141, 142));
		clock.advance(40);
		assertFalse(session.secondsPerKill().isPresent());
		assertFalse(session.killsPerHour().isPresent());
		assertFalse(session.secondsLeft().isPresent());
	}

	@Test
	public void pausesWhileLoggedOut()
	{
		session.onTaskUpdate(task("Abyssal demons", 100, 100));
		session.onTaskUpdate(task("Abyssal demons", 99, 100));
		clock.advance(30);
		session.onLoggedOut();
		clock.advance(10_000);
		session.onLoggedIn();
		clock.advance(30);
		session.onTaskUpdate(task("Abyssal demons", 97, 100));
		// 3 kills, the 2 after the first took 60 active seconds
		assertEquals(30.0, session.secondsPerKill().getAsDouble(), 1e-9);
	}

	@Test
	public void newTaskResets()
	{
		session.onTaskUpdate(task("Abyssal demons", 100, 100));
		session.onTaskUpdate(task("Abyssal demons", 99, 100));
		clock.advance(30);
		session.onTaskUpdate(task("Abyssal demons", 98, 100));
		session.onLoot(4151, "Abyssal whip", 1, 2_000_000L);
		session.onSuperior();
		session.onTaskUpdate(task("Gargoyles", 120, 120));
		assertEquals(120, session.remaining());
		assertEquals(0, session.kills());
		assertEquals(0, session.superiors());
		assertEquals(0L, session.lootTotal());
		assertFalse(session.secondsPerKill().isPresent());
		assertFalse(session.bestDrop().isPresent());
	}

	@Test
	public void bestDropKeepsHighestValueWithRate()
	{
		Monster m = new Monster();
		Drop scim = new Drop();
		scim.setItemId(1333);
		scim.setName("Rune scimitar");
		scim.setRate(1.0 / 128);
		m.getDrops().add(scim);
		session.setMonster(m);
		session.onTaskUpdate(task("Abyssal demons", 10, 10));
		session.onSupplies(Map.of());
		session.onLoot(995, "Coins", 500, 1);
		session.onLoot(1333, "Rune scim", 1, 15000);
		session.onLoot(560, "Death rune", 10, 100);
		session.onLoot(532, "Bones", 1, 100); // left on the floor
		session.onSupplies(Map.of(995, 500, 1333, 1, 560, 10));
		TaskSession.BestDrop best = session.bestDrop().get();
		assertEquals("Rune scimitar", best.getName());
		assertEquals(15000L, best.getValue());
		assertEquals(1.0 / 128, best.getRate(), 1e-12);
		assertEquals(500 + 15000 + 1000, session.lootTotal());
	}

	@Test
	public void lootTakenByAnOpenBagCounts()
	{
		session.onTaskUpdate(task("Abyssal demons", 10, 10));
		session.onSupplies(Map.of());
		session.onLoot(207, "Grimy ranarr weed", 1, 7000);
		session.onLoot(1619, "Uncut ruby", 1, 1000);
		session.onBagMessage("You put the Grimy ranarr weed herb into your herb sack.");
		session.onGroundTake(1619);
		session.onBagMessage("You put it straight into your open gem bag.");
		assertEquals(8000L, session.lootTotal());
	}

	@Test
	public void bestDropOffTableUsesItemNameWithNoRate()
	{
		session.setMonster(new Monster());
		session.onTaskUpdate(task("Abyssal demons", 10, 10));
		session.onLoot(12345, "Clue scroll (hard)", 1, 5000);
		TaskSession.BestDrop best = session.bestDrop().get();
		assertEquals("Clue scroll (hard)", best.getName());
		assertEquals(0.0, best.getRate(), 0.0);
		session.onLoot(23456, null, 1, 9000);
		assertEquals("Item 23456", session.bestDrop().get().getName());
	}

	private static Map<Integer, Integer> food(int sharks)
	{
		return Map.of(ItemID.SHARK, sharks);
	}

	@Test
	public void foodRunway()
	{
		session.onTaskUpdate(task("Abyssal demons", 100, 100));
		session.onSupplies(food(6));
		session.onTaskUpdate(task("Abyssal demons", 99, 100));
		session.onTaskUpdate(task("Abyssal demons", 75, 100)); // 25 kills
		session.onSupplies(food(10)); // withdrawing, ignored
		session.onSupplies(food(6)); // 4 eaten / 25 kills = 0.16
		assertEquals(37, session.killsUntilFoodRunsOut().getAsInt());
	}

	@Test
	public void bankingDoesNotCountAsEating()
	{
		session.onTaskUpdate(task("Abyssal demons", 100, 100));
		session.onSupplies(food(10));
		session.onTaskUpdate(task("Abyssal demons", 90, 100)); // 10 kills
		session.onSupplies(food(8)); // 2 eaten
		session.setBankOpen(true);
		session.onSupplies(food(0)); // deposited
		session.onSupplies(food(20)); // withdrew
		session.onSupplies(food(18)); // put two back
		session.setBankOpen(false);
		session.onSupplies(food(17)); // 1 eaten
		// 3 eaten / 10 kills = 0.3 per kill; 17 left -> 56 kills
		assertEquals(56, session.killsUntilFoodRunsOut().getAsInt());
	}

	@Test
	public void nextTaskBonusAt49()
	{
		assertTrue(session.nextTaskIsBonus(49));
		assertFalse(session.nextTaskIsBonus(50));
	}

	@Test
	public void levelProgress()
	{
		// level 50 = 101333 xp, level 51 = 111945 xp
		assertEquals(0.0, session.levelProgressAtTaskEnd(101_333, 0), 1e-9);
		session.onTaskUpdate(task("Abyssal demons", 10, 10));
		double expected = (101_333 + 10 * 100 - 101_333) / (double) (111_945 - 101_333);
		assertEquals(expected, session.levelProgressAtTaskEnd(101_333, 100), 1e-9);
	}

	@Test
	public void noStatsBeforeFirstKill()
	{
		session.onTaskUpdate(task("Abyssal demons", 142, 142));
		assertFalse(session.secondsPerKill().isPresent());
		assertFalse(session.killsPerHour().isPresent());
		assertFalse(session.secondsLeft().isPresent());
		assertFalse(session.gpPerKill().isPresent());
		assertFalse(session.killsUntilFoodRunsOut().isPresent());
		assertFalse(session.bestDrop().isPresent());
		assertEquals(0.0, session.slayerXpPerHour(100), 0.0);
	}

	@Test
	public void weaponChargesAreCostedAsSupplies()
	{
		Map<Integer, Long> prices = Map.of(ItemID.DEATH_RUNE, 200L, ItemID.CHAOS_RUNE, 80L, ItemID.FIRE_RUNE, 5L,
			ItemID.COINS_995, 1L, ItemID.ZULRAHS_SCALES, 150L);
		TaskSession s = new TaskSession(clock, new SupplyTracker(id -> prices.getOrDefault(id, 0L), id -> null));
		s.onSupplies(Map.of(ItemID.TRIDENT_OF_THE_SEAS, 1, ItemID.ZULRAHS_SCALES, 1000));
		s.onTaskUpdate(task("Abyssal demons", 100, 100));
		s.onWeaponTick(ItemID.TRIDENT_OF_THE_SEAS, -1, WeaponCharges.POWERED_STAFF_ANIMATION, 0, true, -1, false);
		assertEquals("before the first kill", 0.0, s.supplies().costTotal(), 0.0);
		s.onTaskUpdate(task("Abyssal demons", 99, 100));
		s.onWeaponTick(ItemID.TRIDENT_OF_THE_SEAS, -1, WeaponCharges.POWERED_STAFF_ANIMATION, 0, true, -1, false);
		s.onWeaponTick(ItemID.TRIDENT_OF_THE_SEAS, -1, WeaponCharges.POWERED_STAFF_ANIMATION, 0, true, -1, false);
		// Two charges at 200 + 80 + 5 * 5 + 10 each.
		assertEquals(630.0, s.supplies().costTotal(), 1e-6);
		assertEquals(2, s.weaponCharges().attacks());
		// Scales put into a weapon are not used until it fires.
		s.onSupplies(Map.of(ItemID.TRIDENT_OF_THE_SEAS, 1, ItemID.ZULRAHS_SCALES, 0));
		assertEquals(630.0, s.supplies().costTotal(), 1e-6);
	}
}
