package com.slayercompanion.gear;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.Monster;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** DPS maths following the OSRS Wiki DPS calculator; v1 assumes no prayers or boosts. */
public final class DpsCalculator
{
	private static final Map<String, Integer> SPELL_MAX = new HashMap<>();
	private static final String[] ELEMENTS = {"wind", "water", "earth", "fire"};
	private static final String[] TIERS = {"strike", "bolt", "blast", "wave", "surge"};
	private static final int[] TIER_BASE = {2, 9, 13, 17, 21};

	static
	{
		for (int t = 0; t < TIERS.length; t++)
		{
			for (int e = 0; e < ELEMENTS.length; e++)
			{
				int max = t == 0 ? 2 + 2 * e : TIER_BASE[t] + e;
				SPELL_MAX.put(ELEMENTS[e] + " " + TIERS[t], max);
			}
		}
	}

	private DpsCalculator()
	{
	}

	public static int maxHit(CombatStats s, Loadout l, Monster m, boolean onTask)
	{
		switch (l.getStyle())
		{
			case RANGED:
				return rangedMax(s, l, onTask, m);
			case MAGIC:
				return magicMax(s, l, m, onTask);
			default:
				return meleeMax(s, l, m, onTask);
		}
	}

	public static double hitChance(CombatStats s, Loadout l, Monster m, boolean onTask)
	{
		return hitChance(attackRoll(s, l, m, onTask), defenceRoll(l, m));
	}

	public static double dps(CombatStats s, Loadout l, Monster m, boolean onTask)
	{
		return hitChance(s, l, m, onTask) * maxHit(s, l, m, onTask) / 2.0 / (speedTicks(l) * 0.6);
	}

	/** Wiki hit chance from an attack roll and a defence roll. */
	public static double hitChance(long attackRoll, long defenceRoll)
	{
		if (attackRoll > defenceRoll)
		{
			return 1.0 - (defenceRoll + 2.0) / (2.0 * (attackRoll + 1.0));
		}
		return attackRoll / (2.0 * (defenceRoll + 1.0));
	}

	public static int speedTicks(Loadout l)
	{
		Equipment w = l.getBySlot().get("weapon");
		if (l.getStyle() == Style.MAGIC)
		{
			if (l.getSpell() != null)
			{
				return 5;
			}
			return w != null && w.getSpeedTicks() > 0 ? w.getSpeedTicks() : 4;
		}
		return w != null && w.getSpeedTicks() > 0 ? w.getSpeedTicks() : 4;
	}

	// ---- rolls ----

	static long attackRoll(CombatStats s, Loadout l, Monster m, boolean onTask)
	{
		switch (l.getStyle())
		{
			case RANGED:
			{
				long eff = s.getRanged() + 8 + 3;
				long roll = eff * (sum(l, "ranged") + 64);
				return (long) Math.floor(roll * accuracyMult(l, m, onTask));
			}
			case MAGIC:
			{
				long eff = s.getMagic() + 9;
				long roll = eff * (sum(l, "magic") + 64);
				long attack = (long) Math.floor(roll * accuracyMult(l, m, onTask));
				if (weaknessApplies(l, m))
				{
					// Wiki: bonus is a percentage of the unmodified base roll, added last.
					attack += roll * m.getElementalWeaknessPercent() / 100;
				}
				return attack;
			}
			default:
			{
				// Aggressive style: +0 attack, +3 strength.
				long eff = s.getAttack() + 8;
				long roll = eff * (sum(l, meleeType(l)) + 64);
				return (long) Math.floor(roll * accuracyMult(l, m, onTask));
			}
		}
	}

	static long defenceRoll(Loadout l, Monster m)
	{
		Map<String, Integer> d = m.getDefensiveBonuses();
		switch (l.getStyle())
		{
			case RANGED:
			{
				Equipment w = l.getBySlot().get("weapon");
				String key = w != null && "Thrown".equals(w.getCategory()) ? "light" : "standard";
				return (long) (m.getDefenceLevel() + 9) * (d.getOrDefault(key, 0) + 64);
			}
			case MAGIC:
				return (long) (m.getMagicLevel() + 9) * (d.getOrDefault("magic", 0) + 64);
			default:
				return (long) (m.getDefenceLevel() + 9) * (d.getOrDefault(meleeType(l), 0) + 64);
		}
	}

