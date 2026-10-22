package com.slayercompanion.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.session.SupplyTracker;
import com.slayercompanion.session.TaskSession;
import java.util.Map;
import net.runelite.api.ItemID;
import org.junit.Test;

public class OnTaskModelTest
{
	private final Samples.MutableClock clock = new Samples.MutableClock();
	private final TaskSession session = new TaskSession(clock);

	private OnTaskModel build(int food)
	{
		return build("Fire giants", session, food);
	}

	private static OnTaskModel build(String taskName, TaskSession s, int food)
	{
		return OnTaskModel.build(taskName, "Waterfall Dungeon", s, Samples.fireGiant(), food, 48, 70,
			1_000_000, 49, 412, "chaeldar", 49);
	}

	private static SlayerTask named(String name)
	{
		SlayerTask t = new SlayerTask();
		t.setId(name.toLowerCase().replace(' ', '-'));
		t.setName(name);
		return t;
	}

	private String stat(OnTaskModel m, String label)
	{
		return m.getStats().stream().filter(s -> s.getLabel().equals(label)).findFirst().get().getValue();
	}

	@Test
	public void noTask()
	{
		OnTaskModel m = OnTaskModel.noTask();
		assertTrue(m.isNoTask());
		assertEquals("No slayer task", m.getMessage());
	}

