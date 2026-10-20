package com.slayercompanion.ui;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.ItemNeed;
import com.slayercompanion.data.Monster;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.gear.GearOptimizer;
import com.slayercompanion.gear.Loadout;
import com.slayercompanion.gear.OwnedItems;
import com.slayercompanion.gear.Style;
import com.slayercompanion.task.CurrentTask;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Value;

/** What the Prep tab shows, built from domain objects on the client thread. */
@Value
public class PrepModel
{
	static final String NO_TASK = "No slayer task";
	static final String OPEN_BANK = "Open your bank once so I can see your gear";
	private static final String[] SLOT_ORDER = {"weapon", "shield", "head", "body", "legs", "neck", "cape", "ammo",
		"hands", "feet", "ring"};
	private static final String[] MELEE_TYPES = {"stab", "slash", "crush"};

	public enum ChipKind
	{
		NORMAL, GOOD, BLUE
	}

	@Value
	public static class Chip
	{
		String bold;
		String rest;
		ChipKind kind;
	}

	@Value
	public static class BringLine
	{
		String name;
		String status;
		boolean have;
	}

	@Value
	public static class Slot
	{
		String label;
		String item;
	}

	boolean noTask;
	String message;
	String title;
	String subtitle;
	List<Chip> weakTo;
	List<Chip> facts;
	boolean noSpecialItem;
	List<BringLine> bring;
	Style style;
	List<Slot> slots;
	/** Shown in place of the slots when there is nothing to list (e.g. nothing owned for this style). */
	String gearMessage;
	String upgradeItem;
	int upgradePercent;
	boolean highlighting;
	Set<Integer> highlightIds;
	String footer;

	public static PrepModel noTask()
	{
		return new PrepModel(true, NO_TASK, null, null, Collections.emptyList(), Collections.emptyList(), true,
			Collections.emptyList(), Style.MELEE, Collections.emptyList(), null, null, 0, false, Collections.emptySet(), null);
	}

	/** "Biggest upgrade: {item} (+{n}% dps)", or null when there is no upgrade. */
	public String getUpgradeText()
	{
		return upgradeItem == null ? null : "Biggest upgrade: " + upgradeItem + " (+" + upgradePercent + "% dps)";
	}

	/**
	 * @param monster the monster at the selected location; null when the task has no bundled data
	 * @param best    {@link GearOptimizer#best} for the style; empty when the bank has not been seen
	 */
	public static PrepModel build(CurrentTask current, Monster monster, OwnedItems owned, Style style,
		Optional<Loadout> best, Optional<GearOptimizer.Upgrade> upgrade, boolean highlighting, Instant now)
	{
		SlayerTask task = current.getTask();
		StringBuilder sub = new StringBuilder().append(current.getRemaining()).append(" left");
		current.getMaster().ifPresent(m -> sub.append(" · from ").append(Format.capitalize(m)));
		if (monster != null && monster.getSlayerXp() > 0)
		{
			sub.append(" · ").append(monster.getSlayerXp()).append(" xp each");
		}

		Set<Integer> highlight = new LinkedHashSet<>();
		List<BringLine> bring = new ArrayList<>();
		addBring(task.getRequiredItems(), owned, bring, highlight);
		addBring(task.getRecommendedSupplies(), owned, bring, highlight);

		List<Slot> slots = new ArrayList<>();
		String gearMessage = null;
		if (best.isPresent())
		{
			Loadout l = best.get();
			Map<String, Equipment> bySlot = l.getBySlot();
			for (String slot : SLOT_ORDER)
			{
				Equipment e = bySlot.get(slot);
				if (e != null)
				{
					slots.add(new Slot(Format.capitalize(slot), e.getName()));
					highlight.add(e.getItemId());
				}
				if ("weapon".equals(slot) && l.getStyle() == Style.MAGIC && l.getSpell() != null)
				{
					slots.add(new Slot("Spell", l.getSpell()));
				}
			}
			if (slots.isEmpty())
			{
				gearMessage = "Nothing you own fits this style";
			}
		}

		String footer = owned.bankSeenAt()
			.map(t -> "Gear read from your bank " + Math.max(0, Duration.between(t, now).toMinutes()) + " min ago")
			.orElse(OPEN_BANK);

		return new PrepModel(false, null, task.getName(), sub.toString(), weakTo(monster), facts(monster),
			task.getRequiredItems().isEmpty(), bring, style, slots, gearMessage,
			upgrade.map(u -> u.getItem().getName()).orElse(null),
			upgrade.map(u -> (int) Math.round(u.getDpsGainPercent())).orElse(0),
			highlighting, Collections.unmodifiableSet(highlight), footer);
	}

	private static void addBring(List<ItemNeed> needs, OwnedItems owned, List<BringLine> out, Set<Integer> highlight)
	{
		for (ItemNeed need : needs)
		{
			int count = 0;
			for (int id : need.getItemIds())
			{
				int c = owned.count(id);
				if (c > 0)
				{
					highlight.add(id);
				}
				count += c;
			}
			out.add(new BringLine(need.getName(), count > 0 ? "bank: " + count : "missing", count > 0));
		}
	}

	private static List<Chip> weakTo(Monster m)
	{
		List<Chip> out = new ArrayList<>();
		if (m == null)
		{
			return out;
		}
		if (m.getElementalWeakness() != null && m.getElementalWeaknessPercent() > 0)
		{
			out.add(new Chip(Format.capitalize(m.getElementalWeakness()) + " spells",
				" +" + m.getElementalWeaknessPercent() + "%", ChipKind.BLUE));
		}
		String lowest = null;
		int lowestDef = Integer.MAX_VALUE;
		for (String type : MELEE_TYPES)
		{
			Integer d = m.getDefensiveBonuses().get(type);
			if (d != null && d < lowestDef)
			{
				lowestDef = d;
				lowest = type;
			}
		}
		if (lowest != null)
		{
			out.add(new Chip(Format.capitalize(lowest), " lowest def", ChipKind.GOOD));
		}
		return out;
	}

	private static List<Chip> facts(Monster m)
	{
		List<Chip> out = new ArrayList<>();
		if (m == null)
		{
			return out;
		}
		if (m.getMaxHit() > 0)
		{
			out.add(new Chip("", "Max hit " + m.getMaxHit(), ChipKind.NORMAL));
		}
		if (!m.getAttackStyles().isEmpty())
		{
			out.add(new Chip("", "Attacks with " + String.join(", ", m.getAttackStyles()), ChipKind.NORMAL));
		}
		out.add(new Chip("", m.isVenomous() ? "Venomous" : m.isPoisonous() ? "Poisonous" : "Not poisonous",
			ChipKind.NORMAL));
		return out;
	}
}