	// ---- max hits ----

	private static int meleeMax(CombatStats s, Loadout l, Monster m, boolean onTask)
	{
		long eff = s.getStrength() + 8 + 3;
		int base = (int) Math.floor(0.5 + eff * (sum(l, "str") + 64) / 640.0);
		return (int) Math.floor(base * damageMult(l, m, onTask));
	}

	private static int rangedMax(CombatStats s, Loadout l, boolean onTask, Monster m)
	{
		long eff = s.getRanged() + 8 + 3;
		int base = (int) Math.floor(0.5 + eff * (sum(l, "ranged_str") + 64) / 640.0);
		return (int) Math.floor(base * damageMult(l, m, onTask));
	}

	private static int magicMax(CombatStats s, Loadout l, Monster m, boolean onTask)
	{
		int base;
		if (l.getSpell() != null)
		{
			Integer b = SPELL_MAX.get(l.getSpell().toLowerCase(Locale.ROOT));
			base = b == null ? 0 : b;
		}
		else
		{
			Equipment w = l.getBySlot().get("weapon");
			String n = w == null || w.getName() == null ? "" : w.getName().toLowerCase(Locale.ROOT);
			if (n.startsWith("trident of the seas"))
			{
				base = s.getMagic() / 3 - 5;
			}
			else if (n.startsWith("trident of the swamp"))
			{
				base = s.getMagic() / 3 - 2;
			}
			else
			{
				return 0;
			}
		}
		if (base <= 0)
		{
			return 0;
		}
		int withBonus = (int) Math.floor(base * (1.0 + sum(l, "magic_str") / 1000.0));
		int max = (int) Math.floor(withBonus * damageMult(l, m, onTask));
		if (weaknessApplies(l, m))
		{
			// Wiki: weakness is added last, as a percentage of the pre-bonus base max.
			max += base * m.getElementalWeaknessPercent() / 100;
		}
		return max;
	}

	private static boolean weaknessApplies(Loadout l, Monster m)
	{
		if (l.getSpell() == null || m.getElementalWeakness() == null)
		{
			return false;
		}
		String first = l.getSpell().toLowerCase(Locale.ROOT).split(" ")[0];
		String element = "wind".equals(first) ? "air" : first;
		return element.equals(m.getElementalWeakness());
	}

	// ---- multipliers ----

	private static double accuracyMult(Loadout l, Monster m, boolean onTask)
	{
		return damageMult(l, m, onTask);
	}

	/** Slayer helm / salve multiplier (best applicable; same value for accuracy and damage). */
	private static double damageMult(Loadout l, Monster m, boolean onTask)
	{
		double best = 1.0;
		boolean melee = l.getStyle() == Style.MELEE;
		Equipment head = l.getBySlot().get("head");
		if (onTask && head != null && head.getName() != null)
		{
			String n = head.getName().toLowerCase(Locale.ROOT);
			if (n.contains("slayer helmet") || n.contains("black mask"))
			{
				boolean imbued = n.contains("(i)");
				if (melee)
				{
					best = 7.0 / 6.0;
				}
				else if (imbued)
				{
					best = 1.15;
				}
			}
		}
		Equipment neck = l.getBySlot().get("neck");
		if (m.isUndead() && neck != null && neck.getName() != null)
		{
			String n = neck.getName().toLowerCase(Locale.ROOT).replace(" ", "");
			if (n.startsWith("salveamulet"))
			{
				boolean imbued = n.contains("(i)") || n.contains("(ei)");
				boolean enhanced = n.contains("(e)") || n.contains("(ei)");
				double salve = 1.0;
				if (melee)
				{
					salve = enhanced ? 1.2 : 7.0 / 6.0;
				}
				else if (imbued)
				{
					salve = enhanced ? 1.2 : 1.15;
				}
				best = Math.max(best, salve);
			}
		}
		return best;
	}

	// ---- helpers ----

	private static String meleeType(Loadout l)
	{
		return l.getMeleeType() == null ? "slash" : l.getMeleeType();
	}

	private static int sum(Loadout l, String key)
	{
		int total = 0;
		for (Equipment e : l.getBySlot().values())
		{
			if (e != null && e.getBonuses() != null)
			{
				total += e.getBonuses().getOrDefault(key, 0);
			}
		}
		return total;
	}
}
