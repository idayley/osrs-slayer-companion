package com.slayercompanion.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.Monster;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

public class GearOptimizerTest
{
	private static final Equipment WHIP = eq(4151, "Abyssal whip", "weapon", 4, "slash", 82, "str", 82, null);
	private static final Equipment SCIM = eq(1333, "Rune scimitar", "weapon", 4, "slash", 45, "str", 44, null);
	private static final Equipment PRICEY = eq(9999, "Scythe of Vitur", "weapon", 5, "slash", 110, "str", 130, null);
	private static final Equipment HIGHREQ = eq(9998, "Godsword", "weapon", 6, "slash", 132, "str", 132, 70);

	private static Equipment eq(int id, String name, String slot, int speed, String atk, int a, String str, int s, Integer attackReq)
	{
		Equipment e = new Equipment();
		e.setItemId(id);
		e.setName(name);
		e.setSlot(slot);
		e.setSpeedTicks(speed);
		e.setCategory("Slash Sword");
		e.getBonuses().put(atk, a);
		e.getBonuses().put(str, s);
		if (attackReq != null)
		{
			e.getSkillReqs().put("attack", attackReq);
		}
		return e;
	}

	private static Monster giant()
	{
		Monster m = new Monster();
		m.setId(1);
		m.setName("Fire giant");
		m.setDefenceLevel(65);
		m.setMagicLevel(1);
		m.getDefensiveBonuses().put("slash", 20);
		m.getDefensiveBonuses().put("stab", 20);
		m.getDefensiveBonuses().put("crush", 20);
		return m;
	}

	private static CombatStats stats(int atk)
	{
		return new CombatStats(atk, 99, 99, 99, 99, 99, 99, 99);
	}

