package com.slayercompanion.gear;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.Monster;
import java.util.Locale;

/**
 * Weapon effects that depend on the monster: which setups can hurt it at all (Kurask/Turoth, vampyres), and
 * the wiki DPS calculator's weapon-vs-attribute bonuses (dragon hunter, demonbane, keris, vampyrebane, leafy).
 * Factors are applied in the calculator's order, each one rounded down.
 */
public final class WeaponEffects
{
	public static final String MAGIC_DART = "Magic Dart";

	private static final String[] LEAF_BLADED = {"leaf-bladed battleaxe", "leaf-bladed spear", "leaf-bladed sword"};
	private static final String[] BROAD_AMMO = {"broad arrows", "broad bolts", "amethyst broad bolts", "seeking broad arrows"};
	private static final String[] MAGIC_DART_STAVES = {"slayer's staff", "staff of the dead", "toxic staff of the dead",
		"staff of light", "staff of balance"};
	/** Melee weapons that hurt tier 3 vampyres (and tier 2 at full damage). */
	private static final String[] VAMPYREBANE = {"ivandis flail", "blisterwood flail", "blisterwood sickle",
		"hallowed flail", "sunspear"};
	private static final String[] SILVER_MELEE = {"blessed axe", "ivandis flail", "blisterwood flail", "hallowed flail",
		"silver sickle", "emerald sickle", "ruby sickle", "enchanted emerald sickle", "enchanted ruby sickle",
		"blisterwood sickle", "silverlight", "darklight", "arclight", "rod of ivandis", "wolfbane"};
	/** Tier 2 vampyres take at most this per hit from silver weapons that are not Ivandis/Blisterwood. */
	private static final int SILVER_TIER_TWO_CAP = 10;

	private WeaponEffects()
	{
	}

	// ---- immunity ----

	/** True when nothing in this setup can damage the monster, so it must score 0 dps. */
	public static boolean isImmune(Loadout l, Monster m)
	{
		if (MonsterTraits.isLeafy(m) && !hurtsLeafy(l))
		{
			return true;
		}
		int tier = MonsterTraits.vampyreTier(m);
		if (tier == 3)
		{
			return !vampyrebane(l, false);
		}
		return tier == 2 && !vampyrebane(l, true) && !silver(l);
	}

	/** What the player must bring when a monster ignores ordinary weapons; null for ordinary monsters. */
	public static String immunityHint(Monster m)
	{
		if (MonsterTraits.isLeafy(m))
		{
			return "Needs a leaf-bladed weapon, broad ammo or Magic Dart";
		}
		switch (MonsterTraits.vampyreTier(m))
		{
			case 2:
				return "Needs a silver weapon, ideally an Ivandis flail or Blisterwood weapon";
			case 3:
				return "Needs an Ivandis flail or Blisterwood weapon";
			default:
				return null;
		}
	}

	/** Whether this ammo can hurt the monster at all, so the optimizer loads it instead of stronger useless ammo. */
	public static boolean ammoCanHurt(Monster m, Equipment ammo)
	{
		if (MonsterTraits.isLeafy(m))
		{
			return isAny(ammo, BROAD_AMMO);
		}
		int tier = MonsterTraits.vampyreTier(m);
		if (tier == 3)
		{
			return false;
		}
		return tier != 2 || isSilverBolts(ammo);
	}

	/** Magic Dart needs one of these staves wielded (plus 50 Magic and 55 Slayer, checked by the optimizer). */
	public static boolean canCastMagicDart(Equipment weapon)
	{
		return isAny(weapon, MAGIC_DART_STAVES);
	}

	/** Ivandis/Blisterwood flails and the Rod of Ivandis sit in the "Staff" category but are melee weapons. */
	static boolean isMeleeStaff(Equipment weapon)
	{
		return isAny(weapon, "ivandis flail", "blisterwood flail", "rod of ivandis");
	}

	private static boolean hurtsLeafy(Loadout l)
	{
		switch (l.getStyle())
		{
			case MELEE:
				return isAny(weapon(l), LEAF_BLADED);
			case RANGED:
				return isAny(l.getBySlot().get("ammo"), BROAD_AMMO);
			default:
				return MAGIC_DART.equalsIgnoreCase(l.getSpell());
		}
	}

	/** Weapons that deal full damage to vampyres; the Rod of Ivandis only counts for tier 2. */
	private static boolean vampyrebane(Loadout l, boolean tierTwo)
	{
		Equipment w = weapon(l);
		if (l.getStyle() == Style.RANGED)
		{
			return is(w, "blisterwood stake");
		}
		return l.getStyle() == Style.MELEE && (isAny(w, VAMPYREBANE) || tierTwo && is(w, "rod of ivandis"));
	}

	private static boolean silver(Loadout l)
	{
		if (l.getStyle() == Style.RANGED)
		{
			return is(weapon(l), "blisterwood stake") || isSilverBolts(l.getBySlot().get("ammo"));
		}
		return l.getStyle() == Style.MELEE && isAny(weapon(l), SILVER_MELEE);
	}

	private static boolean isSilverBolts(Equipment ammo)
	{
		return is(ammo, "silver bolts");
	}

	// ---- accuracy ----

