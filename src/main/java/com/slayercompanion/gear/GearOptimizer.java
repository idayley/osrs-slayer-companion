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
		Loadout cur = new Loadout();
		cur.setStyle(style);
		double curDps = tune(stats, cur, m, onTask);
		for (int pass = 0; pass < 3; pass++)
		{
			boolean changed = false;
			// weapon first
			for (Equipment w : pool)
			{
				if (!"weapon".equals(w.getSlot()) || classify(w) != style || !ammoOk(cur, w, pool))
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
					if (!slot.equals(e.getSlot()))
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
		return Optional.of(cur);
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
				if (classify(e) != base.getStyle() || !ammoOk(base, e, new ArrayList<>(base.getBySlot().values())))
				{
					continue;
				}
			}
			Loadout t = with(base, e, new ArrayList<>());
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
			if (!usesAmmo(e))
			{
				t.getBySlot().remove("ammo");
			}
			else if (t.getBySlot().get("ammo") == null)
			{
				Equipment a = bestAmmo(pool);
				if (a != null)
				{
					t.getBySlot().put("ammo", a);
				}
			}
		}
		return t;
	}

	private static Equipment bestAmmo(List<Equipment> pool)
	{
		Equipment best = null;
		for (Equipment a : pool)
		{
			if ("ammo".equals(a.getSlot()) && (best == null
				|| a.getBonuses().getOrDefault("ranged_str", 0) > best.getBonuses().getOrDefault("ranged_str", 0)))
			{
				best = a;
			}
		}
		return best;
	}

	private static boolean ammoOk(Loadout cur, Equipment w, List<Equipment> pool)
	{
		if (!usesAmmo(w))
		{
			return true;
		}
		return cur.getBySlot().get("ammo") != null || bestAmmo(pool) != null;
	}

	private static boolean usesAmmo(Equipment w)
	{
		String c = w.getCategory() == null ? "" : w.getCategory().toLowerCase(Locale.ROOT);
		return c.contains("bow");
	}

	static Style classify(Equipment w)
	{
		String c = w.getCategory() == null ? "" : w.getCategory().toLowerCase(Locale.ROOT);
		String n = w.getName() == null ? "" : w.getName().toLowerCase(Locale.ROOT);
		if (c.contains("staff") || c.contains("wand") || n.startsWith("trident"))
		{
			return Style.MAGIC;
		}
		if (c.contains("bow") || c.contains("thrown") || c.contains("chinchompa") || n.contains("blowpipe"))
		{
			return Style.RANGED;
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
