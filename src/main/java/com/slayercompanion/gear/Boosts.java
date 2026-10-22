package com.slayercompanion.gear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * Prayer and potion the maths assumes when {@link CombatStats#isAssumeBoosts()}: the best offensive prayer the player
 * can use for the style, and the standard potion for it (super combat, ranging potion, magic potion) whether or not
 * it is in the bank. Factors and potion formulas are the OSRS Wiki DPS calculator's (enums/Prayer.ts, utils.ts).
 */
public final class Boosts
{
	/** An offensive prayer, or a melee strength + attack pair, with the calculator's factors over 100. */
	@Value
	public static class Prayer
	{
		List<String> names;
		int accuracy;
		int strength;
		/** Added to the gear's magic damage bonus, in tenths of a percent. */
		int magicDamage;

		/** "Piety", "Ultimate Strength and Incredible Reflexes", or "none". */
		public String getName()
		{
			return names.isEmpty() ? "none" : String.join(" and ", names);
		}
	}

	static final Prayer NONE = new Prayer(Collections.emptyList(), 100, 100, 0);

	/** Prayer level, factor; best first. */
	private static final Object[][] MELEE_STRENGTH = {
		{"Ultimate Strength", 31, 115}, {"Superhuman Strength", 13, 110}, {"Burst of Strength", 4, 105}};
	private static final Object[][] MELEE_ATTACK = {
		{"Incredible Reflexes", 34, 115}, {"Improved Reflexes", 16, 110}, {"Clarity of Thought", 7, 105}};
	private static final Object[][] RANGED = {{"Eagle Eye", 44, 115}, {"Hawk Eye", 26, 110}, {"Sharp Eye", 8, 105}};
	/** Magic prayers also carry their magic damage bonus (tenths of a percent). */
	private static final Object[][] MAGIC = {
		{"Mystic Might", 45, 115, 20}, {"Mystic Lore", 27, 110, 10}, {"Mystic Will", 9, 105, 0}};

	private Boosts()
	{
	}

	/** Best offensive prayer for the style that the levels and unlocks allow; {@link #NONE} when boosts are off. */
	public static Prayer prayer(CombatStats s, Style style)
	{
		if (!s.isAssumeBoosts())
		{
			return NONE;
		}
		int p = s.getPrayer();
		int def = s.getDefence();
		switch (style)
		{
			case RANGED:
				if (s.isRigourUnlocked() && p >= 74 && def >= 70)
				{
					return single("Rigour", 120, 123, 0);
				}
				if (s.isDeadeyeUnlocked() && p >= 62)
				{
					return single("Deadeye", 118, 118, 0);
				}
				for (Object[] r : RANGED)
				{
					if (p >= (int) r[1])
					{
						return single((String) r[0], (int) r[2], (int) r[2], 0);
					}
				}
				return NONE;
			case MAGIC:
				if (s.isAuguryUnlocked() && p >= 77 && def >= 70)
				{
					return single("Augury", 125, 100, 40);
				}
				if (s.isMysticVigourUnlocked() && p >= 63)
				{
					return single("Mystic Vigour", 118, 100, 30);
				}
				for (Object[] r : MAGIC)
				{
					if (p >= (int) r[1])
					{
						return single((String) r[0], (int) r[2], 100, (int) r[3]);
					}
				}
				return NONE;
			default:
				if (s.isPietyUnlocked() && p >= 70 && def >= 70)
				{
					return single("Piety", 120, 123, 0);
				}
				if (s.isPietyUnlocked() && p >= 60 && def >= 65)
				{
					return single("Chivalry", 115, 118, 0);
				}
				List<String> names = new ArrayList<>();
				int str = best(MELEE_STRENGTH, p, names);
				int att = best(MELEE_ATTACK, p, names);
				return names.isEmpty() ? NONE : new Prayer(names, att, str, 0);
		}
	}

	/** "Assumes Piety and a super combat"; null when boosts are off. */
	public static String note(CombatStats s, Style style)
	{
		if (!s.isAssumeBoosts())
		{
			return null;
		}
		List<String> parts = new ArrayList<>(prayer(s, style).getNames());
		parts.add(potionName(style));
		String last = parts.remove(parts.size() - 1);
		return "Assumes " + (parts.isEmpty() ? "" : String.join(", ", parts) + " and ") + last;
	}

	static String potionName(Style style)
	{
		switch (style)
		{
			case RANGED:
				return "a ranging potion";
			case MAGIC:
				return "a magic potion";
			default:
				return "a super combat";
		}
	}

	// ---- visible levels ----

	/** Attack after a super combat (melee only): + floor(5 + 15%). */
	static int attack(CombatStats s, Style style)
	{
		return s.getAttack() + (boosting(s, style, Style.MELEE) ? superCombat(s.getAttack()) : 0);
	}

	static int strength(CombatStats s, Style style)
	{
		return s.getStrength() + (boosting(s, style, Style.MELEE) ? superCombat(s.getStrength()) : 0);
	}

	/** Ranged after a ranging potion: + floor(4 + 10%). */
	static int ranged(CombatStats s, Style style)
	{
		return s.getRanged() + (boosting(s, style, Style.RANGED) ? (int) Math.floor(4 + s.getRanged() * 0.1) : 0);
	}

	/** Magic after a magic potion: + 4. */
	static int magic(CombatStats s, Style style)
	{
		return s.getMagic() + (boosting(s, style, Style.MAGIC) ? 4 : 0);
	}

	// ---- prayer factors ----

	static int prayAccuracy(int level, Prayer p)
	{
		return level * p.getAccuracy() / 100;
	}

	/** Burst of Strength / Sharp Eye give at least one level: the calculator adds 1 at 20 or less. */
	static int prayStrength(int level, Prayer p)
	{
		if (p.getStrength() == 105 && level <= 20)
		{
			return level + 1;
		}
		return level * p.getStrength() / 100;
	}

	// ---- helpers ----

	private static boolean boosting(CombatStats s, Style style, Style potionStyle)
	{
		return s.isAssumeBoosts() && style == potionStyle;
	}

	/** Same floating-point form as the calculator, so its rounding matches level for level. */
	private static int superCombat(int level)
	{
		return (int) Math.floor(5 + level * 0.15);
	}

	private static Prayer single(String name, int accuracy, int strength, int magicDamage)
	{
		return new Prayer(Arrays.asList(name), accuracy, strength, magicDamage);
	}

	/** Factor of the best prayer in the table the level allows (adding its name), else 100. */
	private static int best(Object[][] table, int prayerLevel, List<String> names)
	{
		for (Object[] r : table)
		{
			if (prayerLevel >= (int) r[1])
			{
				names.add((String) r[0]);
				return (int) r[2];
			}
		}
		return 100;
	}
}
