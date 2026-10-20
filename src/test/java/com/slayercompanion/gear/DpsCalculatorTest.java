package com.slayercompanion.gear;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.Monster;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DpsCalculatorTest
{
	private static Equipment item(String name, String slot, int speed, Object... kv)
	{
		Equipment e = new Equipment();
		e.setName(name);
		e.setSlot(slot);
		e.setSpeedTicks(speed);
		for (int i = 0; i < kv.length; i += 2)
		{
			e.getBonuses().put((String) kv[i], (Integer) kv[i + 1]);
		}
		return e;
	}

	private static Loadout melee(Equipment... items)
	{
		Loadout l = new Loadout();
		l.setStyle(Style.MELEE);
		l.setMeleeType("slash");
		for (Equipment e : items)
		{
			l.getBySlot().put(e.getSlot(), e);
		}
		return l;
	}

	private static final CombatStats MAXED = new CombatStats(99, 99, 99, 99, 99, 99, 99, 99);

	private static Equipment whip()
	{
		return item("Abyssal whip", "weapon", 4, "slash", 82, "str", 82);
	}

	private static Monster monster()
	{
		Monster m = new Monster();
		m.setDefenceLevel(100);
		m.setMagicLevel(100);
		return m;
	}

	@Test
	public void whipMaxHit()
	{
		assertEquals(25, DpsCalculator.maxHit(MAXED, melee(whip()), monster(), false));
	}

	@Test
	public void slayerHelmMelee()
	{
		Loadout l = melee(whip(), item("Slayer helmet", "head", 0));
		assertEquals(29, DpsCalculator.maxHit(MAXED, l, monster(), true));
	}

	@Test
	public void slayerHelmIgnoredOffTask()
	{
		Loadout l = melee(whip(), item("Slayer helmet", "head", 0));
		assertEquals(25, DpsCalculator.maxHit(MAXED, l, monster(), false));
	}

	@Test
	public void hitChanceAttackerHigher()
	{
		assertEquals(0.74996, DpsCalculator.hitChance(20000, 10000), 1e-4);
	}

	@Test
	public void hitChanceDefenderHigher()
	{
		assertEquals(0.24999, DpsCalculator.hitChance(10000, 20000), 1e-4);
	}

	private static Loadout waterWave()
	{
		Loadout l = new Loadout();
		l.setStyle(Style.MAGIC);
		l.setSpell("Water Wave");
		Equipment staff = item("Staff of water", "weapon", 4, "magic", 10);
		l.getBySlot().put("weapon", staff);
		return l;
	}

	@Test
	public void waterWaveVsFireGiantGetsWeaknessBonus()
	{
		Monster m = monster();
		m.setElementalWeakness("water");
		m.setElementalWeaknessPercent(50);
		double with = DpsCalculator.dps(MAXED, waterWave(), m, false);
		m.setElementalWeakness(null);
		double without = DpsCalculator.dps(MAXED, waterWave(), m, false);
		assertTrue(with > without);
		assertEquals(18, DpsCalculator.maxHit(MAXED, waterWave(), m, false));
	}

	@Test
	public void dpsFormula()
	{
		Loadout l = melee(whip());
		Monster m = monster();
		double expected = DpsCalculator.hitChance(MAXED, l, m, false)
			* DpsCalculator.maxHit(MAXED, l, m, false) / 2.0 / (4 * 0.6);
		assertEquals(expected, DpsCalculator.dps(MAXED, l, m, false), 1e-9);
	}

	@Test
	public void salveBeatsSlayerHelmVsUndead()
	{
		Monster m = monster();
		m.setUndead(true);
		Loadout l = melee(whip(), item("Slayer helmet", "head", 0), item("Salve amulet (e)", "neck", 0));
		assertEquals(30, DpsCalculator.maxHit(MAXED, l, m, true));
	}
}
