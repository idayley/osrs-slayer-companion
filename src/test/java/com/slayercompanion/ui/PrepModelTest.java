package com.slayercompanion.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.gear.GearOptimizer;
import com.slayercompanion.gear.OwnedItems;
import com.slayercompanion.gear.Style;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Test;

public class PrepModelTest
{
	private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

	private static OwnedItems bankSeen(int minutesAgo)
	{
		OwnedItems o = new OwnedItems();
		o.updateBank(Collections.singletonMap(Samples.SHARK, 214), NOW.minus(Duration.ofMinutes(minutesAgo)));
		return o;
	}

	@Test
	public void noTask()
	{
		PrepModel m = PrepModel.noTask();
		assertTrue(m.isNoTask());
		assertEquals("No slayer task", m.getMessage());
	}

	@Test
	public void bankNeverSeenAsksToOpenBank()
	{
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			new OwnedItems(), Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertEquals("Open your bank to load gear", m.getFooter());
		assertTrue(m.getSlots().isEmpty());
		assertNull(m.getUpgradeItem());
	}

	@Test
	public void bankSeenShowsMinutesAgo()
	{
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			bankSeen(12), Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.empty(), false, NOW);
		assertEquals("Bank last read 12 min ago", m.getFooter());
	}

	@Test
	public void headerUsesTaskNameMasterAndXp()
	{
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			new OwnedItems(), Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertEquals("Fire giants", m.getTitle());
		assertEquals("142 left · Chaeldar · 111 xp each", m.getSubtitle());
	}

	@Test
	public void weakToChips()
	{
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			new OwnedItems(), Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		List<String> weak = m.getWeakTo().stream()
			.map(w -> w.getName() + " / " + w.getDetail() + " / " + w.getIcon() + (w.isBest() ? " (best)" : ""))
			.collect(Collectors.toList());
		assertEquals(java.util.Arrays.asList("Water spells / +50% damage / magic (best)",
			"Slash / lowest defence / attack"), weak);
		List<String> facts = m.getFacts().stream().map(c -> c.getBold() + c.getRest()).collect(Collectors.toList());
		assertEquals(java.util.Arrays.asList("Max hit 11", "Melee", "Not poisonous"), facts);
		assertEquals("hitpoints", m.getFacts().get(0).getIcon());
		assertEquals(PrepModel.ChipKind.GOOD, m.getFacts().get(2).getKind());
	}

	@Test
	public void meleeWeaknessIsBestWithoutAnElementalOne()
	{
		com.slayercompanion.data.Monster giant = Samples.fireGiant();
		giant.setElementalWeakness(null);
		giant.setPoisonous(true);
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), giant, true,
			new OwnedItems(), Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertEquals(1, m.getWeakTo().size());
		assertTrue(m.getWeakTo().get(0).isBest());
		assertEquals(PrepModel.ChipKind.BAD, m.getFacts().get(2).getKind());
	}

	@Test
	public void styleIconsByAttackType()
	{
		assertEquals("magic", PrepModel.styleIcon("Water spells"));
		assertEquals("ranged", PrepModel.styleIcon("ranged"));
		assertEquals("attack", PrepModel.styleIcon("crush"));
	}

	@Test
	public void bringListShowsBankCountOrMissing()
	{
		OwnedItems owned = bankSeen(1);
		PrepModel have = PrepModel.build(Samples.task(Samples.needs("Aberrant spectres", "nose peg", Samples.SHARK), 50, 50),
			null, true, owned, Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertFalse(have.isNoSpecialItem());
		assertEquals("214 in bank", have.getBring().get(0).getStatus());
		assertTrue(have.getBring().get(0).isHave());
		assertEquals(Samples.SHARK, have.getBring().get(0).getItemId());

		PrepModel miss = PrepModel.build(Samples.task(Samples.needs("Aberrant spectres", "nose peg", 4168), 50, 50),
			null, true, owned, Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertEquals("nose peg", miss.getBring().get(0).getName());
		assertEquals("Missing", miss.getBring().get(0).getStatus());
		assertFalse(miss.getBring().get(0).isHave());
		assertEquals("missing items are pictured by their first id", 4168, miss.getBring().get(0).getItemId());

		PrepModel none = PrepModel.build(Samples.task(Samples.fireGiants(), 50, 50), Samples.fireGiant(), true, owned,
			Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertTrue(none.isNoSpecialItem());
	}

	@Test
	public void slotsInDisplayOrderAndUpgradeLine()
	{
		GearOptimizer.Upgrade upg = new GearOptimizer.Upgrade(Samples.item(11832, "Bandos chestplate", "body"), 4.3);
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			bankSeen(1), Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.of(upg), false, NOW);
		List<String> labels = m.getSlots().stream().map(PrepModel.Slot::getLabel).collect(Collectors.toList());
		assertEquals(java.util.Arrays.asList("Weapon", "Shield", "Head", "Body", "Legs", "Neck"), labels);
		assertEquals("Abyssal whip", m.getSlots().get(0).getItem());
		assertEquals(Samples.WHIP, m.getSlots().get(0).getItemId());
		assertEquals("Bandos chestplate", m.getUpgradeItem());
		assertEquals(11832, m.getUpgradeItemId());
		assertEquals("+4% dps", m.getUpgradeGain());
		assertEquals("Biggest upgrade: Bandos chestplate (+4% dps)", m.getUpgradeText());
		assertTrue(m.getHighlightIds().contains(Samples.WHIP));
	}

	@Test
	public void equippedGearIsMarkedWorn()
	{
		OwnedItems owned = bankSeen(1);
		owned.updateWorn(Collections.singletonMap(Samples.WHIP, 1));
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			owned, Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.empty(), false, NOW);
		assertTrue("whip is worn", m.getSlots().get(0).isWorn());
		assertFalse("shield is still in the bank", m.getSlots().get(1).isWorn());
		assertEquals(1, m.getWornCount());
		assertEquals("1 of 6 worn", m.getWornText());
	}

	@Test
	public void magicLoadoutShowsSpell()
	{
		com.slayercompanion.gear.Loadout l = new com.slayercompanion.gear.Loadout(new java.util.HashMap<>(),
			Style.MAGIC, "slash", "Water Wave");
		l.getBySlot().put("weapon", Samples.item(1405, "Mystic water staff", "weapon"));
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			bankSeen(1), Style.MAGIC, Optional.of(l), Optional.empty(), false, NOW);
		assertEquals("Spell", m.getSlots().get(1).getLabel());
		assertEquals("Water Wave", m.getSlots().get(1).getItem());
		assertEquals(0, m.getSlots().get(1).getItemId());
		assertEquals("the spell row is not a worn item", "0 of 1 worn", m.getWornText());
	}

	@Test
	public void gearIsGatedOnBankBeingSeen()
	{
		assertEquals(Optional.empty(), PrepModel.gateOnBank(new OwnedItems(), Optional.of("x")));
		assertEquals(Optional.of("x"), PrepModel.gateOnBank(bankSeen(0), Optional.of("x")));

		// Inventory/worn alone must not produce a gear list.
		OwnedItems invOnly = new OwnedItems();
		invOnly.updateInventory(Collections.singletonMap(Samples.WHIP, 1));
		GearOptimizer.Upgrade upg = new GearOptimizer.Upgrade(Samples.item(11832, "Bandos chestplate", "body"), 4.3);
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			invOnly, Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.of(upg), false, NOW);
		assertTrue(m.getSlots().isEmpty());
		assertNull(m.getWornText());
		assertNull(m.getUpgradeItem());
		assertEquals("Open your bank to load gear", m.getGearMessage());
		assertFalse(m.getHighlightIds().contains(Samples.WHIP));
	}

	@Test
	public void setupButtonTogglesLabel()
	{
		PrepModel off = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			bankSeen(1), Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.empty(), false, NOW);
		assertEquals("Show setup in bank", off.getSetupButtonText());
		PrepModel on = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			bankSeen(1), Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.empty(), true, NOW);
		assertEquals("Hide setup", on.getSetupButtonText());
	}

	@Test
	public void setupLayoutHasDollThenBringRow()
	{
		// Nose peg missing (first listed id used as a placeholder); sharks owned via the second listed id.
		PrepModel m = PrepModel.build(Samples.task(Samples.needs("Aberrant spectres", "nose peg", 4168), 50, 50),
			null, true, bankSeen(1), Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.empty(), true, NOW);
		int[] layout = m.getSetupLayout();
		assertEquals(Samples.WHIP, layout[16]);
		assertEquals(11864, layout[1]);
		assertEquals(4168, layout[48]);

		com.slayercompanion.data.SlayerTask t = Samples.needs("Aberrant spectres", "food", 379, Samples.SHARK);
		int[] owned = PrepModel.build(Samples.task(t, 50, 50), null, true, bankSeen(1), Style.MELEE,
			Optional.empty(), Optional.empty(), true, NOW).getSetupLayout();
		assertEquals("first owned variant, from the top when there is no gear", Samples.SHARK, owned[0]);
	}

	@Test
	public void noTaskHasEmptySetup()
	{
		assertEquals(0, PrepModel.noTask().getSetupLayout().length);
	}

	private static List<String> bringNames(PrepModel m)
	{
		return m.getBring().stream().map(PrepModel.BringLine::getName).collect(Collectors.toList());
	}

	@Test
	public void suppliesDerivedFromMonsterWhenTaskListsNone()
	{
		OwnedItems owned = bankSeen(1);
		com.slayercompanion.data.Monster giant = Samples.fireGiant();
		// Max hit 11, not poisonous: Food, plus Prayer potions only without a safespot.
		PrepModel safe = PrepModel.build(Samples.task(Samples.fireGiants(), 50, 50), giant, true, owned,
			Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertEquals(java.util.Arrays.asList("Food"), bringNames(safe));
		assertEquals("214 in bank", safe.getBring().get(0).getStatus());
		PrepModel unsafe = PrepModel.build(Samples.task(Samples.fireGiants(), 50, 50), giant, false, owned,
			Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertEquals(java.util.Arrays.asList("Food", "Prayer potions"), bringNames(unsafe));
		assertEquals("Missing", unsafe.getBring().get(1).getStatus());

		giant.setPoisonous(true);
		giant.setMaxHit(5);
		assertEquals(java.util.Arrays.asList("Food", "Antipoison"), bringNames(PrepModel.build(
			Samples.task(Samples.fireGiants(), 50, 50), giant, false, owned, Style.MELEE, Optional.empty(),
			Optional.empty(), false, NOW)));
		giant.setVenomous(true);
		assertEquals(java.util.Arrays.asList("Food", "Antivenom"), bringNames(PrepModel.build(
			Samples.task(Samples.fireGiants(), 50, 50), giant, false, owned, Style.MELEE, Optional.empty(),
			Optional.empty(), false, NOW)));
	}

	@Test
	public void taskSuppliesWinOverDerivedOnes()
	{
		com.slayercompanion.data.SlayerTask t = Samples.fireGiants();
		com.slayercompanion.data.ItemNeed shield = new com.slayercompanion.data.ItemNeed();
		shield.setName("dragonfire protection");
		shield.setItemIds(Collections.singletonList(1540));
		t.setRecommendedSupplies(Collections.singletonList(shield));
		PrepModel m = PrepModel.build(Samples.task(t, 50, 50), Samples.fireGiant(), false, bankSeen(1),
			Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertEquals(java.util.Arrays.asList("dragonfire protection"), bringNames(m));
	}

	private static PrepModel withUpgrade(com.slayercompanion.data.Equipment item, PrepModel.UpgradeFacts facts)
	{
		return PrepModel.build(Samples.task(Samples.fireGiants(), 50, 50), Samples.fireGiant(), true, bankSeen(1),
			Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.of(new GearOptimizer.Upgrade(item, 4.2)), facts,
			false, NOW);
	}

	private static PrepModel.UpgradeFacts facts(long price, int level, java.util.Set<String> questsDone)
	{
		return new PrepModel.UpgradeFacts(price, skill -> level, questsDone::contains,
			q -> "DRAGON_SLAYER_I".equals(q) ? "Dragon Slayer I" : q);
	}

	private static List<String> needs(PrepModel m)
	{
		return m.getUpgradeNeeds().stream().map(n -> n.getText() + (n.isMet() ? " (met)" : " (unmet)"))
			.collect(Collectors.toList());
	}

	@Test
	public void upgradeCostIsCompactGePrice()
	{
		PrepModel m = withUpgrade(Samples.item(11832, "Bandos chestplate", "body"),
			facts(14_632_000, 99, Collections.emptySet()));
		assertEquals("14.6m", m.getUpgradeCost());
		assertEquals("850k", PrepModel.cost(850_000));
	}

	@Test
	public void untradeableUpgradeHasNoPrice()
	{
		PrepModel m = withUpgrade(Samples.item(8850, "Rune defender", "shield"), facts(0, 99, Collections.emptySet()));
		assertEquals("Untradeable", m.getUpgradeCost());
		assertTrue(m.getUpgradeNeeds().isEmpty());
	}

	@Test
	public void untradeableUpgradeSaysHowToGetIt()
	{
		com.slayercompanion.data.Equipment defender = Samples.item(8850, "Rune defender", "shield");
		defender.setSource("Warriors' Guild cyclopes");
		assertEquals("Warriors' Guild cyclopes",
			withUpgrade(defender, facts(0, 99, Collections.emptySet())).getUpgradeSource());
		// A GE price means it can be bought, so the source is not shown.
		assertNull(withUpgrade(defender, facts(5_000, 99, Collections.emptySet())).getUpgradeSource());
		assertNull(withUpgrade(defender, null).getUpgradeSource());
		assertNull(withUpgrade(Samples.item(8850, "Rune defender", "shield"), facts(0, 99, Collections.emptySet()))
			.getUpgradeSource());
	}

	@Test
	public void skillRequirementsMetAndUnmet()
	{
		com.slayercompanion.data.Equipment e = Samples.item(11832, "Bandos chestplate", "body");
		e.getSkillReqs().put("defence", 65);
		e.getSkillReqs().put("attack", 70);
		assertEquals(java.util.Arrays.asList("70 Attack (met)", "65 Defence (met)"),
			needs(withUpgrade(e, facts(1, 70, Collections.emptySet()))));
		assertEquals(java.util.Arrays.asList("70 Attack (unmet)", "65 Defence (met)"),
			needs(withUpgrade(e, facts(1, 65, Collections.emptySet()))));
	}

	@Test
	public void questRequirementUsesDisplayName()
	{
		com.slayercompanion.data.Equipment e = Samples.item(1540, "Anti-dragon shield", "shield");
		e.getQuestReqs().add("DRAGON_SLAYER_I");
		assertEquals(java.util.Arrays.asList("Dragon Slayer I (unmet)"),
			needs(withUpgrade(e, facts(1, 99, Collections.emptySet()))));
		assertEquals(java.util.Arrays.asList("Dragon Slayer I (met)"),
			needs(withUpgrade(e, facts(1, 99, Collections.singleton("DRAGON_SLAYER_I")))));
	}

	@Test
	public void noFactsLeavesCostOut()
	{
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 50, 50), Samples.fireGiant(), true,
			bankSeen(1), Style.MELEE, Optional.of(Samples.meleeLoadout()),
			Optional.of(new GearOptimizer.Upgrade(Samples.item(11832, "Bandos chestplate", "body"), 4.2)), false, NOW);
		assertNull(m.getUpgradeCost());
	}

	@Test
	public void kuraskWithoutLeafBladedWeaponWarnsInsteadOfListingGear()
	{
		com.slayercompanion.data.Monster kurask = Samples.fireGiant();
		kurask.setId(410);
		kurask.setName("Kurask (Normal)");
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 50, 50), kurask, true,
			bankSeen(1), Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.empty(), false, NOW);
		assertTrue(m.isGearWarning());
		assertEquals("Needs a leaf-bladed weapon, broad ammo or Magic Dart", m.getGearMessage());
		assertTrue(m.getSlots().isEmpty());
		assertFalse(m.getHighlightIds().contains(Samples.WHIP));

		PrepModel giant = PrepModel.build(Samples.task(Samples.fireGiants(), 50, 50), Samples.fireGiant(), true,
			bankSeen(1), Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.empty(), false, NOW);
		assertFalse(giant.isGearWarning());
		assertNull(giant.getGearMessage());
	}

	@Test
	public void boostNoteOnlyWithGearListed()
	{
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			bankSeen(1), Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.empty(), null, false, NOW,
			"Assumes Piety and a super combat");
		assertEquals("Assumes Piety and a super combat", m.getBoostNote());
		PrepModel noBank = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(), true,
			new OwnedItems(), Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.empty(), null, false, NOW,
			"Assumes Piety and a super combat");
		assertNull(noBank.getBoostNote());
	}
}