	@Test
	public void timingHiddenBeforeTwoKills()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 142, 142));
		OnTaskModel zero = build(-1);
		assertTrue(zero.isTimingHidden());
		assertEquals("—", stat(zero, "Time left"));
		assertEquals("—", stat(zero, "Per kill"));
		assertEquals("—", stat(zero, "Kills / hr"));
		assertEquals("—", stat(zero, "Slayer XP / hr"));
		assertEquals("Tracking starts at first kill", zero.getNote());

		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 141, 142));
		clock.advance(32);
		assertTrue(build(-1).isTimingHidden());

		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 140, 142));
		OnTaskModel two = build(-1);
		assertTrue(!two.isTimingHidden());
		assertEquals("32", stat(two, "Per kill"));
		assertEquals("~75", stat(two, "Time left"));
		assertEquals("113", stat(two, "Kills / hr"));
		assertEquals("12.5k", stat(two, "Slayer XP / hr"));
	}

	@Test
	public void headerAndProgress()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 142, 142));
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 87, 142));
		OnTaskModel m = build(-1);
		assertEquals("Fire giants · Waterfall Dungeon", m.getHeader());
		assertEquals(87, m.getRemaining());
		assertEquals(142, m.getTotal());
		assertEquals(55.0 / 142, m.getProgress(), 1e-9);
		assertEquals("Prayer", m.getPrayerLabel());
		assertEquals("48 prayer points", m.getPrayer());
	}

	@Test
	public void foodWarningOnlyWhenFoodRunsOutBeforeTaskEnds()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 100, 100));
		session.onSupplies(Map.of(ItemID.SHARK, 20));
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 99, 100));
		session.onSupplies(Map.of(ItemID.SHARK, 18));
		// 2 food for 1 kill, 18 left -> 9 kills, 99 remaining
		assertEquals("Food runs out in ~9 kills", build(18).getFoodWarning());
		assertEquals("18 left · lasts ~9 kills", build(18).getFoodLine());
		assertNull(build(-1).getFoodLine());

		TaskSession plenty = new TaskSession(clock);
		plenty.onTaskUpdate(Samples.task(Samples.fireGiants(), 3, 4));
		plenty.onSupplies(Map.of(ItemID.SHARK, 20));
		plenty.onTaskUpdate(Samples.task(Samples.fireGiants(), 2, 4));
		plenty.onSupplies(Map.of(ItemID.SHARK, 19));
		// 1 food per kill, 19 left, 2 remaining -> no warning
		assertNull(build("Fire giants", plenty, 19).getFoodWarning());
		assertEquals("19 left · lasts ~19 kills", build("Fire giants", plenty, 19).getFoodLine());
	}

	@Test
	public void worthKnowing()
	{
		session.setMonster(Samples.fireGiant());
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 142, 142));
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 141, 142));
		session.onSupplies(Map.of());
		session.onLoot(1333, "Rune scimitar", 1, 15_000);
		session.onSupplies(Map.of(1333, 1));
		session.onSuperior();
		OnTaskModel m = build(-1);
		assertEquals("1", m.getSuperiors());
		assertEquals("Rune scimitar · 15k 1/128", m.getBestDrop());
		// 1,000,000 xp + 141 * 111 = 1,015,651 -> level 73 (1,000,000 is 72, 73 at 1,096,278)
		assertTrue(m.getLevelLabel().startsWith("Toward lvl "));
		assertTrue(m.getLevelValue().endsWith("%"));
		assertEquals("50th · bonus points", m.getNextTask());
		assertEquals("15k", stat(m, "Loot so far"));
		assertEquals("15k", stat(m, "GP per kill"));
	}

	@Test
	public void nextTaskWithoutBonus()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 142, 142));
		OnTaskModel m = OnTaskModel.build("Fire giants", null, session, Samples.fireGiant(), -1, 48, 70, 0, 40, -1,
			null, 40);
		assertEquals("41st · bonus on 50th", m.getNextTask());
		assertEquals("Fire giants", m.getHeader());
	}

	@Test
	public void statsGridHasSuppliesAndProfitInEvenCount()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 100, 100));
		OnTaskModel m = build(-1);
		assertEquals(8, m.getStats().size());
		assertEquals("—", stat(m, "Supplies / kill"));
		assertEquals("—", stat(m, "Profit / kill"));
	}

	@Test
	public void profitPerKillCanBeNegative()
	{
		TaskSession s = new TaskSession(clock, new SupplyTracker(id -> id == ItemID.SHARK ? 1000 : 0, id -> null));
		s.onTaskUpdate(Samples.task(Samples.fireGiants(), 100, 100));
		s.onSupplies(Map.of(ItemID.SHARK, 20));
		s.onTaskUpdate(Samples.task(Samples.fireGiants(), 99, 100));
		s.onSupplies(Map.of(ItemID.SHARK, 18));
		s.onLoot(995, "Coins", 1500, 1);
		s.onSupplies(Map.of(ItemID.SHARK, 18), Map.of(ItemID.SHARK, 18, 995, 1500));
		// 2 sharks (2,000 gp) and 1,500 gp loot over 1 kill
		OnTaskModel m = build("Fire giants", s, 18);
		assertEquals("2,000", stat(m, "Supplies / kill"));
		assertEquals("-500", stat(m, "Profit / kill"));

		s.onLoot(995, "Coins", 5000, 1);
		s.onSupplies(Map.of(ItemID.SHARK, 18), Map.of(ItemID.SHARK, 18, 995, 6500));
		assertEquals("4,500", stat(build("Fire giants", s, 18), "Profit / kill"));
	}

	@Test
	public void prayerShowsPointsUntilDrainIsSeen()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 100, 100));
		session.onPrayerPoints(70);
		OnTaskModel idle = build(-1);
		assertEquals("48 prayer points", idle.getPrayer());
		assertNull(idle.getPrayerWarning());

		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 99, 100));
		session.onPrayerPoints(60);
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 98, 100));
		session.onPrayerPoints(50);
		// 20 drained over the 1 measured kill; 48 points -> 2 kills, 98 left
		OnTaskModel m = build(-1);
		assertEquals("Lasts ~2 kills", m.getPrayer());
		assertEquals("Prayer runs out ~96 kills before task end", m.getPrayerWarning());
	}

	@Test
	public void finishingItemOnlyForTasksThatNeedOne()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 40, 100));
		OnTaskModel giants = build(-1);
		assertNull(giants.getFinishingLabel());
		assertNull(giants.getFinishingLine());
		assertNull(giants.getFinishingWarning());

		TaskSession zygs = new TaskSession(clock);
		zygs.onTaskUpdate(Samples.task(named("Mutated zygomites"), 40, 100));
		OnTaskModel none = build("Mutated zygomites", zygs, -1);
		assertEquals("Fungicide charges", none.getFinishingLabel());
		assertEquals("None · 40 left", none.getFinishingLine());
		assertEquals("No fungicide charges carried · needed to finish kills", none.getFinishingWarning());

		zygs.onSupplies(Map.of(ItemID.FUNGICIDE_SPRAY_10, 1, ItemID.FUNGICIDE, 1));
		OnTaskModel some = build("Mutated zygomites", zygs, -1);
		assertEquals("20 · 40 left", some.getFinishingLine());
		assertEquals("Fungicide charges run out ~20 kills before task end", some.getFinishingWarning());

		TaskSession lizards = new TaskSession(clock);
		lizards.onTaskUpdate(Samples.task(named("Lizards"), 40, 100));
		lizards.onSupplies(Map.of(ItemID.ICE_COOLER, 50));
		OnTaskModel plenty = build("Lizards", lizards, -1);
		assertEquals("Ice coolers", plenty.getFinishingLabel());
		assertEquals("50 · 40 left", plenty.getFinishingLine());
		assertNull(plenty.getFinishingWarning());
	}

	@Test
	public void cannonballsOnlyWhileCannonPlaced()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 100, 100));
		session.onSupplies(Map.of(ItemID.STEEL_CANNONBALL, 100));
		session.onCannon(false, 0);
		assertNull(build(-1).getCannonLine());
		assertNull(build(-1).getCannonWarning());

		session.onCannon(true, 30);
		assertEquals("130 balls", build(-1).getCannonLine());

		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 99, 100));
		session.onCannon(true, 20);
		// 10 balls for 1 kill, 120 left -> 12 kills, 99 left on task
		OnTaskModel m = build(-1);
		assertEquals("~12 kills · 120 balls", m.getCannonLine());
		assertEquals("Cannonballs run out ~87 kills before task end", m.getCannonWarning());

		session.onCannon(false, 0);
		assertNull(build(-1).getCannonLine());
		assertNull(build(-1).getCannonWarning());
	}

	@Test
	public void firedCannonballsCountAsSupplies()
	{
		TaskSession s = new TaskSession(clock,
			new SupplyTracker(id -> id == ItemID.STEEL_CANNONBALL ? 200 : 0, id -> null));
		s.onTaskUpdate(Samples.task(Samples.fireGiants(), 100, 100));
		s.onSupplies(Map.of(ItemID.STEEL_CANNONBALL, 100));
		s.onCannon(true, 0);
		s.onTaskUpdate(Samples.task(Samples.fireGiants(), 99, 100));
		// Loading 30 balls is not usage.
		s.onSupplies(Map.of(ItemID.STEEL_CANNONBALL, 70));
		s.onCannon(true, 30);
		assertEquals("0", stat(build("Fire giants", s, -1), "Supplies / kill"));
		s.onCannon(true, 25);
		assertEquals("1,000", stat(build("Fire giants", s, -1), "Supplies / kill"));
	}

	@Test
	public void slayerPoints()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 100, 100));
		// Chaeldar, 50th task: 10 x 15 milestone
		assertEquals("412 → 562", build(-1).getSlayerPoints());
		OnTaskModel unknownMaster = OnTaskModel.build("Fire giants", null, session, Samples.fireGiant(), -1, 48, 70,
			0, 48, 412, null, 48);
		assertEquals("412", unknownMaster.getSlayerPoints());
		OnTaskModel unknownPoints = OnTaskModel.build("Fire giants", null, session, Samples.fireGiant(), -1, 48, 70,
			0, 48, -1, "chaeldar", 48);
		assertNull(unknownPoints.getSlayerPoints());
		assertNotNull(build(-1).getNextTask());
	}

	private static OnTaskModel.Supply supply(OnTaskModel m, String label)
	{
		return m.getSupplies().stream().filter(x -> x.getLabel().equals(label)).findFirst().orElse(null);
	}

	@Test
	public void heroFieldsAndProgressLine()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 142, 142));
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 141, 142));
		OnTaskModel early = build(-1);
		assertEquals("Fire giants", early.getTaskName());
		assertEquals("Waterfall Dungeon", early.getLocationName());
		assertEquals("1 of 142 done", early.getProgressLine());

		clock.advance(32);
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 140, 142));
		// 140 left at 32 s each -> ~75 min
		assertEquals("2 of 142 done · ~75 min left", build(-1).getProgressLine());
		assertEquals("s", build(-1).getStats().stream().filter(x -> x.getLabel().equals("Per kill")).findFirst()
			.get().getUnit());
	}

	@Test
	public void supplyRowsCompareRunwayWithKillsLeft()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 100, 100));
		session.onSupplies(Map.of(ItemID.LOBSTER, 30, ItemID.SHARK, 10));
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 99, 100));
		session.onSupplies(Map.of(ItemID.LOBSTER, 28, ItemID.SHARK, 10));
		// 2 food a kill, 38 carried -> 19 kills against 99 left: short
		OnTaskModel m = build(38);
		OnTaskModel.Supply food = supply(m, "Food");
		assertEquals(ItemID.LOBSTER, food.getIconItemId());
		assertEquals(19, food.getKillsItLasts());
		assertEquals(19 / 99.0, food.getFraction(), 1e-9);
		assertEquals(-1, food.getTaskEndAt(), 1e-9);
		assertTrue(food.isRunsShort());
		assertEquals("Food runs out in ~19 kills", food.getWarning());
		assertEquals("38 left · lasts ~19 kills", food.getValue());

		OnTaskModel.Supply prayer = supply(m, "Prayer");
		assertEquals("prayer", prayer.getSkillIcon());
		assertEquals(-1, prayer.getKillsItLasts());
		assertEquals(-1, prayer.getFraction(), 1e-9);
		assertEquals("48 prayer points", prayer.getValue());
		assertNull(supply(m, "Cannonballs"));
	}

	@Test
	public void supplyThatOutlastsTheTaskMarksTheTaskEnd()
	{
		OnTaskModel.Supply s = OnTaskModel.Supply.of("Food", ItemID.SHARK, null, "x", null,
			java.util.OptionalInt.of(120), 87);
		assertEquals(1, s.getFraction(), 1e-9);
		assertEquals(87 / 120.0, s.getTaskEndAt(), 1e-9);
		assertTrue(!s.isRunsShort());
		OnTaskModel.Supply exact = OnTaskModel.Supply.of("Food", ItemID.SHARK, null, "x", null,
			java.util.OptionalInt.of(87), 87);
		assertEquals(1, exact.getFraction(), 1e-9);
		assertEquals(-1, exact.getTaskEndAt(), 1e-9);
	}

	@Test
	public void foodIconFallsBackToShark()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 100, 100));
		session.onSupplies(Map.of(ItemID.STEEL_CANNONBALL, 10));
		assertEquals(ItemID.SHARK, supply(build(0), "Food").getIconItemId());
	}

	@Test
	public void finishingAndCannonRowsCarryIcons()
	{
		TaskSession zygs = new TaskSession(clock);
		zygs.onTaskUpdate(Samples.task(named("Mutated zygomites"), 40, 100));
		zygs.onSupplies(Map.of(ItemID.FUNGICIDE_SPRAY_10, 1, ItemID.STEEL_CANNONBALL, 100));
		zygs.onCannon(true, 30);
		OnTaskModel m = build("Mutated zygomites", zygs, -1);
		OnTaskModel.Supply f = supply(m, "Fungicide charges");
		assertEquals(ItemID.FUNGICIDE_SPRAY_10, f.getIconItemId());
		assertEquals(10, f.getKillsItLasts());
		assertEquals(0.25, f.getFraction(), 1e-9);
		assertTrue(f.isRunsShort());
		OnTaskModel.Supply c = supply(m, "Cannonballs");
		assertEquals(ItemID.STEEL_CANNONBALL, c.getIconItemId());
		assertEquals(-1, c.getFraction(), 1e-9);
		assertEquals("130 balls", c.getValue());
	}

	@Test
	public void bestDropIconLevelFractionAndProfitSign()
	{
		TaskSession s = new TaskSession(clock, new SupplyTracker(id -> id == ItemID.SHARK ? 1000 : 0, id -> null));
		s.setMonster(Samples.fireGiant());
		s.onTaskUpdate(Samples.task(Samples.fireGiants(), 100, 100));
		s.onSupplies(Map.of(ItemID.SHARK, 20));
		s.onTaskUpdate(Samples.task(Samples.fireGiants(), 99, 100));
		s.onSupplies(Map.of(ItemID.SHARK, 18));
		s.onLoot(1333, "Rune scimitar", 1, 1_500);
		s.onSupplies(Map.of(ItemID.SHARK, 18), Map.of(ItemID.SHARK, 18, 1333, 1));
		OnTaskModel m = build("Fire giants", s, 18);
		assertEquals(1333, m.getBestDropItemId());
		assertTrue(m.isProfitNegative());
		assertTrue(m.getLevelFraction() >= 0 && m.getLevelFraction() <= 1);
		assertEquals(Math.round(m.getLevelFraction() * 100) + "%", m.getLevelValue());

		s.onLoot(995, "Coins", 5000, 1);
		s.onSupplies(Map.of(ItemID.SHARK, 18), Map.of(ItemID.SHARK, 18, 1333, 1, 995, 5000));
		assertTrue(!build("Fire giants", s, 18).isProfitNegative());
		assertEquals(-1, OnTaskModel.noTask().getBestDropItemId());
		assertTrue(OnTaskModel.noTask().getSupplies().isEmpty());
	}
}