	/** Applies weapon-vs-monster accuracy factors to an attack roll that already includes slayer helm / salve. */
	static long applyAccuracy(long roll, Loadout l, Monster m)
	{
		Equipment w = weapon(l);
		switch (l.getStyle())
		{
			case MELEE:
				if (m.isDemon())
				{
					roll = demonbane(roll, w);
				}
				if (m.isDragon() && is(w, "dragon hunter lance"))
				{
					roll = roll * 6 / 5;
				}
				if (MonsterTraits.isKalphite(m) && is(w, "keris partisan of breaching"))
				{
					roll = roll * 133 / 100;
				}
				if (MonsterTraits.vampyreTier(m) > 0)
				{
					if (isAny(w, "blisterwood flail", "blisterwood sickle"))
					{
						roll = roll * 105 / 100;
					}
					else if (isAny(w, "hallowed flail", "sunspear"))
					{
						roll = roll * 125 / 100;
					}
				}
				return roll;
			case RANGED:
				if (m.isDragon() && is(w, "dragon hunter crossbow"))
				{
					roll = roll * 13 / 10;
				}
				if (m.isDemon() && is(w, "scorching bow"))
				{
					roll = addPercent(roll, 30);
				}
				return roll;
			default:
				if (m.isDragon() && is(w, "dragon hunter wand"))
				{
					roll = roll * 7 / 4;
				}
				return roll;
		}
	}

	// ---- max hit ----

	/** Dragon hunter wand: +40% to the spell's base max hit vs draconic, before magic damage bonuses. */
	static int applyMagicBase(int base, Loadout l, Monster m)
	{
		return m.isDragon() && is(weapon(l), "dragon hunter wand") ? base * 7 / 5 : base;
	}

	/**
	 * Extra twentieths the ranged weapon adds to an imbued slayer helm's 23/20 instead of multiplying after it
	 * (dragon hunter crossbow +5, scorching bow +6); 0 when none applies.
	 */
	static int rangedHelmAddend(Loadout l, Monster m)
	{
		Equipment w = weapon(l);
		if (m.isDragon() && is(w, "dragon hunter crossbow"))
		{
			return 5;
		}
		return m.isDemon() && is(w, "scorching bow") ? 6 : 0;
	}

	/**
	 * Applies weapon-vs-monster damage factors to a max hit that already includes slayer helm / salve.
	 *
	 * @param rangedBonusInHelm the ranged weapon's bonus was already added into the imbued slayer helm factor
	 */
	static int applyMaxHit(int max, Loadout l, Monster m, boolean rangedBonusInHelm)
	{
		Equipment w = weapon(l);
		switch (l.getStyle())
		{
			case MELEE:
				if (m.isDemon() && !isAny(w, "silverlight", "darklight"))
				{
					max = (int) demonbane(max, w);
				}
				if (m.isDragon() && is(w, "dragon hunter lance"))
				{
					max = max * 6 / 5;
				}
				if (MonsterTraits.isKalphite(m) && is(w, "keris"))
				{
					max = is(w, "keris partisan of amascut") ? max * 115 / 100 : max * 133 / 100;
				}
				if (m.isDemon() && isAny(w, "silverlight", "darklight"))
				{
					max = (int) addPercent(max, 60);
				}
				if (MonsterTraits.isLeafy(m) && is(w, "leaf-bladed battleaxe"))
				{
					max = max * 47 / 40;
				}
				break;
			case RANGED:
				if (!rangedBonusInHelm && m.isDragon() && is(w, "dragon hunter crossbow"))
				{
					max = max * 5 / 4;
				}
				if (!rangedBonusInHelm && m.isDemon() && is(w, "scorching bow"))
				{
					max = (int) addPercent(max, 30);
				}
				break;
			default:
				break;
		}
		return vampyreDamage(max, l, MonsterTraits.vampyreTier(m));
	}

	/** Vampyrebane / silver damage bonuses, then the tier 2 cap for other silver weapons. */
	private static int vampyreDamage(int max, Loadout l, int tier)
	{
		if (tier == 0)
		{
			return max;
		}
		Equipment w = weapon(l);
		if (isAny(w, "blisterwood flail", "hallowed flail", "blisterwood stake"))
		{
			max = max * 125 / 100;
		}
		else if (is(w, "sunspear"))
		{
			max = max * 150 / 100;
		}
		else if (is(w, "blisterwood sickle"))
		{
			max = max * 23 / 20;
		}
		else if (is(w, "ivandis flail"))
		{
			max = max * 6 / 5;
		}
		else if (is(w, "rod of ivandis") && tier != 3)
		{
			max = max * 11 / 10;
		}
		else if (tier == 1 && silver(l))
		{
			max = max * 11 / 10;
		}
		if (tier == 2 && !vampyrebane(l, true) && silver(l))
		{
			max = Math.min(max, SILVER_TIER_TWO_CAP);
		}
		return max;
	}

	/** Melee demonbane: Arclight/Emberlight +70%, Silverlight/Darklight +60%, bone/burning claws +5%. */
	private static long demonbane(long value, Equipment w)
	{
		if (isAny(w, "arclight", "emberlight") && !is(w, "arclight (inactive)"))
		{
			return addPercent(value, 70);
		}
		if (isAny(w, "silverlight", "darklight"))
		{
			return addPercent(value, 60);
		}
		if (isAny(w, "bone claws", "burning claws"))
		{
			return addPercent(value, 5);
		}
		return value;
	}

	/** Wiki "add factor": value + floor(value * percent / 100). */
	private static long addPercent(long value, int percent)
	{
		return value + value * percent / 100;
	}

	// ---- name matching ----

	private static Equipment weapon(Loadout l)
	{
		return l.getBySlot().get("weapon");
	}

	/** Case-insensitive prefix match, so "Arclight (Charged)" and "Rod of Ivandis (5)" match their base names. */
	private static boolean is(Equipment e, String prefix)
	{
		return e != null && e.getName() != null && e.getName().toLowerCase(Locale.ROOT).startsWith(prefix);
	}

	private static boolean isAny(Equipment e, String... prefixes)
	{
		for (String p : prefixes)
		{
			if (is(e, p))
			{
				return true;
			}
		}
		return false;
	}
}
