package com.slayercompanion.gear;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.Monster;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Value;

/** Greedy per-slot best-in-slot search over owned items, plus a "biggest upgrade" suggestion. */
public class GearOptimizer
{
	private static final String[] SLOTS = {"head", "cape", "neck", "ammo", "body", "shield", "legs", "hands", "feet", "ring"};
	private static final String[] MELEE_TYPES = {"stab", "slash", "crush"};
	private static final String[][] SPELLS = {
		{"Wind Strike", "1"}, {"Water Strike", "5"}, {"Earth Strike", "9"}, {"Fire Strike", "13"},
		{"Wind Bolt", "17"}, {"Water Bolt", "23"}, {"Earth Bolt", "29"}, {"Fire Bolt", "35"},
		{"Wind Blast", "41"}, {"Water Blast", "47"}, {"Earth Blast", "53"}, {"Fire Blast", "59"},
		{"Wind Wave", "62"}, {"Water Wave", "65"}, {"Earth Wave", "70"}, {"Fire Wave", "75"},
		{"Wind Surge", "81"}, {"Water Surge", "90"}, {"Earth Surge", "95"}, {"Fire Surge", "99"}};

	@Value
	@AllArgsConstructor
	public static class Upgrade
	{
		Equipment item;
		double dpsGainPercent;
	}

	private final Collection<Equipment> catalogue;

	public GearOptimizer(Collection<Equipment> catalogue)
	{
		this.catalogue = catalogue;
	}

	public Optional<Loadout> best(CombatStats stats, Style style, Monster m, boolean onTask, OwnedItems owned)
	{
		if (!owned.bankSeen())
		{
			return Optional.empty();
		}
		List<Equipment> pool = new ArrayList<>();
		for (Equipment e : catalogue)
		{
			if (e.getSlot() != null && owned.count(e.getItemId()) > 0 && meetsSkills(stats, e))
			{
				pool.add(e);
			}
		}
		// Shield vs two-hander interact, so search each family separately and keep the better.
		Loadout oneHand = search(stats, style, m, onTask, pool, false);
		Loadout twoHand = search(stats, style, m, onTask, pool, true);
		double d1 = tune(stats, oneHand, m, onTask);
		double d2 = tune(stats, twoHand, m, onTask);
		return Optional.of(d2 > d1 + 1e-9 ? twoHand : oneHand);
	}

	private Loadout search(CombatStats stats, Style style, Monster m, boolean onTask, List<Equipment> pool, boolean twoHanded)
	{
		Loadout cur = new Loadout();
		cur.setStyle(style);
		double curDps = tune(stats, cur, m, onTask);
		for (int pass = 0; pass < 3; pass++)
		{
			boolean changed = false;
			// weapon first
			for (Equipment w : pool)
			{
				if (!"weapon".equals(w.getSlot()) || classify(w) != style || w.isTwoHanded() != twoHanded
					|| !ammoOk(cur, w, pool))
				{
					continue;
				}
				Loadout t = with(cur, w, pool);
				double d = tune(stats, t, m, onTask);
				if (d > curDps + 1e-9)
				{
					curDps = d;
					cur = t;
					changed = true;
				}
			}
			for (String slot : SLOTS)
			{
				if (cur.getBySlot().get("weapon") != null && cur.getBySlot().get("weapon").isTwoHanded() && "shield".equals(slot))
				{
					continue;
				}
				for (Equipment e : pool)
				{
					if (!slot.equals(e.getSlot()) || ("ammo".equals(slot) && !ammoCompatible(cur.getBySlot().get("weapon"), e)))
					{
						continue;
					}
					Loadout t = with(cur, e, pool);
					double d = tune(stats, t, m, onTask);
					if (d > curDps + 1e-9)
					{
						curDps = d;
						cur = t;
						changed = true;
					}
				}
			}
			if (!changed)
			{
				break;
			}
		}
		return cur;
	}

