package com.slayercompanion.gear;

import com.slayercompanion.data.Equipment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Armour set bonuses as the OSRS Wiki DPS calculator models them: Void / Elite void, obsidian with Tzhaar weapons,
 * berserker necklace, Inquisitor's (crush), crystal armour with a crystal bow or Bow of Faerdhinen, and the Verac's,
 * Karil's and Ahrim's set effects. Dharok's assumes full Hitpoints (no bonus); Torag's and Guthan's do not change dps.
 * Names match by prefix, so "(or)", "(Normal)" and charge suffixes count; broken pieces never do.
 */
public final class SetBonuses
{
	private static final String[] VOID_TOP = {"void knight top", "elite void top"};
	private static final String[] VOID_ROBE = {"void knight robe", "elite void robe"};
	private static final String[] ELITE_TOP = {"elite void top"};
	private static final String[] ELITE_ROBE = {"elite void robe"};
	private static final String[] VOID_GLOVES = {"void knight gloves"};
	private static final String[] TZHAAR_WEAPONS = {"tzhaar-ket-em", "tzhaar-ket-om", "toktz-xil-ak", "toktz-xil-ek",
		"toktz-mej-tal"};
	private static final String DAMNED = "amulet of the damned";

	/** A set the optimizer can start from: slot -> name prefixes, best variant first. */
	static final class Kit
	{
		final Style style;
		final Map<String, String[]> pieces = new HashMap<>();

		Kit(Style style, Object... slotPrefixes)
		{
			this.style = style;
			for (int i = 0; i < slotPrefixes.length; i += 2)
			{
				pieces.put((String) slotPrefixes[i], (String[]) slotPrefixes[i + 1]);
			}
		}
	}

	private static final String[] ELITE_FIRST_TOP = {"elite void top", "void knight top"};
	private static final String[] ELITE_FIRST_ROBE = {"elite void robe", "void knight robe"};

	/** Sets whose bonus needs several pieces at once, which one-slot-at-a-time swaps cannot find. */
	static final List<Kit> KITS = Collections.unmodifiableList(Arrays.asList(
		voidKit(Style.MELEE, "void melee helm"),
		voidKit(Style.RANGED, "void ranger helm"),
		voidKit(Style.MAGIC, "void mage helm"),
		new Kit(Style.MELEE, "head", new String[]{"obsidian helmet"}, "body", new String[]{"obsidian platebody"},
			"legs", new String[]{"obsidian platelegs"}),
		new Kit(Style.MELEE, "head", new String[]{"inquisitor's great helm"}, "body", new String[]{"inquisitor's hauberk"},
			"legs", new String[]{"inquisitor's plateskirt"}),
		new Kit(Style.RANGED, "head", new String[]{"crystal helm"}, "body", new String[]{"crystal body"},
			"legs", new String[]{"crystal legs"}),
		new Kit(Style.MELEE, "weapon", new String[]{"verac's flail"}, "head", new String[]{"verac's helm"},
			"body", new String[]{"verac's brassard"}, "legs", new String[]{"verac's plateskirt"}),
		new Kit(Style.RANGED, "weapon", new String[]{"karil's crossbow"}, "head", new String[]{"karil's coif"},
			"body", new String[]{"karil's leathertop"}, "legs", new String[]{"karil's leatherskirt"},
			"neck", new String[]{DAMNED}),
		new Kit(Style.MAGIC, "weapon", new String[]{"ahrim's staff"}, "head", new String[]{"ahrim's hood"},
			"body", new String[]{"ahrim's robetop"}, "legs", new String[]{"ahrim's robeskirt"},
			"neck", new String[]{DAMNED})));

	private SetBonuses()
	{
	}

	private static Kit voidKit(Style style, String helm)
	{
		return new Kit(style, "head", new String[]{helm}, "body", ELITE_FIRST_TOP, "legs", ELITE_FIRST_ROBE,
			"hands", VOID_GLOVES);
	}

	// ---- void ----

	static boolean meleeVoid(Loadout l)
	{
		return voidRobes(l) && wears(l, "head", "void melee helm");
	}

	static boolean rangedVoid(Loadout l)
	{
		return voidRobes(l) && wears(l, "head", "void ranger helm");
	}

