package com.slayercompanion.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.Monster;
import org.junit.Test;

/**
 * Assumed prayers and potions, checked against the OSRS Wiki DPS calculator (src/enums/Prayer.ts for the factors,
 * src/utils.ts for the potion formulas, PlayerVsNPCCalc.ts for the order: level + potion, prayer, stance, +8).
 */
public class BoostsTest
{
	private static CombatStats boosted(int prayer, int defence)
	{
		CombatStats s = new CombatStats(99, 99, defence, 99, 99, 99, prayer, 99);
		s.setAssumeBoosts(true);
		return s;
	}

	private static CombatStats maxedAllUnlocked()
	{
		CombatStats s = boosted(99, 99);
		s.setPietyUnlocked(true);
		s.setRigourUnlocked(true);
		s.setAuguryUnlocked(true);
		s.setDeadeyeUnlocked(true);
		s.setMysticVigourUnlocked(true);
		return s;
	}

	private static Equipment item(String name, String slot, Object... kv)
	{
		Equipment e = new Equipment();
		e.setName(name);
		e.setSlot(slot);
		e.setSpeedTicks(4);
		for (int i = 0; i < kv.length; i += 2)
		{
			e.getBonuses().put((String) kv[i], (Integer) kv[i + 1]);
		}
		return e;
	}

	private static Loadout loadout(Style style, Equipment... items)
	{
		Loadout l = new Loadout();
		l.setStyle(style);
		l.setMeleeType("slash");
		for (Equipment e : items)
		{
			l.getBySlot().put(e.getSlot(), e);
		}
		return l;
	}

	private static Monster monster()
	{
		Monster m = new Monster();
		m.setDefenceLevel(100);
		m.setMagicLevel(100);
		return m;
	}

	// ---- prayer choice ----

	@Test
	public void meleePrayerByLevelAndUnlocks()
	{
		assertEquals("Piety", Boosts.prayer(maxedAllUnlocked(), Style.MELEE).getName());
		CombatStats noWaves = boosted(99, 99);
		assertEquals("Ultimate Strength and Incredible Reflexes", Boosts.prayer(noWaves, Style.MELEE).getName());
		CombatStats def65 = boosted(99, 65);
		def65.setPietyUnlocked(true);
		assertEquals("Chivalry", Boosts.prayer(def65, Style.MELEE).getName());
		CombatStats prayer33 = boosted(33, 99);
		Boosts.Prayer p = Boosts.prayer(prayer33, Style.MELEE);
		assertEquals("Ultimate Strength and Improved Reflexes", p.getName());
		assertEquals(110, p.getAccuracy());
		assertEquals(115, p.getStrength());
		assertEquals("none", Boosts.prayer(boosted(3, 99), Style.MELEE).getName());
	}

	@Test
	public void rangedPrayerByLevelAndUnlocks()
	{
		assertEquals("Rigour", Boosts.prayer(maxedAllUnlocked(), Style.RANGED).getName());
		CombatStats deadeye = boosted(99, 99);
		deadeye.setDeadeyeUnlocked(true);
		assertEquals("Deadeye", Boosts.prayer(deadeye, Style.RANGED).getName());
		assertEquals("Eagle Eye", Boosts.prayer(boosted(99, 99), Style.RANGED).getName());
		CombatStats rigourLowDef = boosted(99, 60);
		rigourLowDef.setRigourUnlocked(true);
		assertEquals("Eagle Eye", Boosts.prayer(rigourLowDef, Style.RANGED).getName());
		assertEquals("Hawk Eye", Boosts.prayer(boosted(30, 99), Style.RANGED).getName());
	}

	@Test
	public void magicPrayerByLevelAndUnlocks()
	{
		assertEquals("Augury", Boosts.prayer(maxedAllUnlocked(), Style.MAGIC).getName());
		CombatStats vigour = boosted(70, 99);
		vigour.setMysticVigourUnlocked(true);
		vigour.setAuguryUnlocked(true); // 70 Prayer is below Augury's 77
		assertEquals("Mystic Vigour", Boosts.prayer(vigour, Style.MAGIC).getName());
		assertEquals("Mystic Might", Boosts.prayer(boosted(99, 99), Style.MAGIC).getName());
		assertEquals(20, Boosts.prayer(boosted(99, 99), Style.MAGIC).getMagicDamage());
	}

	@Test
	public void noBoostsWhenNotAssumed()
	{
		CombatStats s = new CombatStats(99, 99, 99, 99, 99, 99, 99, 99);
		assertEquals("none", Boosts.prayer(s, Style.MELEE).getName());
		assertEquals(99, Boosts.strength(s, Style.MELEE));
		assertNull(Boosts.note(s, Style.MELEE));
	}

	// ---- potions ----