	private static OwnedItems owned(Equipment... items)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (Equipment e : items)
		{
			m.put(e.getItemId(), 1);
		}
		OwnedItems o = new OwnedItems();
		o.updateBank(m, Instant.now());
		return o;
	}

	@Test
	public void prefersWhipOverRuneScimitar()
	{
		GearOptimizer g = new GearOptimizer(List.of(WHIP, SCIM));
		Optional<Loadout> l = g.best(stats(99), Style.MELEE, giant(), false, owned(WHIP, SCIM));
		assertEquals("Abyssal whip", l.get().getBySlot().get("weapon").getName());
	}

	@Test
	public void onlyUsesOwnedItems()
	{
		GearOptimizer g = new GearOptimizer(List.of(WHIP, SCIM, PRICEY));
		OwnedItems o = owned(SCIM);
		Loadout l = g.best(stats(99), Style.MELEE, giant(), false, o).get();
		assertFalse(l.getBySlot().isEmpty());
		l.getBySlot().values().forEach(e -> assertTrue(o.count(e.getItemId()) > 0));
	}

	@Test
	public void emptyWhenBankNeverSeen()
	{
		GearOptimizer g = new GearOptimizer(List.of(WHIP));
		assertFalse(g.best(stats(99), Style.MELEE, giant(), false, new OwnedItems()).isPresent());
	}

	@Test
	public void upgradeSkipsUnmetRequirements()
	{
		GearOptimizer g = new GearOptimizer(List.of(SCIM, HIGHREQ));
		Loadout cur = new Loadout();
		cur.getBySlot().put("weapon", SCIM);
		Optional<GearOptimizer.Upgrade> u = g.biggestUpgrade(stats(60), new HashSet<>(), cur, giant(), false, owned(SCIM));
		assertFalse(u.isPresent());
	}

	@Test
	public void upgradeChecksSlayerLevel()
	{
		Equipment leaf = eq(20727, "Leaf-bladed battleaxe", "weapon", 5, "slash", 110, "str", 110, null);
		leaf.getSkillReqs().put("slayer", 55);
		GearOptimizer g = new GearOptimizer(List.of(SCIM, leaf));
		Loadout cur = new Loadout();
		cur.getBySlot().put("weapon", SCIM);
		CombatStats lowSlayer = new CombatStats(99, 99, 99, 99, 99, 99, 99, 40);
		assertFalse(g.biggestUpgrade(lowSlayer, new HashSet<>(), cur, giant(), false, owned(SCIM)).isPresent());
		CombatStats highSlayer = new CombatStats(99, 99, 99, 99, 99, 99, 99, 55);
		assertEquals("Leaf-bladed battleaxe",
			g.biggestUpgrade(highSlayer, new HashSet<>(), cur, giant(), false, owned(SCIM)).get().getItem().getName());
	}

	@Test
	public void upgradeIgnoresPrice()
	{
		GearOptimizer g = new GearOptimizer(List.of(SCIM, WHIP, PRICEY, HIGHREQ));
		Loadout cur = new Loadout();
		cur.getBySlot().put("weapon", SCIM);
		GearOptimizer.Upgrade u = g.biggestUpgrade(stats(60), new HashSet<>(), cur, giant(), false, owned(SCIM)).get();
		assertEquals("Scythe of Vitur", u.getItem().getName());
		assertTrue(u.getDpsGainPercent() > 0);
	}

	@Test
	public void upgradeSkipsOwnedItems()
	{
		GearOptimizer g = new GearOptimizer(List.of(SCIM, WHIP, PRICEY));
		Loadout cur = new Loadout();
		cur.getBySlot().put("weapon", SCIM);
		GearOptimizer.Upgrade u = g.biggestUpgrade(stats(60), new HashSet<>(), cur, giant(), false, owned(SCIM, PRICEY)).get();
		assertEquals("Abyssal whip", u.getItem().getName());
	}

	// ---- ranged / magic / 2h helpers ----

	private static Equipment item(int id, String name, String slot, String category, int speed, boolean twoHanded, Object... bonuses)
	{
		Equipment e = new Equipment();
		e.setItemId(id);
		e.setName(name);
		e.setSlot(slot);
		e.setCategory(category);
		e.setSpeedTicks(speed);
		e.setTwoHanded(twoHanded);
		for (int i = 0; i < bonuses.length; i += 2)
		{
			e.getBonuses().put((String) bonuses[i], (Integer) bonuses[i + 1]);
		}
		return e;
	}

	private static Monster rangedTarget()
	{
		Monster m = giant();
		m.getDefensiveBonuses().put("standard", 10);
		m.getDefensiveBonuses().put("light", 10);
		m.getDefensiveBonuses().put("magic", 10);
		return m;
	}

	@Test
	public void bowGetsArrowsNotJavelinsAndCrossbowGetsBolts()
	{
		Equipment bow = item(1, "Magic shortbow", "weapon", "Bow", 4, true, "ranged", 60);
		Equipment xbow = item(2, "Rune crossbow", "weapon", "Crossbow", 6, false, "ranged", 90);
		Equipment arrows = item(3, "Rune arrow (Unpoisoned)", "ammo", "", 0, false, "ranged_str", 49);
		Equipment bolts = item(4, "Runite bolts", "ammo", "", 0, false, "ranged_str", 115);
		Equipment javs = item(5, "Dragon javelin", "ammo", "", 0, false, "ranged_str", 150);
		GearOptimizer g = new GearOptimizer(List.of(bow, xbow, arrows, bolts, javs));

		Loadout l = g.best(stats(99), Style.RANGED, rangedTarget(), false, owned(bow, arrows, bolts, javs)).get();
		assertEquals("Magic shortbow", l.getBySlot().get("weapon").getName());
		assertEquals("Rune arrow (Unpoisoned)", l.getBySlot().get("ammo").getName());

		l = g.best(stats(99), Style.RANGED, rangedTarget(), false, owned(xbow, arrows, bolts, javs)).get();
		assertEquals("Rune crossbow", l.getBySlot().get("weapon").getName());
		assertEquals("Runite bolts", l.getBySlot().get("ammo").getName());

		// bow with only incompatible ammo is not usable
		assertFalse(g.best(stats(99), Style.RANGED, rangedTarget(), false, owned(bow, bolts, javs)).get()
			.getBySlot().containsKey("weapon"));
	}

	@Test
	public void dartsGetNoAmmo()
	{
		Equipment dart = item(1, "Rune dart", "weapon", "Thrown", 3, false, "ranged", 20, "ranged_str", 30);
		Equipment arrows = item(3, "Rune arrow (Unpoisoned)", "ammo", "", 0, false, "ranged_str", 49);
		GearOptimizer g = new GearOptimizer(List.of(dart, arrows));
		Loadout l = g.best(stats(99), Style.RANGED, rangedTarget(), false, owned(dart, arrows)).get();
		assertEquals("Rune dart", l.getBySlot().get("weapon").getName());
		assertFalse(l.getBySlot().containsKey("ammo"));
	}

	@Test
	public void upgradeAmmoMustBeCompatible()
	{
		Equipment bow = item(1, "Magic shortbow", "weapon", "Bow", 4, true, "ranged", 60);
		Equipment arrows = item(3, "Rune arrow (Unpoisoned)", "ammo", "", 0, false, "ranged_str", 49);
		Equipment bolts = item(4, "Runite bolts", "ammo", "", 0, false, "ranged_str", 115);
		Equipment dragonArrows = item(6, "Dragon arrow", "ammo", "", 0, false, "ranged_str", 60);
		GearOptimizer g = new GearOptimizer(List.of(bow, arrows, bolts, dragonArrows));
		Loadout cur = new Loadout();
		cur.setStyle(Style.RANGED);
		cur.getBySlot().put("weapon", bow);
		cur.getBySlot().put("ammo", arrows);
		GearOptimizer.Upgrade u = g.biggestUpgrade(stats(99), new HashSet<>(), cur, rangedTarget(), false, owned(bow, arrows)).get();
		assertEquals("Dragon arrow", u.getItem().getName());
	}

	@Test
	public void choosesTwoHandedOrOneHandPlusShield()
	{
		Equipment twoH = item(1, "Big 2h", "weapon", "2h Sword", 4, true, "slash", 100, "str", 100);
		Equipment oneH = item(2, "Small 1h", "weapon", "Slash Sword", 4, false, "slash", 50, "str", 50);
		Equipment goodShield = item(3, "Strength shield", "shield", "", 0, false, "str", 200);
		GearOptimizer g = new GearOptimizer(List.of(twoH, oneH, goodShield));

		Loadout l = g.best(stats(99), Style.MELEE, giant(), false, owned(twoH, oneH)).get();
		assertEquals("Big 2h", l.getBySlot().get("weapon").getName());

		l = g.best(stats(99), Style.MELEE, giant(), false, owned(twoH, oneH, goodShield)).get();
		if (l.getBySlot().get("weapon").isTwoHanded())
		{
			assertFalse(l.getBySlot().containsKey("shield"));
		}
		else
		{
			assertTrue(l.getBySlot().containsKey("shield"));
		}
		// a shield with +200 str is worth more than 2h's extra 50 str
		assertEquals("Small 1h", l.getBySlot().get("weapon").getName());
		assertEquals("Strength shield", l.getBySlot().get("shield").getName());
	}

	@Test
	public void staffUsesBestSpellForMagicLevel()
	{
		Equipment staff = item(1, "Staff of air", "weapon", "Staff", 4, false, "magic", 10);
		GearOptimizer g = new GearOptimizer(List.of(staff));
		Monster m = rangedTarget();
		Loadout l = g.best(new CombatStats(99, 99, 99, 99, 50, 99, 99, 99), Style.MAGIC, m, false, owned(staff)).get();
		assertTrue(l.getSpell(), l.getSpell().endsWith("Blast"));
		l = g.best(new CombatStats(99, 99, 99, 99, 99, 99, 99, 99), Style.MAGIC, m, false, owned(staff)).get();
		assertTrue(l.getSpell(), l.getSpell().endsWith("Surge"));
	}

	@Test
	public void poweredStaffNonTridentNeverRecommended()
	{
		Equipment sang = item(1, "Sanguinesti staff", "weapon", "Powered Staff", 4, false, "magic", 25, "magic_str", 150);
		Equipment staff = item(2, "Staff of air", "weapon", "Staff", 4, false, "magic", 10);
		Equipment trident = item(3, "Trident of the seas", "weapon", "Powered Staff", 4, false, "magic", 15);
		GearOptimizer g = new GearOptimizer(List.of(sang, staff, trident));
		Monster m = rangedTarget();
		Loadout l = g.best(stats(99), Style.MAGIC, m, false, owned(sang, staff)).get();
		assertEquals("Staff of air", l.getBySlot().get("weapon").getName());
		Loadout cur = new Loadout();
		cur.setStyle(Style.MAGIC);
		cur.setSpell("Fire Surge");
		cur.getBySlot().put("weapon", staff);
		Optional<GearOptimizer.Upgrade> u = g.biggestUpgrade(stats(99), new HashSet<>(), cur, m, false, owned(staff));
		assertFalse(u.isPresent() && u.get().getItem().getName().startsWith("Sanguinesti"));
	}

	// ---- monster-specific damage rules ----

	private static Monster kurask()
	{
		Monster m = rangedTarget();
		m.setId(410);
		m.setName("Kurask (Normal)");
		return m;
	}

	@Test
	public void kuraskPicksLeafBladedWeaponOverStrongerWhip()
	{
		Equipment spear = item(4158, "Leaf-bladed spear", "weapon", "Spear", 5, false, "stab", 47, "slash", 42, "str", 50);
		GearOptimizer g = new GearOptimizer(List.of(WHIP, spear));
		assertEquals("Leaf-bladed spear",
			g.best(stats(99), Style.MELEE, kurask(), true, owned(WHIP, spear)).get().getBySlot().get("weapon").getName());
		assertEquals("Abyssal whip",
			g.best(stats(99), Style.MELEE, giant(), true, owned(WHIP, spear)).get().getBySlot().get("weapon").getName());
	}

	@Test
	public void kuraskWithNoValidWeaponRecommendsNothing()
	{
		Equipment helm = item(10, "Helm of neitiznot", "head", "", 0, false, "str", 3);
		GearOptimizer g = new GearOptimizer(List.of(WHIP, helm));
		Loadout l = g.best(stats(99), Style.MELEE, kurask(), true, owned(WHIP, helm)).get();
		assertTrue(l.getBySlot().isEmpty());
		assertTrue(WeaponEffects.isImmune(l, kurask()));
	}

	@Test
	public void kuraskRangedPicksBroadBolts()
	{
		Equipment xbow = item(2, "Rune crossbow", "weapon", "Crossbow", 6, false, "ranged", 90);
		Equipment runite = item(4, "Runite bolts", "ammo", "", 0, false, "ranged_str", 115);
		Equipment broad = item(11875, "Broad bolts", "ammo", "", 0, false, "ranged_str", 100);
		GearOptimizer g = new GearOptimizer(List.of(xbow, runite, broad));
		Loadout l = g.best(stats(99), Style.RANGED, kurask(), true, owned(xbow, runite, broad)).get();
		assertEquals("Rune crossbow", l.getBySlot().get("weapon").getName());
		assertEquals("Broad bolts", l.getBySlot().get("ammo").getName());
		l = g.best(stats(99), Style.RANGED, rangedTarget(), true, owned(xbow, runite, broad)).get();
		assertEquals("Runite bolts", l.getBySlot().get("ammo").getName());
	}

	@Test
	public void kuraskMagicUsesMagicDartWithSlayerStaff()
	{
		Equipment slayerStaff = item(4170, "Slayer's staff", "weapon", "Staff", 4, false, "magic", 10);
		slayerStaff.getSkillReqs().put("magic", 50);
		slayerStaff.getSkillReqs().put("slayer", 55);
		GearOptimizer g = new GearOptimizer(List.of(slayerStaff));
		Loadout l = g.best(stats(99), Style.MAGIC, kurask(), true, owned(slayerStaff)).get();
		assertEquals("Magic Dart", l.getSpell());
		// Magic Dart needs 55 Slayer; without it, nothing hurts Kurask by magic
		CombatStats lowSlayer = new CombatStats(99, 99, 99, 99, 99, 99, 99, 40);
		slayerStaff.getSkillReqs().clear();
		l = g.best(lowSlayer, Style.MAGIC, kurask(), true, owned(slayerStaff)).get();
		assertFalse(l.getBySlot().containsKey("weapon"));
	}

	@Test
	public void upgradeSuggestsLeafBladedWeaponWhenNothingOwnedWorks()
	{
		Equipment sword = item(11902, "Leaf-bladed sword", "weapon", "Stab Sword", 4, false, "stab", 67, "str", 70);
		GearOptimizer g = new GearOptimizer(List.of(WHIP, sword));
		Loadout cur = g.best(stats(99), Style.MELEE, kurask(), true, owned(WHIP)).get();
		GearOptimizer.Upgrade u = g.biggestUpgrade(stats(99), new HashSet<>(), cur, kurask(), true, owned(WHIP)).get();
		assertEquals("Leaf-bladed sword", u.getItem().getName());
	}

	@Test
	public void ivandisAndBlisterwoodFlailsAreMeleeWeapons()
	{
		assertEquals(Style.MELEE, GearOptimizer.classify(item(22398, "Ivandis flail", "weapon", "Staff", 5, false)));
		assertEquals(Style.MELEE, GearOptimizer.classify(item(24699, "Blisterwood flail", "weapon", "Staff", 5, false)));
		assertEquals(Style.MELEE, GearOptimizer.classify(item(7639, "Rod of Ivandis (10)", "weapon", "Staff", 5, false)));
		assertEquals(Style.MAGIC, GearOptimizer.classify(item(1, "Staff of air", "weapon", "Staff", 4, false)));
	}

	@Test
	public void juvinatePicksIvandisFlailOverWhip()
	{
		Equipment flail = item(22398, "Ivandis flail", "weapon", "Staff", 5, false, "crush", 33, "str", 40);
		Monster juvinate = giant();
		juvinate.setId(3694);
		GearOptimizer g = new GearOptimizer(List.of(WHIP, flail));
		assertEquals("Ivandis flail",
			g.best(stats(99), Style.MELEE, juvinate, true, owned(WHIP, flail)).get().getBySlot().get("weapon").getName());
	}

	@Test
	public void smokeRealEquipmentUnder500ms()
	{
		com.slayercompanion.data.GameData data = com.slayercompanion.data.GameData.load("/com/slayercompanion");
		Monster m = data.monster(2084).orElseGet(GearOptimizerTest::giant);
		GearOptimizer g = new GearOptimizer(data.allEquipment());
		List<Equipment> all = new ArrayList<>(data.allEquipment());
		OwnedItems o = new OwnedItems();
		Map<Integer, Integer> bank = new HashMap<>();
		for (int i = 0; i < all.size(); i += 3)
		{
			bank.put(all.get(i).getItemId(), 1);
		}
		o.updateBank(bank, Instant.now());
		CombatStats s = stats(80);
		s.setAssumeBoosts(true);
		long t0 = System.nanoTime();
		Loadout l = null;
		for (Style st : Style.values())
		{
			Optional<Loadout> r = g.best(s, st, m, true, o);
			assertTrue(r.isPresent());
			l = r.get();
		}
		Loadout cur = new Loadout();
		g.biggestUpgrade(s, new HashSet<>(), cur, m, true, o);
		long ms = (System.nanoTime() - t0) / 1_000_000;
		System.out.println("SMOKE_TIMING_MS=" + ms + " catalogue=" + all.size());
		assertTrue("took " + ms + " ms", ms < 500);
	}

	// ---- set bonuses ----

	private static Equipment piece(int id, String name, String slot, int bonus)
	{
		return eq(id, name, slot, 0, "slash", bonus, "str", bonus, null);
	}

	private static final Equipment VOID_HELM = piece(11665, "Void melee helm (Normal)", "head", 0);
	private static final Equipment VOID_TOP = piece(8839, "Void knight top (Normal)", "body", 0);
	private static final Equipment VOID_ROBE = piece(8840, "Void knight robe (Normal)", "legs", 0);
	private static final Equipment VOID_GLOVES = piece(8842, "Void knight gloves (Normal)", "hands", 0);
	private static final Equipment[] PLUS_ONE = {piece(101, "Plus helm", "head", 1), piece(102, "Plus body", "body", 1),
		piece(103, "Plus legs", "legs", 1), piece(104, "Plus gloves", "hands", 1)};

	/** Each +1 piece beats a lone void piece, so slot-by-slot swaps alone would never find the set. */
	@Test
	public void picksCompleteOwnedVoidSet()
	{
		List<Equipment> cat = new ArrayList<>(List.of(WHIP, VOID_HELM, VOID_TOP, VOID_ROBE, VOID_GLOVES));
		cat.addAll(List.of(PLUS_ONE));
		List<Equipment> own = new ArrayList<>(cat);
		GearOptimizer g = new GearOptimizer(cat);
		Loadout l = g.best(stats(99), Style.MELEE, giant(), false, owned(own.toArray(new Equipment[0]))).get();
		assertEquals("Void melee helm (Normal)", l.getBySlot().get("head").getName());
		assertEquals("Void knight gloves (Normal)", l.getBySlot().get("hands").getName());
		assertEquals("Abyssal whip", l.getBySlot().get("weapon").getName());
	}

	@Test
	public void ignoresVoidSetWhenPiecesAreBetter()
	{
		Equipment[] strong = {piece(201, "Strong helm", "head", 15), piece(202, "Strong body", "body", 15),
			piece(203, "Strong legs", "legs", 15), piece(204, "Strong gloves", "hands", 15)};
		List<Equipment> cat = new ArrayList<>(List.of(WHIP, VOID_HELM, VOID_TOP, VOID_ROBE, VOID_GLOVES));
		cat.addAll(List.of(strong));
		GearOptimizer g = new GearOptimizer(cat);
		Loadout l = g.best(stats(99), Style.MELEE, giant(), false, owned(cat.toArray(new Equipment[0]))).get();
		assertEquals("Strong helm", l.getBySlot().get("head").getName());
	}

	/** The missing helm is the upgrade, valued with the owned void pieces it completes. */
	@Test
	public void upgradeCompletesAnOwnedSet()
	{
		List<Equipment> cat = new ArrayList<>(List.of(WHIP, VOID_HELM, VOID_TOP, VOID_ROBE, VOID_GLOVES));
		cat.addAll(List.of(PLUS_ONE));
		OwnedItems o = owned(WHIP, VOID_TOP, VOID_ROBE, VOID_GLOVES, PLUS_ONE[0], PLUS_ONE[1], PLUS_ONE[2], PLUS_ONE[3]);
		GearOptimizer g = new GearOptimizer(cat);
		Loadout best = g.best(stats(99), Style.MELEE, giant(), false, o).get();
		assertEquals("Plus helm", best.getBySlot().get("head").getName());
		GearOptimizer.Upgrade u = g.biggestUpgrade(stats(99), new HashSet<>(), best, giant(), false, o).get();
		assertEquals("Void melee helm (Normal)", u.getItem().getName());
		assertTrue(u.getDpsGainPercent() > 0);
	}

	/** Prayers and potions apply to both sides of the comparison, so the gain is upgrade vs best under the same boosts. */
	@Test
	public void upgradePercentUsesTheSameBoostsOnBothSides()
	{
		CombatStats s = stats(99);
		s.setAssumeBoosts(true);
		s.setPietyUnlocked(true);
		GearOptimizer g = new GearOptimizer(List.of(SCIM, WHIP));
		Loadout cur = g.best(s, Style.MELEE, giant(), false, owned(SCIM)).get();
		GearOptimizer.Upgrade u = g.biggestUpgrade(s, new HashSet<>(), cur, giant(), false, owned(SCIM)).get();
		Loadout up = new Loadout();
		up.getBySlot().put("weapon", WHIP);
		up.setMeleeType("slash");
		double before = DpsCalculator.dps(s, cur, giant(), false);
		double after = DpsCalculator.dps(s, up, giant(), false);
		assertEquals((after - before) / before * 100.0, u.getDpsGainPercent(), 1e-6);
		// And it differs from the unboosted figure (boosted strength scales bonuses differently).
		Loadout curPlain = g.best(stats(99), Style.MELEE, giant(), false, owned(SCIM)).get();
		double plain = g.biggestUpgrade(stats(99), new HashSet<>(), curPlain, giant(), false, owned(SCIM)).get().getDpsGainPercent();
		assertTrue(Math.abs(plain - u.getDpsGainPercent()) > 0.01);
	}
}
