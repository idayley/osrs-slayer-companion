package com.slayercompanion.ui;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.ItemNeed;
import com.slayercompanion.data.Monster;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.gear.GearOptimizer;
import com.slayercompanion.gear.Loadout;
import com.slayercompanion.gear.OwnedItems;
import com.slayercompanion.gear.SetupLayout;
import com.slayercompanion.gear.Style;
import com.slayercompanion.gear.WeaponEffects;
import com.slayercompanion.task.CurrentTask;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import lombok.Value;

/** What the Prep tab shows, built from domain objects on the client thread. */
@Value
public class PrepModel
{
	static final String NO_TASK = "No slayer task";
	static final String OPEN_BANK = "Open your bank to load gear";
	static final String UNTRADEABLE = "Untradeable";
	static final String NO_REQUIREMENTS = "No requirements";
	private static final String[] SLOT_ORDER = {"weapon", "shield", "head", "body", "legs", "neck", "cape", "ammo",
		"hands", "feet", "ring"};
	private static final String[] MELEE_TYPES = {"stab", "slash", "crush"};

	public enum ChipKind
	{
		NORMAL, GOOD, BLUE, BAD
	}

	@Value
	public static class Chip
	{
		String bold;
		String rest;
		ChipKind kind;
		/** Skill icon drawn before the text ("hitpoints"), or null. */
		String icon;
	}

	/** One "Weak to" tile: "Water spells" / "+50% damage", with the skill icon for that combat style. */
	@Value
	public static class Weakness
	{
		String name;
		String detail;
		/** Skill icon: "magic" for spells, "attack" for melee styles, "ranged" for ranged. */
		String icon;
		/** The strongest weakness (shown first, in blue). */
		boolean best;
	}

	@Value
	public static class BringLine
	{
		String name;
		String status;
		boolean have;
		/** Item to picture: the first variant the player owns, else the first listed; 0 when none. */
		int itemId;
	}

	@Value
	public static class Slot
	{
		String label;
		String item;
		/** Equipped right now, so the player can see what's left to put on. */
		boolean worn;
		/** Item to picture; 0 for the spell row. */
		int itemId;
	}

	/** One requirement of the upgrade item: "65 Defence" or a quest name, met or not. */
	@Value
	public static class Need
	{
		String text;
		boolean met;
	}

	/**
	 * Client-side facts about the upgrade item, gathered on the client thread so the model stays testable.
	 * A price of 0 or less means untradeable.
	 */
	@Value
	public static class UpgradeFacts
	{
		long price;
		ToIntFunction<String> skillLevel;
		Predicate<String> questDone;
		Function<String, String> questName;
	}

	boolean noTask;
	String message;
	String title;
	String subtitle;
	List<Weakness> weakTo;
	List<Chip> facts;
	boolean noSpecialItem;
	List<BringLine> bring;
	Style style;
	List<Slot> slots;
	/** Shown in place of the slots when there is nothing to list (e.g. nothing owned for this style). */
	String gearMessage;
	/** The gear message is a warning: nothing owned for this style can hurt the monster (e.g. Kurask). */
	boolean gearWarning;
	String upgradeItem;
	int upgradeItemId;
	int upgradePercent;
	/** "≈ 14.6m GE" or "Untradeable"; null when there is no upgrade or no facts were supplied. */
	String upgradeCost;
	/** How to get an untradeable upgrade ("TzHaar Fight Cave reward"); null when tradeable or unknown. */
	String upgradeSource;
	/** Requirements of the upgrade item; empty means none ({@link #NO_REQUIREMENTS}). */
	List<Need> upgradeNeeds;
	/** "Show setup in bank" is on (bank tab, or the highlight overlay when Bank Tags is unavailable). */
	boolean highlighting;
	Set<Integer> highlightIds;
	/** {@link SetupLayout} of the recommended gear and bring items, for the bank tab. */
	int[] setupLayout;
	String footer;
	/** "Assumes Piety and a super combat" under the gear list; null when no gear is listed. */
	String boostNote;

	public static PrepModel noTask()
	{
		return new PrepModel(true, NO_TASK, null, null, Collections.emptyList(), Collections.emptyList(), true,
			Collections.emptyList(), Style.MELEE, Collections.emptyList(), null, false, null, 0, 0, null, null, Collections.emptyList(), false, Collections.emptySet(), new int[0], null, null);
	}

	public String getSetupButtonText()
	{
		return highlighting ? "Hide setup" : "Show setup in bank";
	}

	/** Gear slots (not the spell row) that are equipped right now. */
	public int getWornCount()
	{
		return (int) slots.stream().filter(s -> s.getItemId() > 0 && s.isWorn()).count();
	}

