package com.slayercompanion.gear;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.Monster;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * DPS maths following the OSRS Wiki DPS calculator, integer rounding in its order. Assumed prayer and potion come from
 * {@link Boosts}, armour sets from {@link SetBonuses}, monster-specific weapon rules (immunities, dragon hunter,
 * demonbane, ...) from {@link WeaponEffects}.
 */
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

	/** Top hit as shown in game, after per-hit effects such as the berserker necklace. */
	public static int maxHit(CombatStats s, Loadout l, Monster m, boolean onTask)
	{
		return SetBonuses.shownMax(l, rawMax(s, l, m, onTask));
	}

	/** Max hit before per-hit set effects (the top of the uniform roll). */
	private static int rawMax(CombatStats s, Loadout l, Monster m, boolean onTask)
	{
		if (WeaponEffects.isImmune(l, m))
		{
			return 0;
		}
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
		if (WeaponEffects.isImmune(l, m))
		{
			return 0;
		}
		double hit = SetBonuses.expectedHit(l, hitChance(s, l, m, onTask), rawMax(s, l, m, onTask));
		return hit / (speedTicks(l) * 0.6);
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
				// Accurate style: +3.
				long eff = Boosts.prayAccuracy(Boosts.ranged(s, Style.RANGED), Boosts.prayer(s, Style.RANGED)) + 3 + 8;
				if (SetBonuses.rangedVoid(l))
				{
					eff = eff * 11 / 10;
				}
				long roll = eff * (sum(l, "ranged") + 64);
				roll = roll * (20 + SetBonuses.crystalPieces(l)) / 20;
				return WeaponEffects.applyAccuracy(scale(roll, accuracyMult(l, m, onTask)), l, m);
			}
			case MAGIC:
			{
				long eff = Boosts.prayAccuracy(Boosts.magic(s, Style.MAGIC), Boosts.prayer(s, Style.MAGIC)) + 9;
				if (SetBonuses.magicVoid(l))
				{
					eff = eff * 29 / 20;
				}
				long roll = eff * (sum(l, "magic") + 64);
				long attack = WeaponEffects.applyAccuracy(scale(roll, accuracyMult(l, m, onTask)), l, m);
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
				long eff = Boosts.prayAccuracy(Boosts.attack(s, Style.MELEE), Boosts.prayer(s, Style.MELEE)) + 8;
				if (SetBonuses.meleeVoid(l))
				{
					eff = eff * 11 / 10;
				}
				long base = eff * (sum(l, meleeType(l)) + 64);
				long roll = scale(base, accuracyMult(l, m, onTask));
				if (SetBonuses.obsidian(l))
				{
					roll += base / 10;
				}
				roll = WeaponEffects.applyAccuracy(roll, l, m);
				return roll * (200 + SetBonuses.inquisitorBonus(l)) / 200;
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
		long eff = Boosts.prayStrength(Boosts.strength(s, Style.MELEE), Boosts.prayer(s, Style.MELEE)) + 3 + 8;
		if (SetBonuses.meleeVoid(l))
		{
			eff = eff * 11 / 10;
		}
		int base = (int) ((eff * (sum(l, "str") + 64) + 320) / 640);
		int max = (int) scale(base, damageMult(l, m, onTask));
		if (SetBonuses.obsidian(l))
		{
			max += base / 10;
		}
		max = WeaponEffects.applyMaxHit(max, l, m, false);
		return max * (200 + SetBonuses.inquisitorBonus(l)) / 200;
	}

	private static int rangedMax(CombatStats s, Loadout l, boolean onTask, Monster m)
	{
		long eff = Boosts.prayStrength(Boosts.ranged(s, Style.RANGED), Boosts.prayer(s, Style.RANGED)) + 3 + 8;
		if (SetBonuses.eliteRangedVoid(l))
		{
			eff = eff * 9 / 8;
		}
		else if (SetBonuses.rangedVoid(l))
		{
			eff = eff * 11 / 10;
		}
		int base = (int) ((eff * (sum(l, "ranged_str") + 64) + 320) / 640);
		// Calculator (tested in game): the crystal armour bonus comes before the slayer helm.
		base = base * (40 + SetBonuses.crystalPieces(l)) / 40;
		// Wiki: with an imbued slayer helm, dragon hunter crossbow / scorching bow add to its 23/20 factor.
		int addend = WeaponEffects.rangedHelmAddend(l, m);
		boolean inHelm = addend > 0 && salveMult(l, m) == 1.0 && slayerHelmMult(l, onTask) > 1.0;
		int max = inHelm ? base * (23 + addend) / 20 : (int) scale(base, damageMult(l, m, onTask));
		return WeaponEffects.applyMaxHit(max, l, m, inHelm);
	}

	private static int magicMax(CombatStats s, Loadout l, Monster m, boolean onTask)
	{
		int base;
		if (WeaponEffects.MAGIC_DART.equalsIgnoreCase(l.getSpell()))
		{
			base = magicDartBase(Boosts.magic(s, Style.MAGIC), l, onTask);
		}
		else if (l.getSpell() != null)
		{
			Integer b = SPELL_MAX.get(l.getSpell().toLowerCase(Locale.ROOT));
			base = b == null ? 0 : b;
		}
		else
		{
			int magic = Boosts.magic(s, Style.MAGIC);
			Equipment w = l.getBySlot().get("weapon");
			String n = w == null || w.getName() == null ? "" : w.getName().toLowerCase(Locale.ROOT);
			if (n.startsWith("trident of the seas"))
			{
				base = magic / 3 - 5;
			}
			else if (n.startsWith("trident of the swamp"))
			{
				base = magic / 3 - 2;
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
		// Gear, prayer and elite void magic damage add together, in tenths of a percent.
		int damage = sum(l, "magic_str") + Boosts.prayer(s, Style.MAGIC).getMagicDamage() + SetBonuses.magicDamageBonus(l);
		int boosted = WeaponEffects.applyMagicBase(base, l, m);
		int withBonus = boosted + boosted * damage / 1000;
		int max = WeaponEffects.applyMaxHit((int) scale(withBonus, damageMult(l, m, onTask)), l, m, false);
		if (weaknessApplies(l, m))
		{
			// Wiki: weakness is added last, as a percentage of the pre-bonus base max.
			max += base * m.getElementalWeaknessPercent() / 100;
		}
		return max;
	}

	/** Wiki: floor(magic / 10) + 10, or floor(magic / 6) + 13 with a Slayer's staff (e) on task; 0 without a valid staff. */
	private static int magicDartBase(int magic, Loadout l, boolean onTask)
	{
		Equipment w = l.getBySlot().get("weapon");
		if (w == null || !WeaponEffects.canCastMagicDart(w))
		{
			return 0;
		}
		boolean enchanted = w.getName().toLowerCase(Locale.ROOT).startsWith("slayer's staff (e)");
		return enchanted && onTask ? 13 + magic / 6 : 10 + magic / 10;
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

	/** Salve beats the slayer helm (they do not stack); same value for accuracy and damage. */
	private static double damageMult(Loadout l, Monster m, boolean onTask)
	{
		double salve = salveMult(l, m);
		return salve > 1.0 ? salve : slayerHelmMult(l, onTask);
	}

	/** Slayer helmet / black mask on task: 7/6 melee; imbued 1.15 ranged and magic. */
	private static double slayerHelmMult(Loadout l, boolean onTask)
	{
		Equipment head = l.getBySlot().get("head");
		if (!onTask || head == null || head.getName() == null)
		{
			return 1.0;
		}
		String n = head.getName().toLowerCase(Locale.ROOT);
		if (!n.contains("slayer helmet") && !n.contains("black mask"))
		{
			return 1.0;
		}
		if (l.getStyle() == Style.MELEE)
		{
			return 7.0 / 6.0;
		}
		return n.contains("(i)") ? 1.15 : 1.0;
	}

	/** Salve amulet vs undead: 7/6 (1.2 enhanced) melee; imbued versions also for ranged and magic. */
	private static double salveMult(Loadout l, Monster m)
	{
		Equipment neck = l.getBySlot().get("neck");
		if (!m.isUndead() || neck == null || neck.getName() == null)
		{
			return 1.0;
		}
		String n = neck.getName().toLowerCase(Locale.ROOT).replace(" ", "");
		if (!n.startsWith("salveamulet"))
		{
			return 1.0;
		}
		boolean imbued = n.contains("(i)") || n.contains("(ei)");
		boolean enhanced = n.contains("(e)") || n.contains("(ei)");
		if (l.getStyle() == Style.MELEE)
		{
			return enhanced ? 1.2 : 7.0 / 6.0;
		}
		if (imbued)
		{
			return enhanced ? 1.2 : 1.15;
		}
		return 1.0;
	}

	// ---- helpers ----

	/**
	 * floor(v * mult) for the calculator's integer fractions (7/6, 23/20, 6/5): the nudge stops 26400 * 1.15 landing on
	 * 30359.999...; a real fraction of these is at least 1/20 off a whole number, so it is never rounded up.
	 */
	private static long scale(long v, double mult)
	{
		return mult == 1.0 ? v : (long) Math.floor(v * mult + 1e-6);
	}

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
