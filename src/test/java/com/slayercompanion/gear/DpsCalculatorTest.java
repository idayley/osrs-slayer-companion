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
		Loadout l = waterWave();
		l.getBySlot().get("weapon").getBonuses().put("magic_str", 100); // +10%
		Monster m = monster();
		m.setElementalWeakness("water");
		m.setElementalWeaknessPercent(50);
		// base 18 -> floor(18*1.1)=19, plus trunc(18*50/100)=9 -> 28
		assertEquals(28, DpsCalculator.maxHit(MAXED, l, m, false));
		double chanceWith = DpsCalculator.hitChance(MAXED, l, m, false);
		double dpsWith = DpsCalculator.dps(MAXED, l, m, false);
		// attack roll 108*(10+64)=7992, +trunc(7992*50/100)=3996 -> 11988
		assertEquals(11988L, DpsCalculator.attackRoll(MAXED, l, m, false));
		m.setElementalWeakness(null);
		assertEquals(19, DpsCalculator.maxHit(MAXED, l, m, false));
		assertEquals(7992L, DpsCalculator.attackRoll(MAXED, l, m, false));
		assertTrue(chanceWith > DpsCalculator.hitChance(MAXED, l, m, false));
		assertTrue(dpsWith > DpsCalculator.dps(MAXED, l, m, false));
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