	public Optional<Upgrade> biggestUpgrade(CombatStats stats, Set<String> questsDone, Loadout current, Monster m,
		boolean onTask, OwnedItems owned)
	{
		Loadout base = copy(current);
		double baseDps = tune(stats, copy(base), m, onTask);
		Equipment bestItem = null;
		double bestDps = baseDps;
		for (Equipment e : catalogue)
		{
			if (e.getSlot() == null || owned.count(e.getItemId()) > 0 || !meetsSkills(stats, e) || !meetsQuests(questsDone, e))
			{
				continue;
			}
			Equipment curW = base.getBySlot().get("weapon");
			if ("shield".equals(e.getSlot()) && curW != null && curW.isTwoHanded())
			{
				continue;
			}
			if ("weapon".equals(e.getSlot()))
			{
				if (classify(e) != base.getStyle() || !ammoOk(base, e, ownedPool(owned)))
				{
					continue;
				}
			}
			if ("ammo".equals(e.getSlot()) && !ammoCompatible(curW, e))
			{
				continue;
			}
			Loadout t = with(base, e, ownedPool(owned));
			double d = tune(stats, t, m, onTask);
			if (d > bestDps + 1e-9)
			{
				bestDps = d;
				bestItem = e;
			}
		}
		if (bestItem == null)
		{
			return Optional.empty();
		}
		double gain = baseDps <= 0 ? 100.0 : (bestDps - baseDps) / baseDps * 100.0;
		return Optional.of(new Upgrade(bestItem, gain));
	}

	// ---- helpers ----

	/** Picks the best melee type / spell for the loadout in place and returns its dps. */
	private static double tune(CombatStats s, Loadout l, Monster m, boolean onTask)
	{
		switch (l.getStyle())
		{
			case MELEE:
			{
				double best = -1;
				String bestType = "slash";
				for (String t : MELEE_TYPES)
				{
					l.setMeleeType(t);
					double d = DpsCalculator.dps(s, l, m, onTask);
					if (d > best + 1e-9)
					{
						best = d;
						bestType = t;
					}
				}
				l.setMeleeType(bestType);
				return best;
			}
			case MAGIC:
			{
				Equipment w = l.getBySlot().get("weapon");
				if (w != null && w.getName() != null && w.getName().toLowerCase(Locale.ROOT).startsWith("trident"))
				{
					l.setSpell(null);
					return DpsCalculator.dps(s, l, m, onTask);
				}
				double best = -1;
				String bestSpell = null;
				for (String[] sp : SPELLS)
				{
					if (s.getMagic() < Integer.parseInt(sp[1]))
					{
						continue;
					}
					l.setSpell(sp[0]);
					double d = DpsCalculator.dps(s, l, m, onTask);
					if (d > best + 1e-9)
					{
						best = d;
						bestSpell = sp[0];
					}
				}
				l.setSpell(bestSpell);
				return Math.max(best, 0);
			}
			default:
				return DpsCalculator.dps(s, l, m, onTask);
		}
	}

	/** Copy of l with e in its slot; two-handers drop the shield; ammo weapons get the best ammo from pool. */
	private static Loadout with(Loadout l, Equipment e, List<Equipment> pool)
	{
		Loadout t = copy(l);
		t.getBySlot().put(e.getSlot(), e);
		if ("weapon".equals(e.getSlot()))
		{
			if (e.isTwoHanded())
			{
				t.getBySlot().remove("shield");
			}
			String need = ammoNeeded(e);
			if (need == null)
			{
				t.getBySlot().remove("ammo");
			}
			else if (!ammoCompatible(e, t.getBySlot().get("ammo")))
			{
				t.getBySlot().remove("ammo");
				Equipment a = bestAmmo(pool, e);
				if (a != null)
				{
					t.getBySlot().put("ammo", a);
				}
			}
		}
		return t;
	}

	private List<Equipment> ownedPool(OwnedItems owned)
	{
		List<Equipment> pool = new ArrayList<>();
		for (Equipment e : catalogue)
		{
			if (owned.count(e.getItemId()) > 0)
			{
				pool.add(e);
			}
		}
		return pool;
	}

	private static Equipment bestAmmo(List<Equipment> pool, Equipment weapon)
	{
		Equipment best = null;
		for (Equipment a : pool)
		{
			if ("ammo".equals(a.getSlot()) && ammoCompatible(weapon, a) && (best == null
				|| a.getBonuses().getOrDefault("ranged_str", 0) > best.getBonuses().getOrDefault("ranged_str", 0)))
			{
				best = a;
			}
		}
		return best;
	}

