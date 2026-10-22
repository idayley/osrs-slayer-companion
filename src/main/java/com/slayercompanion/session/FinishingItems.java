package com.slayercompanion.session;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.api.ItemID;

/**
 * Consumable items that each kill needs to finish the monster off, and how many kills the carried ones cover.
 * <ul>
 * <li>Lizards (desert, small, large): one Ice cooler per kill.</li>
 * <li>Rockslugs (and giant rockslugs): one Bag of salt per kill.</li>
 * <li>Mutated and ancient zygomites: one Fungicide spray charge per kill. A spray (n) holds n charges;
 * each Fungicide refill tops a spray up by 10, so refills only count while a spray is carried.</li>
 * </ul>
 * Tools that are not used up (rock hammer / granite hammer for gargoyles) are skipped.
 * <p>
 * Slayer unlocks "Reptile freezer", "Slug salter" and "Shroom sprayer" apply the item automatically, but it is
 * still consumed, so the counts here hold with or without them. "Gargoyle smasher" covers the hammer tool.
 */
public final class FinishingItems
{
	private static final int[] SPRAYS = {
		ItemID.FUNGICIDE_SPRAY_0, ItemID.FUNGICIDE_SPRAY_1, ItemID.FUNGICIDE_SPRAY_2, ItemID.FUNGICIDE_SPRAY_3,
		ItemID.FUNGICIDE_SPRAY_4, ItemID.FUNGICIDE_SPRAY_5, ItemID.FUNGICIDE_SPRAY_6, ItemID.FUNGICIDE_SPRAY_7,
		ItemID.FUNGICIDE_SPRAY_8, ItemID.FUNGICIDE_SPRAY_9, ItemID.FUNGICIDE_SPRAY_10,
	};
	/** Charges one Fungicide refill adds to a spray. */
	static final int REFILL_CHARGES = 10;

	public static final Need ICE_COOLER = new Need("Ice coolers", "Ice cooler",
		Map.of(ItemID.ICE_COOLER, 1), Set.of());
	public static final Need BAG_OF_SALT = new Need("Bags of salt", "Bag of salt",
		Map.of(ItemID.BAG_OF_SALT, 1), Set.of());
	public static final Need FUNGICIDE = new Need("Fungicide charges", "Fungicide charge",
		fungicideUses(), Set.of(ItemID.FUNGICIDE));

	private static final Map<String, Need> BY_NAME = new HashMap<>();

	static
	{
		for (String n : new String[]{"lizards", "lizard", "desert lizards", "desert lizard", "small lizards",
			"small lizard", "large lizards", "large lizard"})
		{
			BY_NAME.put(n, ICE_COOLER);
		}
		for (String n : new String[]{"rockslugs", "rockslug", "giant rockslugs", "giant rockslug"})
		{
			BY_NAME.put(n, BAG_OF_SALT);
		}
		for (String n : new String[]{"mutated zygomites", "mutated zygomite", "zygomites", "zygomite",
			"ancient zygomites", "ancient zygomite"})
		{
			BY_NAME.put(n, FUNGICIDE);
		}
	}

	private FinishingItems()
	{
	}

	private static Map<Integer, Integer> fungicideUses()
	{
		Map<Integer, Integer> m = new LinkedHashMap<>();
		for (int charges = 0; charges < SPRAYS.length; charges++)
		{
			m.put(SPRAYS[charges], charges);
		}
		m.put(ItemID.FUNGICIDE, REFILL_CHARGES);
		return m;
	}

	/** The finishing item a task needs, by task name (e.g. "Mutated Zygomites"); empty if none. */
	public static Optional<Need> forTask(String taskName)
	{
		if (taskName == null)
		{
			return Optional.empty();
		}
		return Optional.ofNullable(BY_NAME.get(taskName.trim().toLowerCase(Locale.ROOT)));
	}

	/** The finishing item a monster needs, by NPC name (e.g. "Desert Lizard"); empty if none. */
	public static Optional<Need> forMonster(String npcName)
	{
		return forTask(npcName);
	}

	/** A consumable finishing item, possibly spread over several item ids (spray charges, refills). */
	public static final class Need
	{
		private final String label;
		private final String singular;
		private final Map<Integer, Integer> usesPerItem;
		/** Items that only count while some other item of this need is carried (refills need a spray). */
		private final Set<Integer> needsHolder;

		Need(String label, String singular, Map<Integer, Integer> usesPerItem, Set<Integer> needsHolder)
		{
			this.label = label;
			this.singular = singular;
			this.usesPerItem = Collections.unmodifiableMap(usesPerItem);
			this.needsHolder = needsHolder;
		}

		/** Plural label, e.g. "Ice coolers". */
		public String getLabel()
		{
			return label;
		}

		/** Item id to kills one of that item covers. */
		public Map<Integer, Integer> getUsesPerItem()
		{
			return usesPerItem;
		}

		/** Kills the carried items cover. */
		public int usesCarried(SupplyTracker t)
		{
			return usesCarried(t::count);
		}

		/** Kills covered by carried counts (item id to quantity). */
		public int usesCarried(Map<Integer, Integer> counts)
		{
			return usesCarried(id -> counts.getOrDefault(id, 0));
		}

		private int usesCarried(IntUnaryOperator count)
		{
			boolean holder = false;
			for (int id : usesPerItem.keySet())
			{
				if (!needsHolder.contains(id) && count.applyAsInt(id) > 0)
				{
					holder = true;
					break;
				}
			}
			int uses = 0;
			for (Map.Entry<Integer, Integer> e : usesPerItem.entrySet())
			{
				if (needsHolder.contains(e.getKey()) && !holder)
				{
					continue;
				}
				uses += Math.max(0, count.applyAsInt(e.getKey())) * e.getValue();
			}
			return uses;
		}

		/** Status line, e.g. "23 ice coolers · 40 left"; empty once no kills are left. */
		public Optional<String> line(int usesCarried, int killsLeft)
		{
			if (killsLeft <= 0)
			{
				return Optional.empty();
			}
			String what = (usesCarried == 1 ? singular : label).toLowerCase(Locale.ROOT);
			String have = usesCarried <= 0 ? "No " + what + " carried" : usesCarried + " " + what;
			return Optional.of(have + " · " + killsLeft + " left");
		}

		/** Warning when the carried items cover fewer kills than are left, e.g.
		 * "Ice coolers run out ~17 kills before task end"; empty otherwise. */
		public Optional<String> warning(int usesCarried, int killsLeft)
		{
			int shortBy = killsLeft - Math.max(0, usesCarried);
			if (shortBy <= 0)
			{
				return Optional.empty();
			}
			if (usesCarried <= 0)
			{
				return Optional.of("No " + label.toLowerCase(Locale.ROOT) + " carried · needed to finish kills");
			}
			return Optional.of(label + " run out ~" + shortBy + (shortBy == 1 ? " kill" : " kills")
				+ " before task end");
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