	static boolean eliteRangedVoid(Loadout l)
	{
		return rangedVoid(l) && eliteRobes(l);
	}

	static boolean magicVoid(Loadout l)
	{
		return voidRobes(l) && wears(l, "head", "void mage helm");
	}

	/** Elite magic void: +5% magic damage, in tenths of a percent like the gear bonus. */
	static int magicDamageBonus(Loadout l)
	{
		return magicVoid(l) && eliteRobes(l) ? 50 : 0;
	}

	private static boolean voidRobes(Loadout l)
	{
		return wears(l, "body", VOID_TOP) && wears(l, "legs", VOID_ROBE) && wears(l, "hands", VOID_GLOVES);
	}

	private static boolean eliteRobes(Loadout l)
	{
		return wears(l, "body", ELITE_TOP) && wears(l, "legs", ELITE_ROBE);
	}

	// ---- obsidian / berserker ----

	/** Full obsidian armour with a Tzhaar weapon: + floor(base / 10) to the base roll and base max hit. */
	static boolean obsidian(Loadout l)
	{
		return tzhaarWeapon(l) && wears(l, "head", "obsidian helmet") && wears(l, "body", "obsidian platebody")
			&& wears(l, "legs", "obsidian platelegs");
	}

	/** Berserker necklace with a Tzhaar weapon: every melee hit x 6/5. */
	static boolean berserker(Loadout l)
	{
		return l.getStyle() == Style.MELEE && tzhaarWeapon(l) && wears(l, "neck", "berserker necklace");
	}

	private static boolean tzhaarWeapon(Loadout l)
	{
		return wears(l, "weapon", TZHAAR_WEAPONS);
	}

	// ---- inquisitor / crystal ----

	/** Inquisitor's on crush: helm 1, hauberk 2, plateskirt 2, over 200; 0 otherwise. */
	static int inquisitorBonus(Loadout l)
	{
		if (l.getStyle() != Style.MELEE || !"crush".equals(l.getMeleeType()))
		{
			return 0;
		}
		return (wears(l, "head", "inquisitor's great helm") ? 1 : 0) + (wears(l, "body", "inquisitor's hauberk") ? 2 : 0)
			+ (wears(l, "legs", "inquisitor's plateskirt") ? 2 : 0);
	}

	/** Crystal helm 1, legs 2, body 3 with a crystal bow or Bow of Faerdhinen; 0 otherwise. */
	static int crystalPieces(Loadout l)
	{
		if (l.getStyle() != Style.RANGED || !wears(l, "weapon", "crystal bow", "bow of faerdhinen"))
		{
			return 0;
		}
		return (wears(l, "head", "crystal helm") ? 1 : 0) + (wears(l, "legs", "crystal legs") ? 2 : 0)
			+ (wears(l, "body", "crystal body") ? 3 : 0);
	}

	// ---- barrows ----

	private static boolean veracs(Loadout l)
	{
		return l.getStyle() == Style.MELEE && wears(l, "weapon", "verac's flail") && wears(l, "head", "verac's helm")
			&& wears(l, "body", "verac's brassard") && wears(l, "legs", "verac's plateskirt");
	}

	private static boolean karils(Loadout l)
	{
		return l.getStyle() == Style.RANGED && wears(l, "weapon", "karil's crossbow") && wears(l, "head", "karil's coif")
			&& wears(l, "body", "karil's leathertop") && wears(l, "legs", "karil's leatherskirt") && wears(l, "neck", DAMNED);
	}

	private static boolean ahrims(Loadout l)
	{
		return l.getStyle() == Style.MAGIC && l.getSpell() != null && wears(l, "weapon", "ahrim's staff")
			&& wears(l, "head", "ahrim's hood") && wears(l, "body", "ahrim's robetop") && wears(l, "legs", "ahrim's robeskirt")
			&& wears(l, "neck", DAMNED);
	}

	// ---- per-hit effects ----

	/** Max hit as shown: the berserker necklace scales every hit, so its top hit too. */
	static int shownMax(Loadout l, int max)
	{
		return berserker(l) ? max * 6 / 5 : max;
	}