	/** "3 of 6 worn", or null when there is no gear listed. */
	public String getWornText()
	{
		long items = slots.stream().filter(s -> s.getItemId() > 0).count();
		return items == 0 ? null : getWornCount() + " of " + items + " worn";
	}

	/** "+4% dps", or null when there is no upgrade. */
	public String getUpgradeGain()
	{
		return upgradeItem == null ? null : "+" + upgradePercent + "% dps";
	}

	/** "Biggest upgrade: {item} (+{n}% dps)", or null when there is no upgrade. */
	public String getUpgradeText()
	{
		return upgradeItem == null ? null : "Biggest upgrade: " + upgradeItem + " (+" + upgradePercent + "% dps)";
	}

	/** Gear advice needs the bank: until it has been seen, inventory and worn items alone would mislead. */
	public static <T> Optional<T> gateOnBank(OwnedItems owned, Optional<T> value)
	{
		return owned.bankSeen() ? value : Optional.empty();
	}

	/**
	 * @param monster     the monster at the selected location; null when the task has no bundled data
	 * @param hasSafespot whether the selected location has a known safespot (affects suggested supplies)
	 * @param best        {@link GearOptimizer#best} for the style; ignored until the bank has been seen
	 */
	public static PrepModel build(CurrentTask current, Monster monster, boolean hasSafespot, OwnedItems owned,
		Style style, Optional<Loadout> bestGear, Optional<GearOptimizer.Upgrade> bestUpgrade, boolean highlighting,
		Instant now)
	{
		return build(current, monster, hasSafespot, owned, style, bestGear, bestUpgrade, null, highlighting, now);
	}

	/** @param facts price and requirement state for the upgrade item; null leaves Cost and Needs out */
	public static PrepModel build(CurrentTask current, Monster monster, boolean hasSafespot, OwnedItems owned,
		Style style, Optional<Loadout> bestGear, Optional<GearOptimizer.Upgrade> bestUpgrade, UpgradeFacts facts,
		boolean highlighting, Instant now)
	{
		return build(current, monster, hasSafespot, owned, style, bestGear, bestUpgrade, facts, highlighting, now, null);
	}

	/** @param boostNote prayer and potion the dps maths assumes ({@code Boosts.note}); shown with the gear list */
	public static PrepModel build(CurrentTask current, Monster monster, boolean hasSafespot, OwnedItems owned,
		Style style, Optional<Loadout> bestGear, Optional<GearOptimizer.Upgrade> bestUpgrade, UpgradeFacts facts,
		boolean highlighting, Instant now, String boostNote)
	{
		Optional<Loadout> best = gateOnBank(owned, bestGear);
		Optional<GearOptimizer.Upgrade> upgrade = gateOnBank(owned, bestUpgrade);
		SlayerTask task = current.getTask();
		StringBuilder sub = new StringBuilder().append(current.getRemaining()).append(" left");
		current.getMaster().ifPresent(m -> sub.append(" · ").append(Format.capitalize(m)));
		if (monster != null && monster.getSlayerXp() > 0)
		{
			sub.append(" · ").append(monster.getSlayerXp()).append(" xp each");
		}

		Set<Integer> highlight = new LinkedHashSet<>();
		List<BringLine> bring = new ArrayList<>();
		List<Integer> bringIds = new ArrayList<>();
		addBring(task.getRequiredItems(), owned, bring, highlight, bringIds);
		addBring(task.getRecommendedSupplies().isEmpty() ? Supplies.derive(monster, hasSafespot)
			: task.getRecommendedSupplies(), owned, bring, highlight, bringIds);
		Map<String, Integer> slotIds = new HashMap<>();

		List<Slot> slots = new ArrayList<>();
		String gearMessage = owned.bankSeen() ? null : OPEN_BANK;
		boolean gearWarning = false;
		if (best.isPresent() && monster != null && WeaponEffects.isImmune(best.get(), monster))
		{
			// Listing armour around a weapon that can't hurt the monster would only mislead.
			gearMessage = WeaponEffects.immunityHint(monster);
			gearWarning = true;
		}
		else if (best.isPresent())
		{
			Loadout l = best.get();
			Map<String, Equipment> bySlot = l.getBySlot();
			for (String slot : SLOT_ORDER)
			{
				Equipment e = bySlot.get(slot);
				if (e != null)
				{
					slots.add(new Slot(Format.capitalize(slot), e.getName(), owned.isWorn(e.getItemId()), e.getItemId()));
					highlight.add(e.getItemId());
					slotIds.put(slot, e.getItemId());
				}
				if ("weapon".equals(slot) && l.getStyle() == Style.MAGIC && l.getSpell() != null)
				{
					slots.add(new Slot("Spell", l.getSpell(), false, 0));
				}
			}
			if (slots.isEmpty())
			{
				gearMessage = "No owned gear for this style";
			}
		}

		String footer = owned.bankSeenAt()
			.map(t -> "Bank last read " + Math.max(0, Duration.between(t, now).toMinutes()) + " min ago")
			.orElse(OPEN_BANK);

		return new PrepModel(false, null, task.getName(), sub.toString(), weakTo(monster), facts(monster),
			task.getRequiredItems().isEmpty(), bring, style, slots, gearMessage, gearWarning,
			upgrade.map(u -> u.getItem().getName()).orElse(null),
			upgrade.map(u -> u.getItem().getItemId()).orElse(0),
			upgrade.map(u -> (int) Math.round(u.getDpsGainPercent())).orElse(0),
			upgrade.isPresent() && facts != null ? cost(facts.getPrice()) : null,
			upgrade.isPresent() && facts != null && facts.getPrice() <= 0 ? upgrade.get().getItem().getSource() : null,
			upgrade.isPresent() && facts != null ? needs(upgrade.get().getItem(), facts) : Collections.emptyList(),
			highlighting, Collections.unmodifiableSet(highlight), SetupLayout.build(slotIds, bringIds), footer,
			slots.isEmpty() ? null : boostNote);
	}

