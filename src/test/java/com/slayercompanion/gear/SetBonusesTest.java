package com.slayercompanion.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.Monster;
import org.junit.Test;

/**
 * Armour set bonuses, checked against the OSRS Wiki DPS calculator (PlayerVsNPCCalc.ts / BaseCalc.ts).
 * Maxed stats without boosts: melee effective strength 110 / attack 107, ranged 110, magic 108.
 */
public class SetBonusesTest
{
	private static final CombatStats MAXED = new CombatStats(99, 99, 99, 99, 99, 99, 99, 99);

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

	private static Loadout loadout(Style style, String meleeType, Equipment... items)
	{
		Loadout l = new Loadout();
		l.setStyle(style);
		l.setMeleeType(meleeType);
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

	private static Equipment whip()
	{
		return item("Abyssal whip", "weapon", "slash", 82, "str", 82);
	}

	private static Equipment[] voidRobes(boolean elite)
	{
		String top = elite ? "Elite void top (Normal)" : "Void knight top (Normal)";
		String robe = elite ? "Elite void robe (Normal)" : "Void knight robe (or) (Normal)";
		return new Equipment[]{item(top, "body"), item(robe, "legs"), item("Void knight gloves (Normal)", "hands")};
	}

	private static Loadout withAll(Loadout l, Equipment... items)
	{
		for (Equipment e : items)
		{
			l.getBySlot().put(e.getSlot(), e);
		}
		return l;
	}

	// ---- void ----

	/** Melee void: effective levels x 11/10 after stance: 110 -> 121 (max 28), 107 -> 117 (roll 117 * 146). */
	@Test
	public void meleeVoid()
	{
		Loadout l = withAll(loadout(Style.MELEE, "slash", whip(), item("Void melee helm (Normal)", "head")), voidRobes(false));
		assertEquals(28, DpsCalculator.maxHit(MAXED, l, monster(), false));
		assertEquals(117L * 146, DpsCalculator.attackRoll(MAXED, l, monster(), false));
	}

	@Test
	public void voidNeedsEveryPiece()
	{
		Loadout l = loadout(Style.MELEE, "slash", whip(), item("Void melee helm (Normal)", "head"),
			voidRobes(false)[0], voidRobes(false)[1]);
		assertEquals(25, DpsCalculator.maxHit(MAXED, l, monster(), false));
		// Broken pieces cannot be worn, so they never complete a set.
		Loadout broken = withAll(loadout(Style.MELEE, "slash", whip(), item("Void melee helm (Broken)", "head")),
			voidRobes(false));
		assertEquals(25, DpsCalculator.maxHit(MAXED, broken, monster(), false));
	}

	/** Ranged void: accuracy 110 -> 121; damage x 11/10 (121), elite x 9/8 (123). */
	@Test
	public void rangedVoidAndElite()
	{
		Equipment rcb = item("Rune crossbow", "weapon", "ranged", 90);
		Equipment bolts = item("Runite bolts", "ammo", "ranged_str", 100);
		Equipment helm = item("Void ranger helm (Normal)", "head");
		Loadout normal = withAll(loadout(Style.RANGED, "slash", rcb, bolts, helm), voidRobes(false));
		assertEquals(121L * 154, DpsCalculator.attackRoll(MAXED, normal, monster(), false));
		assertEquals(31, DpsCalculator.maxHit(MAXED, normal, monster(), false)); // floor((121*164+320)/640)
		Loadout elite = withAll(loadout(Style.RANGED, "slash", rcb, bolts, helm), voidRobes(true));
		assertEquals(32, DpsCalculator.maxHit(MAXED, elite, monster(), false)); // floor((123*164+320)/640)
	}

	/** Magic void: accuracy x 29/20 (108 -> 156); elite adds 5% magic damage (50 tenths) to gear's. */
	@Test
	public void magicVoidAndElite()
	{
		Equipment staff = item("Staff of water", "weapon", "magic", 10, "magic_str", 100);
		Equipment helm = item("Void mage helm (or) (Normal)", "head");
		Loadout normal = withAll(loadout(Style.MAGIC, "slash", staff, helm), voidRobes(false));
		normal.setSpell("Water Wave");
		assertEquals(156L * 74, DpsCalculator.attackRoll(MAXED, normal, monster(), false));
		assertEquals(19, DpsCalculator.maxHit(MAXED, normal, monster(), false)); // 18 + floor(18 * 0.10)
		Loadout elite = withAll(loadout(Style.MAGIC, "slash", staff, helm), voidRobes(true));
		elite.setSpell("Water Wave");
		assertEquals(20, DpsCalculator.maxHit(MAXED, elite, monster(), false)); // 18 + floor(18 * 0.15)
	}

	@Test
	public void wrongHelmGivesNoBonus()
	{
		Loadout l = withAll(loadout(Style.MELEE, "slash", whip(), item("Void ranger helm (Normal)", "head")), voidRobes(false));
		assertEquals(25, DpsCalculator.maxHit(MAXED, l, monster(), false));
	}

	// ---- obsidian / berserker necklace ----

	private static Loadout obsidian(Equipment weapon, boolean armour)
	{
		Loadout l = loadout(Style.MELEE, "stab", weapon);
		if (armour)
		{
			withAll(l, item("Obsidian helmet", "head"), item("Obsidian platebody", "body"), item("Obsidian platelegs", "legs"));
		}
		return l;
	}

	/** Full obsidian with a Tzhaar weapon: + floor(base / 10) to the base roll and base max hit. */
	@Test
	public void obsidianArmourWithTzhaarWeapon()
	{
		Equipment ak = item("Toktz-xil-ak", "weapon", "stab", 47, "str", 49);
		// base max floor((110 * 113 + 320) / 640) = 19, + 1; base roll 107 * 111 = 11877, + 1187
		assertEquals(19, DpsCalculator.maxHit(MAXED, obsidian(ak, false), monster(), false));
		assertEquals(20, DpsCalculator.maxHit(MAXED, obsidian(ak, true), monster(), false));
		assertEquals(13064L, DpsCalculator.attackRoll(MAXED, obsidian(ak, true), monster(), false));
		Equipment sword = item("Rune sword", "weapon", "stab", 47, "str", 49);
		assertEquals(19, DpsCalculator.maxHit(MAXED, obsidian(sword, true), monster(), false));
	}

	/** Berserker necklace with a Tzhaar weapon: every hit x 6/5 (calculator scales the hit distribution). */
	@Test
	public void berserkerNecklaceWithTzhaarWeapon()
	{
		Equipment ak = item("Toktz-xil-ak", "weapon", "stab", 47, "str", 49);
		Loadout l = obsidian(ak, true);
		l.getBySlot().put("neck", item("Berserker necklace (or)", "neck"));
		assertEquals(24, DpsCalculator.maxHit(MAXED, l, monster(), false)); // floor(20 * 6 / 5)
		// Expected damage per accurate hit is the mean of floor(6d / 5) over d = 0..20, not 24 / 2.
		double mean = 0;
		for (int d = 0; d <= 20; d++)
		{
			mean += Math.floor(6.0 * d / 5);
		}
		mean /= 21;
		double expected = DpsCalculator.hitChance(MAXED, l, monster(), false) * mean / 2.4;
		assertEquals(expected, DpsCalculator.dps(MAXED, l, monster(), false), 1e-9);
		Loadout plain = obsidian(item("Rune sword", "weapon", "stab", 47, "str", 49), false);
		plain.getBySlot().put("neck", item("Berserker necklace", "neck"));
		assertEquals(19, DpsCalculator.maxHit(MAXED, plain, monster(), false));
	}

	// ---- inquisitor ----

	/** Crush only: helm +1, hauberk +2, plateskirt +2 over 200 to the final roll and max hit. */
	@Test
	public void inquisitorPerPieceOnCrush()
	{
		Equipment mace = item("Test mace", "weapon", "crush", 80, "str", 120);
		Loadout helmOnly = loadout(Style.MELEE, "crush", mace, item("Inquisitor's great helm", "head"));
		assertEquals(15408L * 201 / 200, DpsCalculator.attackRoll(MAXED, helmOnly, monster(), false));
		Loadout full = withAll(loadout(Style.MELEE, "crush", mace, item("Inquisitor's great helm", "head")),
			item("Inquisitor's hauberk", "body"), item("Inquisitor's plateskirt", "legs"));
		assertEquals(15408L * 205 / 200, DpsCalculator.attackRoll(MAXED, full, monster(), false));
		CombatStats piety = new CombatStats(99, 99, 99, 99, 99, 99, 99, 99);
		piety.setAssumeBoosts(true);
		piety.setPietyUnlocked(true);
		// strength 156 effective: floor((156 * 184 + 320) / 640) = 45 -> floor(45 * 205 / 200) = 46
		assertEquals(46, DpsCalculator.maxHit(piety, full, monster(), false));
		full.setMeleeType("slash");
		assertEquals(107L * 64, DpsCalculator.attackRoll(MAXED, full, monster(), false));
	}

	// ---- crystal ----

	/**
	 * Calculator's in-game test: slayer helmet (i) + crystal legs + crystal body + bow of faerdhinen, 99 Ranged, no
	 * prayer, on task: max 36 (it would be 37 with the crystal bonus after the helm).
	 */
	@Test
	public void crystalArmourWithBowfaBeforeSlayerHelm()
	{
		Loadout l = loadout(Style.RANGED, "slash", item("Bow of Faerdhinen (c)", "weapon", "ranged", 128, "ranged_str", 106),
			item("Slayer helmet (i)", "head"), item("Crystal body (Active)", "body"), item("Crystal legs (perfected)", "legs"));
		assertEquals(36, DpsCalculator.maxHit(MAXED, l, monster(), true));
		// roll 110 * 192 = 21120 -> x 25/20 = 26400 -> x 23/20 = 30360
		assertEquals(30360L, DpsCalculator.attackRoll(MAXED, l, monster(), true));
	}

	@Test
	public void crystalArmourNeedsCrystalBow()
	{
		Loadout l = loadout(Style.RANGED, "slash", item("Magic shortbow", "weapon", "ranged", 128, "ranged_str", 106),
			item("Crystal body (Active)", "body"));
		assertEquals(110L * 192, DpsCalculator.attackRoll(MAXED, l, monster(), false));
	}

	// ---- barrows ----

	/** Verac's: 25% of hits ignore accuracy and deal 1..max+1. */
	@Test
	public void veracsSet()
	{
		Loadout l = loadout(Style.MELEE, "crush", item("Verac's flail (Undamaged)", "weapon", "crush", 68, "str", 72),
			item("Verac's helm (Undamaged)", "head"), item("Verac's brassard (100)", "body"),
			item("Verac's plateskirt (Undamaged)", "legs"));
		int max = DpsCalculator.maxHit(MAXED, l, monster(), false);
		double acc = DpsCalculator.hitChance(MAXED, l, monster(), false);
		double expected = (0.75 * acc * max / 2.0 + 0.25 * (max + 2) / 2.0) / 2.4;
		assertEquals(expected, DpsCalculator.dps(MAXED, l, monster(), false), 1e-9);
		l.getBySlot().put("head", item("Verac's helm (0)", "head"));
		assertEquals(acc * max / 2.0 / 2.4, DpsCalculator.dps(MAXED, l, monster(), false), 1e-9);
	}

	/** Karil's with the amulet of the damned: 25% of hits add a second hitsplat of half the damage. */
	@Test
	public void karilsDamned()
	{
		Loadout l = loadout(Style.RANGED, "slash", item("Karil's crossbow (Undamaged)", "weapon", "ranged", 84),
			item("Bolt rack", "ammo", "ranged_str", 55), item("Karil's coif (Undamaged)", "head"),
			item("Karil's leathertop (Undamaged)", "body"), item("Karil's leatherskirt (Undamaged)", "legs"),
			item("Amulet of the damned (full)", "neck"));
		int max = DpsCalculator.maxHit(MAXED, l, monster(), false);
		double acc = DpsCalculator.hitChance(MAXED, l, monster(), false);
		double halves = 0;
		for (int d = 0; d <= max; d++)
		{
			halves += d / 2;
		}
		double expected = acc * (max / 2.0 + 0.25 * halves / (max + 1)) / 2.4;
		assertEquals(expected, DpsCalculator.dps(MAXED, l, monster(), false), 1e-9);
		assertTrue(expected > acc * max / 2.0 / 2.4);
	}
}
