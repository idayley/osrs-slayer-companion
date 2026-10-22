package com.slayercompanion.gear;

import com.google.gson.Gson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.Monster;
import org.junit.Test;

/**
 * Monster-specific damage rules, checked against the OSRS Wiki DPS calculator maths.
 * Maxed stats: melee effective strength 110 / attack 107, ranged 110, magic 108.
 */
public class WeaponEffectsTest
{
	private static final Gson GSON = new Gson();

	private static final CombatStats MAXED = new CombatStats(99, 99, 99, 99, 99, 99, 99, 99);

	private static final int KURASK = 410;
	private static final int TUROTH = 427;
	private static final int KALPHITE_WORKER = 955;
	private static final int FERAL_VAMPYRE = 3237;
	private static final int VAMPYRE_JUVINATE = 3694;
	private static final int VYREWATCH_SENTINEL = 9756;

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

	/** slash 70 / str 72: base max 23, attack roll 14338 at 99s. */
	private static Equipment sword(String name)
	{
		return item(name, "weapon", "slash", 70, "str", 72);
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

	private static Loadout spell(String spell, Equipment staff)
	{
		Loadout l = loadout(Style.MAGIC, staff);
		l.setSpell(spell);
		return l;
	}

	private static Monster monster(int id)
	{
		Monster m = new Monster();
		m.setId(id);
		m.setDefenceLevel(100);
		m.setMagicLevel(100);
		return m;
	}

	private static Monster plain()
	{
		return monster(1);
	}

	private static Monster dragon()
	{
		Monster m = monster(265);
		m.setDragon(true);
		return m;
	}

	private static Monster demon()
	{
		Monster m = monster(240);
		m.setDemon(true);
		return m;
	}

	private static int max(Loadout l, Monster m)
	{
		return DpsCalculator.maxHit(MAXED, l, m, false);
	}

	private static long roll(Loadout l, Monster m)
	{
		return DpsCalculator.attackRoll(MAXED, l, m, false);
	}

	// ---- Kurask / Turoth: leaf-bladed weapons, broad ammo or Magic Dart only ----

	@Test
	public void kuraskImmuneToOrdinaryMelee()
	{
		Loadout whip = loadout(Style.MELEE, item("Abyssal whip", "weapon", "slash", 82, "str", 82));
		assertEquals(0, max(whip, monster(KURASK)));
		assertEquals(0.0, DpsCalculator.dps(MAXED, whip, monster(KURASK), true), 0);
		assertTrue(WeaponEffects.isImmune(whip, monster(KURASK)));
		assertEquals(25, max(whip, plain()));
	}

	@Test
	public void leafBladedSpearAndSwordHurtKuraskAndTurothWithoutBonus()
	{
		Loadout spear = loadout(Style.MELEE, item("Leaf-bladed spear", "weapon", "slash", 47, "str", 50));
		assertEquals(20, max(spear, monster(KURASK)));
		Loadout sword = loadout(Style.MELEE, item("Leaf-bladed sword", "weapon", "slash", 47, "str", 50));
		assertEquals(20, max(sword, monster(TUROTH)));
	}

	@Test
	public void leafBladedBattleaxeDealsSeventeenPointFivePercentMoreDamageOnly()
	{
		Loadout axe = loadout(Style.MELEE, item("Leaf-bladed battleaxe", "weapon", "slash", 72, "str", 92));
		assertEquals(27, max(axe, plain()));
		// 27 * 47/40 = 31.7 -> 31; accuracy is unchanged (wiki: damage only)
		assertEquals(31, max(axe, monster(KURASK)));
		assertEquals(14552L, roll(axe, monster(KURASK)));
		assertEquals(14552L, roll(axe, plain()));
		// stacks after the slayer helm: floor(27 * 7/6) = 31, then * 47/40 = 36
		axe.getBySlot().put("head", item("Slayer helmet", "head"));
		assertEquals(36, DpsCalculator.maxHit(MAXED, axe, monster(TUROTH), true));
	}

	@Test
	public void kuraskNeedsBroadAmmoForRanged()
	{
		Equipment xbow = item("Rune crossbow", "weapon", "ranged", 90);
		Loadout runite = loadout(Style.RANGED, xbow, item("Runite bolts", "ammo", "ranged_str", 115));
		assertEquals(0, max(runite, monster(KURASK)));
		Loadout broad = loadout(Style.RANGED, xbow, item("Broad bolts", "ammo", "ranged_str", 100));
		assertEquals(28, max(broad, monster(KURASK)));
		Loadout amethyst = loadout(Style.RANGED, xbow, item("Amethyst broad bolts", "ammo", "ranged_str", 115));
		assertTrue(max(amethyst, monster(TUROTH)) > 0);
		Loadout arrows = loadout(Style.RANGED, item("Magic shortbow", "weapon", "ranged", 69),
			item("Broad arrows", "ammo", "ranged_str", 28));
		assertTrue(max(arrows, monster(KURASK)) > 0);
	}

	@Test
	public void kuraskOnlyTakesMagicDartFromSpells()
	{
		Equipment staff = item("Slayer's staff", "weapon", "magic", 10);
		assertEquals(0, max(spell("Fire Surge", staff), monster(KURASK)));
		// Magic Dart: floor(99 / 10) + 10 = 19
		assertEquals(19, max(spell("Magic Dart", staff), monster(KURASK)));
		assertEquals(5, DpsCalculator.speedTicks(spell("Magic Dart", staff)));
	}

	@Test
	public void magicDartWithEnchantedSlayerStaffOnTask()
	{
		Loadout l = spell("Magic Dart", item("Slayer's staff (e)", "weapon", "magic", 10));
		// floor(99 / 6) + 13 = 29 on task, the normal 19 off task
		assertEquals(29, DpsCalculator.maxHit(MAXED, l, monster(KURASK), true));
		assertEquals(19, DpsCalculator.maxHit(MAXED, l, monster(KURASK), false));
	}

	@Test
	public void magicDartNeedsASlayerStyleStaff()
	{
		assertEquals(0, max(spell("Magic Dart", item("Staff of air", "weapon", "magic", 10)), plain()));
		assertTrue(WeaponEffects.canCastMagicDart(item("Toxic staff of the dead (Charged)", "weapon")));
		assertTrue(WeaponEffects.canCastMagicDart(item("Staff of the Dead", "weapon")));
		assertTrue(WeaponEffects.canCastMagicDart(item("Staff of Light", "weapon")));
		assertTrue(WeaponEffects.canCastMagicDart(item("Staff of Balance", "weapon")));
		assertFalse(WeaponEffects.canCastMagicDart(item("Mystic water staff", "weapon")));
	}

	@Test
	public void restrictionHints()
	{
		assertEquals("Needs a leaf-bladed weapon, broad ammo or Magic Dart", WeaponEffects.immunityHint(monster(KURASK)));
		assertNotNull(WeaponEffects.immunityHint(monster(VAMPYRE_JUVINATE)));
		assertNotNull(WeaponEffects.immunityHint(monster(VYREWATCH_SENTINEL)));
		assertNull(WeaponEffects.immunityHint(monster(FERAL_VAMPYRE)));
		assertNull(WeaponEffects.immunityHint(plain()));
	}

	// ---- dragon hunter weapons vs draconic ----

	@Test
	public void dragonHunterLance()
	{
		Loadout l = loadout(Style.MELEE, item("Dragon hunter lance", "weapon", "slash", 85, "str", 70));
		assertEquals(23, max(l, plain()));
		assertEquals(15943L, roll(l, plain()));
		// +20% accuracy and damage: 23 * 6/5 = 27, 15943 * 6/5 = 19131
		assertEquals(27, max(l, dragon()));
		assertEquals(19131L, roll(l, dragon()));
	}

	@Test
	public void dragonHunterCrossbow()
	{
		Loadout l = loadout(Style.RANGED, item("Dragon hunter crossbow", "weapon", "ranged", 95),
			item("Dragon bolts", "ammo", "ranged_str", 122));
		assertEquals(32, max(l, plain()));
		// +30% accuracy, +25% damage
		assertEquals(40, max(l, dragon()));
		assertEquals(22737L, roll(l, dragon()));
	}

	@Test
	public void dragonHunterCrossbowIsAdditiveWithImbuedSlayerHelm()
	{
		Loadout l = loadout(Style.RANGED, item("Dragon hunter crossbow (t)", "weapon", "ranged", 95),
			item("Dragon bolts", "ammo", "ranged_str", 122), item("Slayer helmet (i)", "head"));
		// floor(32 * (23 + 5) / 20) = 44, not floor(floor(32 * 1.15) * 1.25) = 45
		assertEquals(44, DpsCalculator.maxHit(MAXED, l, dragon(), true));
		// accuracy stays multiplicative: floor(17490 * 1.15) = 20113, * 13/10 = 26146
		assertEquals(26146L, DpsCalculator.attackRoll(MAXED, l, dragon(), true));
	}

	@Test
	public void dragonHunterWand()
	{
		Loadout l = spell("Fire Surge", item("Dragon hunter wand", "weapon", "magic", 15));
		assertEquals(24, max(l, plain()));
		assertEquals(8532L, roll(l, plain()));
		// +75% accuracy, +40% damage
		assertEquals(33, max(l, dragon()));
		assertEquals(14931L, roll(l, dragon()));
	}

	// ---- demonbane ----

	@Test
	public void arclightAndEmberlightSeventyPercentVsDemons()
	{
		for (String name : new String[]{"Arclight (Charged)", "Emberlight"})
		{
			Loadout l = loadout(Style.MELEE, sword(name));
			assertEquals(23, max(l, plain()));
			assertEquals(39, max(l, demon()));
			assertEquals(24374L, roll(l, demon()));
		}
	}

	@Test
	public void silverlightAndDarklightSixtyPercentVsDemons()
	{
		for (String name : new String[]{"Darklight", "Silverlight (Normal)", "Silverlight (Dyed)"})
		{
			Loadout l = loadout(Style.MELEE, sword(name));
			assertEquals(36, max(l, demon()));
			assertEquals(22940L, roll(l, demon()));
		}
	}

	@Test
	public void burningClawsFivePercentVsDemons()
	{
		Loadout l = loadout(Style.MELEE, sword("Burning claws"));
		assertEquals(24, max(l, demon()));
		assertEquals(15054L, roll(l, demon()));
	}

	// ---- keris vs kalphites ----

	@Test
	public void kerisThirtyThreePercentDamageVsKalphites()
	{
		Loadout keris = loadout(Style.MELEE, item("Keris (Unpoisoned)", "weapon", "slash", 40, "str", 40));
		assertEquals(18, max(keris, plain()));
		assertEquals(23, max(keris, monster(KALPHITE_WORKER)));
		assertEquals(11128L, roll(keris, monster(KALPHITE_WORKER)));
		assertEquals(30, max(loadout(Style.MELEE, sword("Keris partisan")), monster(KALPHITE_WORKER)));
	}

	@Test
	public void kerisPartisanVariants()
	{
		Loadout breaching = loadout(Style.MELEE, sword("Keris partisan of breaching"));
		assertEquals(30, max(breaching, monster(KALPHITE_WORKER)));
		// breaching also gets +33% accuracy
		assertEquals(19069L, roll(breaching, monster(KALPHITE_WORKER)));
		// amascut only +15% damage
		assertEquals(26, max(loadout(Style.MELEE, sword("Keris partisan of amascut")), monster(KALPHITE_WORKER)));
	}

	// ---- vampyres ----

	@Test
	public void tierOneVampyresTakeAnyDamageAndSilverBonus()
	{
		Loadout whip = loadout(Style.MELEE, item("Abyssal whip", "weapon", "slash", 82, "str", 82));
		assertEquals(25, max(whip, monster(FERAL_VAMPYRE)));
		// silver vs tier 1: +10% damage
		assertEquals(25, max(loadout(Style.MELEE, sword("Silver sickle (b)")), monster(FERAL_VAMPYRE)));
		// blisterwood flail: +5% accuracy, +25% damage
		Loadout flail = loadout(Style.MELEE, sword("Blisterwood flail"));
		assertEquals(28, max(flail, monster(FERAL_VAMPYRE)));
		assertEquals(15054L, roll(flail, monster(FERAL_VAMPYRE)));
	}

	@Test
	public void tierTwoVampyresNeedSilver()
	{
		Monster juvinate = monster(VAMPYRE_JUVINATE);
		assertEquals(0, max(loadout(Style.MELEE, item("Abyssal whip", "weapon", "slash", 82, "str", 82)), juvinate));
		assertEquals(0, max(spell("Fire Surge", item("Staff of fire", "weapon", "magic", 10)), juvinate));
		assertEquals(27, max(loadout(Style.MELEE, sword("Ivandis flail")), juvinate));
		assertEquals(25, max(loadout(Style.MELEE, sword("Rod of Ivandis (5)")), juvinate));
		// other silver weapons hit tier 2 for at most 10
		assertEquals(10, max(loadout(Style.MELEE, sword("Silver sickle (b)")), juvinate));
		assertFalse(WeaponEffects.isImmune(loadout(Style.MELEE, sword("Silver sickle (b)")), juvinate));
	}

	@Test
	public void tierThreeVampyresNeedIvandisFlailOrBlisterwood()
	{
		Monster sentinel = monster(VYREWATCH_SENTINEL);
		assertEquals(0, max(loadout(Style.MELEE, sword("Rod of Ivandis (5)")), sentinel));
		assertEquals(0, max(loadout(Style.MELEE, sword("Silver sickle (b)")), sentinel));
		assertEquals(27, max(loadout(Style.MELEE, sword("Ivandis flail")), sentinel));
		// blisterwood sickle: +15% damage
		assertEquals(26, max(loadout(Style.MELEE, sword("Blisterwood sickle")), sentinel));
	}

	// ---- attributes ----

	@Test
	public void monsterTraitsFromIdTable()
	{
		assertTrue(MonsterTraits.isLeafy(monster(KURASK)));
		assertTrue(MonsterTraits.isLeafy(monster(TUROTH)));
		assertTrue(MonsterTraits.isKalphite(monster(KALPHITE_WORKER)));
		assertEquals(1, MonsterTraits.vampyreTier(monster(FERAL_VAMPYRE)));
		assertEquals(2, MonsterTraits.vampyreTier(monster(VAMPYRE_JUVINATE)));
		assertEquals(3, MonsterTraits.vampyreTier(monster(VYREWATCH_SENTINEL)));
		assertEquals(0, MonsterTraits.vampyreTier(plain()));
		assertFalse(MonsterTraits.isLeafy(plain()));
	}

	@Test
	public void taskMonstersInBundledDataHaveTraits()
	{
		com.slayercompanion.data.GameData data = com.slayercompanion.data.GameData.load(GSON, "/com/slayercompanion");
		assertTrue(MonsterTraits.isLeafy(data.monster(KURASK).get()));
		assertTrue(MonsterTraits.isLeafy(data.monster(TUROTH).get()));
		assertTrue(MonsterTraits.isKalphite(data.monster(KALPHITE_WORKER).get()));
		assertEquals(2, MonsterTraits.vampyreTier(data.monster(VAMPYRE_JUVINATE).get()));
		assertTrue("blue dragons are draconic", data.monster(265).get().isDragon());
	}
}
