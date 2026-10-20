package com.slayercompanion.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.session.TaskSession;
import org.junit.Test;

public class OnTaskModelTest
{
	private final Samples.MutableClock clock = new Samples.MutableClock();
	private final TaskSession session = new TaskSession(clock);

	private OnTaskModel build(int food)
	{
		return OnTaskModel.build("Fire giants", "Waterfall Dungeon", session, Samples.fireGiant(), food, 48, 70,
			1_000_000, 49);
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
		assertEquals("Starts counting when you get your first kill", zero.getNote());

		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 141, 142));
		clock.advance(32);
		assertTrue(build(-1).isTimingHidden());

		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 140, 142));
		OnTaskModel two = build(-1);
		assertTrue(!two.isTimingHidden());
		assertEquals("16", stat(two, "Per kill"));
		assertEquals("~37", stat(two, "Time left"));
		assertEquals("225", stat(two, "Kills / hr"));
		assertEquals("25k", stat(two, "Slayer XP / hr"));
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
		assertEquals("48 / 70", m.getPrayer());
	}

	@Test
	public void foodWarningOnlyWhenFoodRunsOutBeforeTaskEnds()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 100, 100));
		session.onFoodCount(20);
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 99, 100));
		session.onFoodCount(18);
		// 2 food for 1 kill, 18 left -> 9 kills, 99 remaining
		assertEquals("Food runs out in ~9 kills", build(18).getFoodWarning());
		assertEquals("18 left", build(18).getFoodLine());

		TaskSession plenty = new TaskSession(clock);
		plenty.onTaskUpdate(Samples.task(Samples.fireGiants(), 3, 4));
		plenty.onFoodCount(20);
		plenty.onTaskUpdate(Samples.task(Samples.fireGiants(), 2, 4));
		plenty.onFoodCount(19);
		// 1 food per kill, 19 left, 2 remaining -> no warning
		assertNull(OnTaskModel.build("Fire giants", "Waterfall Dungeon", plenty, Samples.fireGiant(), 19, 48, 70,
			1_000_000, 49).getFoodWarning());
	}

	@Test
	public void worthKnowing()
	{
		session.setMonster(Samples.fireGiant());
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 142, 142));
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 141, 142));
		session.onLoot(1333, 1, 15_000);
		session.onSuperior();
		OnTaskModel m = build(-1);
		assertEquals("1", m.getSuperiors());
		assertEquals("Rune scimitar · 15k 1/128", m.getBestDrop());
		// 1,000,000 xp + 141 * 111 = 1,015,651 -> level 73 (1,000,000 is 72, 73 at 1,096,278)
		assertTrue(m.getLevelLabel().startsWith("Toward lvl "));
		assertTrue(m.getLevelValue().endsWith("%"));
		assertEquals("50th: bonus points", m.getNextTask());
		assertEquals("15k", stat(m, "Loot so far"));
		assertEquals("15k", stat(m, "GP per kill"));
	}

	@Test
	public void nextTaskWithoutBonus()
	{
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 142, 142));
		OnTaskModel m = OnTaskModel.build("Fire giants", null, session, Samples.fireGiant(), -1, 48, 70, 0, 40);
		assertEquals("41st: no bonus", m.getNextTask());
		assertEquals("Fire giants", m.getHeader());
	}
}
