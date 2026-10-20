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
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(),
			new OwnedItems(), Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertEquals("Open your bank once so I can see your gear", m.getFooter());
		assertTrue(m.getSlots().isEmpty());
		assertNull(m.getUpgradeItem());
	}

	@Test
	public void bankSeenShowsMinutesAgo()
	{
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(),
			bankSeen(12), Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.empty(), false, NOW);
		assertEquals("Gear read from your bank 12 min ago", m.getFooter());
	}

	@Test
	public void headerUsesTaskNameMasterAndXp()
	{
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(),
			new OwnedItems(), Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertEquals("Fire giants", m.getTitle());
		assertEquals("142 left · from Chaeldar · 111 xp each", m.getSubtitle());
	}

	@Test
	public void weakToChips()
	{
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(),
			new OwnedItems(), Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		List<String> weak = m.getWeakTo().stream().map(c -> c.getBold() + c.getRest()).collect(Collectors.toList());
		assertEquals(java.util.Arrays.asList("Water spells +50%", "Slash lowest def"), weak);
		List<String> facts = m.getFacts().stream().map(c -> c.getBold() + c.getRest()).collect(Collectors.toList());
		assertEquals(java.util.Arrays.asList("Max hit 11", "Attacks with melee", "Not poisonous"), facts);
	}

	@Test
	public void bringListShowsBankCountOrMissing()
	{
		OwnedItems owned = bankSeen(1);
		PrepModel have = PrepModel.build(Samples.task(Samples.needs("Aberrant spectres", "nose peg", Samples.SHARK), 50, 50),
			null, owned, Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertFalse(have.isNoSpecialItem());
		assertEquals("bank: 214", have.getBring().get(0).getStatus());
		assertTrue(have.getBring().get(0).isHave());

		PrepModel miss = PrepModel.build(Samples.task(Samples.needs("Aberrant spectres", "nose peg", 4168), 50, 50),
			null, owned, Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertEquals("nose peg", miss.getBring().get(0).getName());
		assertEquals("missing", miss.getBring().get(0).getStatus());
		assertFalse(miss.getBring().get(0).isHave());

		PrepModel none = PrepModel.build(Samples.task(Samples.fireGiants(), 50, 50), Samples.fireGiant(), owned,
			Style.MELEE, Optional.empty(), Optional.empty(), false, NOW);
		assertTrue(none.isNoSpecialItem());
	}

	@Test
	public void slotsInDisplayOrderAndUpgradeLine()
	{
		GearOptimizer.Upgrade upg = new GearOptimizer.Upgrade(Samples.item(11832, "Bandos chestplate", "body"), 4.3);
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(),
			bankSeen(1), Style.MELEE, Optional.of(Samples.meleeLoadout()), Optional.of(upg), false, NOW);
		List<String> labels = m.getSlots().stream().map(PrepModel.Slot::getLabel).collect(Collectors.toList());
		assertEquals(java.util.Arrays.asList("Weapon", "Shield", "Head", "Body", "Legs", "Neck"), labels);
		assertEquals("Abyssal whip", m.getSlots().get(0).getItem());
		assertEquals("Bandos chestplate", m.getUpgradeItem());
		assertEquals("Biggest upgrade: Bandos chestplate (+4% dps)", m.getUpgradeText());
		assertTrue(m.getHighlightIds().contains(Samples.WHIP));
	}

	@Test
	public void magicLoadoutShowsSpell()
	{
		com.slayercompanion.gear.Loadout l = new com.slayercompanion.gear.Loadout(new java.util.HashMap<>(),
			Style.MAGIC, "slash", "Water Wave");
		l.getBySlot().put("weapon", Samples.item(1405, "Mystic water staff", "weapon"));
		PrepModel m = PrepModel.build(Samples.task(Samples.fireGiants(), 142, 142), Samples.fireGiant(),
			bankSeen(1), Style.MAGIC, Optional.of(l), Optional.empty(), false, NOW);
		assertEquals("Spell", m.getSlots().get(1).getLabel());
		assertEquals("Water Wave", m.getSlots().get(1).getItem());
	}
}