	static String cost(long price)
	{
		return price > 0 ? Format.compact(price) : UNTRADEABLE;
	}

	private static List<Need> needs(Equipment item, UpgradeFacts facts)
	{
		List<Need> out = new ArrayList<>();
		// Sorted by skill so the row reads the same every time.
		for (Map.Entry<String, Integer> r : new TreeMap<>(item.getSkillReqs()).entrySet())
		{
			out.add(new Need(r.getValue() + " " + Format.capitalize(r.getKey().toLowerCase(java.util.Locale.ROOT)),
				facts.getSkillLevel().applyAsInt(r.getKey()) >= r.getValue()));
		}
		for (String q : item.getQuestReqs())
		{
			out.add(new Need(facts.getQuestName().apply(q), facts.getQuestDone().test(q)));
		}
		return out;
	}

	private static void addBring(List<ItemNeed> needs, OwnedItems owned, List<BringLine> out, Set<Integer> highlight,
		List<Integer> setupIds)
	{
		for (ItemNeed need : needs)
		{
			int count = 0;
			Integer setupId = need.getItemIds().isEmpty() ? null : need.getItemIds().get(0);
			boolean ownedFound = false;
			for (int id : need.getItemIds())
			{
				int c = owned.count(id);
				if (c > 0)
				{
					highlight.add(id);
					if (!ownedFound)
					{
						// The first variant the player has, so the bank tab finds it.
						setupId = id;
						ownedFound = true;
					}
				}
				count += c;
			}
			if (setupId != null)
			{
				setupIds.add(setupId);
			}
			out.add(new BringLine(need.getName(), count > 0 ? count + " in bank" : "Missing", count > 0,
				setupId == null ? 0 : setupId));
		}
	}

	private static List<Weakness> weakTo(Monster m)
	{
		List<Weakness> out = new ArrayList<>();
		if (m == null)
		{
			return out;
		}
		if (m.getElementalWeakness() != null && m.getElementalWeaknessPercent() > 0)
		{
			out.add(new Weakness(Format.capitalize(m.getElementalWeakness()) + " spells",
				"+" + m.getElementalWeaknessPercent() + "% damage", "magic", true));
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
			out.add(new Weakness(Format.capitalize(lowest), "lowest defence", styleIcon(lowest), out.isEmpty()));
		}
		return out;
	}

	/** Skill icon for an attack type: spells are magic, ranged types ranged, melee types attack. */
	static String styleIcon(String type)
	{
		String t = type.toLowerCase(java.util.Locale.ROOT);
		if (t.contains("magic") || t.contains("spell"))
		{
			return "magic";
		}
		if (t.contains("ranged") || t.contains("arrow") || t.contains("bolt") || t.contains("dart"))
		{
			return "ranged";
		}
		return "attack";
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
			out.add(new Chip("", "Max hit " + m.getMaxHit(), ChipKind.NORMAL, "hitpoints"));
		}
		if (!m.getAttackStyles().isEmpty())
		{
			List<String> styles = new ArrayList<>();
			for (String st : m.getAttackStyles())
			{
				styles.add(Format.capitalize(st));
			}
			out.add(new Chip("", String.join(", ", styles), ChipKind.NORMAL, null));
		}
		boolean poison = m.isVenomous() || m.isPoisonous();
		out.add(new Chip("", m.isVenomous() ? "Venomous" : m.isPoisonous() ? "Poisonous" : "Not poisonous",
			poison ? ChipKind.BAD : ChipKind.GOOD, null));
		return out;
	}
}