	/** True if the weapon can be used with the current ammo, or has/needs a compatible ammo in the pool. */
	private static boolean ammoOk(Loadout cur, Equipment w, List<Equipment> pool)
	{
		if (ammoNeeded(w) == null)
		{
			return true;
		}
		return ammoCompatible(w, cur.getBySlot().get("ammo")) || bestAmmo(pool, w) != null;
	}

	/** Ammo family a weapon fires: arrow, bolt, javelin, ogre; null if it takes no (identifiable) ammo. */
	static String ammoNeeded(Equipment w)
	{
		if (w == null || classify(w) != Style.RANGED)
		{
			return null;
		}
		String c = w.getCategory() == null ? "" : w.getCategory().toLowerCase(Locale.ROOT);
		String n = w.getName() == null ? "" : w.getName().toLowerCase(Locale.ROOT);
		if (n.contains("ballista"))
		{
			return "javelin";
		}
		if (n.contains("ogre bow"))
		{
			return "ogre";
		}
		if (n.contains("crystal bow") || n.contains("faerdhinen") || n.contains("craw's bow") || n.contains("webweaver")
			|| n.contains("venator") || n.contains("atlatl") || n.contains("blowpipe"))
		{
			return null;
		}
		if (c.equals("crossbow"))
		{
			return "bolt";
		}
		if (c.equals("bow"))
		{
			return "arrow";
		}
		return null;
	}

	static String ammoType(Equipment a)
	{
		String n = a.getName() == null ? "" : a.getName().toLowerCase(Locale.ROOT);
		if (n.contains("javelin"))
		{
			return "javelin";
		}
		if (n.contains("brutal") || n.contains("ogre arrow"))
		{
			return "ogre";
		}
		if (n.contains("bolt"))
		{
			return "bolt";
		}
		if (n.contains("arrow"))
		{
			return "arrow";
		}
		return null;
	}

	/** Ammo is only compatible when both families are identified and equal. */
	private static boolean ammoCompatible(Equipment weapon, Equipment ammo)
	{
		String need = ammoNeeded(weapon);
		return need != null && ammo != null && need.equals(ammoType(ammo));
	}

	/** Combat style a weapon is used for; null if unsupported in v1 (e.g. non-trident powered staves). */
	static Style classify(Equipment w)
	{
		String c = w.getCategory() == null ? "" : w.getCategory().toLowerCase(Locale.ROOT);
		String n = w.getName() == null ? "" : w.getName().toLowerCase(Locale.ROOT);
		if (c.equals("powered staff"))
		{
			return n.startsWith("trident of the seas") || n.startsWith("trident of the swamp") ? Style.MAGIC : null;
		}
		if (c.equals("staff") || c.equals("bladed staff"))
		{
			return Style.MAGIC;
		}
		if (c.equals("bow") || c.equals("crossbow") || c.equals("thrown") || c.equals("chinchompas"))
		{
			return Style.RANGED;
		}
		if (c.isEmpty() || c.equals("unarmed") || c.equals("salamander") || c.equals("blaster") || c.equals("gun"))
		{
			return null;
		}
		return Style.MELEE;
	}

	private static Loadout copy(Loadout l)
	{
		return new Loadout(new java.util.HashMap<>(l.getBySlot()), l.getStyle(), l.getMeleeType(), l.getSpell());
	}

	private static boolean meetsSkills(CombatStats s, Equipment e)
	{
		for (java.util.Map.Entry<String, Integer> r : e.getSkillReqs().entrySet())
		{
			int have;
			switch (r.getKey().toLowerCase(Locale.ROOT))
			{
				case "attack": have = s.getAttack(); break;
				case "strength": have = s.getStrength(); break;
				case "defence": have = s.getDefence(); break;
				case "ranged": have = s.getRanged(); break;
				case "magic": have = s.getMagic(); break;
				case "hitpoints": have = s.getHitpoints(); break;
				case "prayer": have = s.getPrayer(); break;
				case "slayer": have = s.getSlayer(); break;
				default: have = Integer.MAX_VALUE; // non-combat skills are not tracked; assume met
			}
			if (have < r.getValue())
			{
				return false;
			}
		}
		return true;
	}

	private static boolean meetsQuests(Set<String> done, Equipment e)
	{
		return done.containsAll(e.getQuestReqs());
	}
}