	@Test
	public void potionLevels()
	{
		CombatStats s = boosted(99, 99);
		// Super combat: floor(5 + 0.15 * 99) = 19; ranging potion: floor(4 + 0.1 * 99) = 13; magic potion: +4.
		assertEquals(118, Boosts.attack(s, Style.MELEE));
		assertEquals(118, Boosts.strength(s, Style.MELEE));
		assertEquals(112, Boosts.ranged(s, Style.RANGED));
		assertEquals(103, Boosts.magic(s, Style.MAGIC));
		CombatStats low = new CombatStats(40, 40, 40, 40, 40, 40, 40, 40);
		low.setAssumeBoosts(true);
		assertEquals(51, Boosts.strength(low, Style.MELEE)); // 40 + floor(5 + 6.0)
		assertEquals(48, Boosts.ranged(low, Style.RANGED)); // 40 + floor(4 + 4.0)
		// Potions only for the style in use.
		assertEquals(99, Boosts.ranged(s, Style.MELEE));
	}

	@Test
	public void notes()
	{
		assertEquals("Assumes Piety and a super combat", Boosts.note(maxedAllUnlocked(), Style.MELEE));
		assertEquals("Assumes Eagle Eye and a ranging potion", Boosts.note(boosted(99, 99), Style.RANGED));
		assertEquals("Assumes Augury and a magic potion", Boosts.note(maxedAllUnlocked(), Style.MAGIC));
		assertEquals("Assumes a super combat", Boosts.note(boosted(1, 99), Style.MELEE));
	}

	// ---- maths ----

	/** Whip at 99s with Piety and a super combat: visible 118 -> 145 str (141 att) -> max 36, roll 149 * 146. */
	@Test
	public void pietySuperCombatWhip()
	{
		Loadout l = loadout(Style.MELEE, item("Abyssal whip", "weapon", "slash", 82, "str", 82));
		assertEquals(36, DpsCalculator.maxHit(maxedAllUnlocked(), l, monster(), false));
		assertEquals(21754L, DpsCalculator.attackRoll(maxedAllUnlocked(), l, monster(), false));
	}

	/** Rigour and a ranging potion: visible 112 -> 134 accuracy / 137 strength, + 3 accurate + 8. */
	@Test
	public void rigourRangingPotion()
	{
		Loadout l = loadout(Style.RANGED, item("Rune crossbow", "weapon", "ranged", 90),
			item("Runite bolts", "ammo", "ranged_str", 100));
		assertEquals(145L * 154, DpsCalculator.attackRoll(maxedAllUnlocked(), l, monster(), false));
		// floor((148 * 164 + 320) / 640) = 38
		assertEquals(38, DpsCalculator.maxHit(maxedAllUnlocked(), l, monster(), false));
	}

	/** Augury and a magic potion: 103 -> floor(103 * 1.25) = 128 + 9; +4% magic damage added to gear's. */
	@Test
	public void auguryMagicPotion()
	{
		Loadout l = loadout(Style.MAGIC, item("Staff of water", "weapon", "magic", 10, "magic_str", 100));
		l.setSpell("Water Wave");
		assertEquals(137L * 74, DpsCalculator.attackRoll(maxedAllUnlocked(), l, monster(), false));
		// 18 + floor(18 * 140 / 1000) = 20
		assertEquals(20, DpsCalculator.maxHit(maxedAllUnlocked(), l, monster(), false));
	}

	/** Powered staves read the boosted Magic level: trident of the seas at 103 visible = 103 / 3 - 5 = 29. */
	@Test
	public void tridentUsesBoostedMagic()
	{
		CombatStats s = boosted(1, 99);
		Equipment trident = item("Trident of the seas", "weapon", "magic", 25);
		trident.setCategory("Powered Staff");
		assertEquals(29, DpsCalculator.maxHit(s, loadout(Style.MAGIC, trident), monster(), false));
	}

	/** Burst of Strength below 21 strength adds one level instead of 5% (calculator special case). */
	@Test
	public void burstOfStrengthAddsOneLevelWhenLow()
	{
		CombatStats s = new CombatStats(10, 7, 10, 10, 10, 10, 7, 10);
		s.setAssumeBoosts(true);
		// Strength 7 + floor(5 + 1.05) = 13 -> Burst +1 = 14 -> + 11 = 25; max floor((25 * 64 + 320) / 640) = 3.
		// As a plain 5% it would be floor(13 * 1.05) = 13 -> 24 -> 2.
		Loadout l = loadout(Style.MELEE, item("Bronze sword", "weapon", "slash", 0, "str", 0));
		assertEquals(3, DpsCalculator.maxHit(s, l, monster(), false));
		assertEquals("Burst of Strength and Clarity of Thought", Boosts.prayer(s, Style.MELEE).getName());
	}
}