	/**
	 * Average damage per attack from the hit chance and the max hit before per-hit effects, following the
	 * calculator's hit distributions (uniform 0..max on an accurate hit, then set effects).
	 */
	static double expectedHit(Loadout l, double hitChance, int max)
	{
		if (berserker(l))
		{
			return hitChance * mean(max, 6, 5, 0);
		}
		if (veracs(l))
		{
			// 25% of attacks ignore accuracy and hit 1..max+1.
			return 0.75 * hitChance * max / 2.0 + 0.25 * (max + 2) / 2.0;
		}
		if (karils(l))
		{
			// 25% of accurate hits add a second hitsplat of half the first.
			return hitChance * (max / 2.0 + 0.25 * mean(max, 1, 2, 0));
		}
		if (ahrims(l))
		{
			// 25% of hits deal 30% more.
			return hitChance * (0.75 * max / 2.0 + 0.25 * mean(max, 13, 10, 0));
		}
		return hitChance * max / 2.0;
	}

	/** Mean of floor(d * num / den) + add over d = 0..max. */
	private static double mean(int max, int num, int den, int add)
	{
		long total = 0;
		for (int d = 0; d <= max; d++)
		{
			total += d * num / den + add;
		}
		return (double) total / (max + 1);
	}

	// ---- optimizer seeds ----

	/** Owned, wearable kits for the style as slot -> item, best variant per slot; empty when none is complete. */
	static List<Map<String, Equipment>> ownedKits(Style style, List<Equipment> pool)
	{
		List<Map<String, Equipment>> out = new ArrayList<>();
		for (Kit k : KITS)
		{
			if (k.style != style)
			{
				continue;
			}
			Map<String, Equipment> found = complete(k, pool, null);
			if (found != null)
			{
				out.add(found);
			}
		}
		return out;
	}

	/**
	 * The other owned pieces of each kit that {@code item} belongs to, so an upgrade that completes a set is valued
	 * with the rest of it; one map per completable kit.
	 */
	static List<Map<String, Equipment>> kitsCompletedBy(Equipment item, Style style, List<Equipment> pool)
	{
		List<Map<String, Equipment>> out = new ArrayList<>();
		for (Kit k : KITS)
		{
			String[] prefixes = k.pieces.get(item.getSlot());
			if (k.style != style || prefixes == null || !matches(item, prefixes))
			{
				continue;
			}
			Map<String, Equipment> found = complete(k, pool, item);
			if (found != null)
			{
				out.add(found);
			}
		}
		return out;
	}

	/** Slot -> the best-variant pool item for every piece of the kit ({@code fixed} fills its own slot); else null. */
	private static Map<String, Equipment> complete(Kit k, List<Equipment> pool, Equipment fixed)
	{
		Map<String, Equipment> found = new HashMap<>();
		for (Map.Entry<String, String[]> piece : k.pieces.entrySet())
		{
			String slot = piece.getKey();
			Equipment e = fixed != null && slot.equals(fixed.getSlot()) ? fixed : bestVariant(pool, slot, piece.getValue());
			if (e == null)
			{
				return null;
			}
			found.put(slot, e);
		}
		return found;
	}

	/** First prefix with an owned wearable match wins (elite void before plain void). */
	private static Equipment bestVariant(List<Equipment> pool, String slot, String[] prefixes)
	{
		for (String p : prefixes)
		{
			for (Equipment e : pool)
			{
				if (slot.equals(e.getSlot()) && matches(e, p))
				{
					return e;
				}
			}
		}
		return null;
	}

	// ---- name matching ----

	private static boolean wears(Loadout l, String slot, String... prefixes)
	{
		return matches(l.getBySlot().get(slot), prefixes);
	}

	private static boolean matches(Equipment e, String... prefixes)
	{
		if (e == null || e.getName() == null)
		{
			return false;
		}
		String n = e.getName().toLowerCase(Locale.ROOT);
		// Broken void and fully degraded barrows pieces cannot be worn.
		if (n.contains("(broken)") || n.endsWith(" (0)"))
		{
			return false;
		}
		for (String p : prefixes)
		{
			if (n.startsWith(p))
			{
				return true;
			}
		}
		return false;
	}
}
