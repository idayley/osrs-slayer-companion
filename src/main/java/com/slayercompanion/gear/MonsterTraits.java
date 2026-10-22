package com.slayercompanion.gear;

import com.slayercompanion.data.Monster;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Combat attributes the bundled monster data does not carry yet (draconic, demon and undead are on {@link Monster}).
 * Ids are the NPCs with the matching attribute in the OSRS Wiki DPS calculator's monster data.
 */
public final class MonsterTraits
{
	/** "leafy": Kurask and Turoth, only hurt by leaf-bladed weapons, broad ammo and Magic Dart. */
	private static final Set<Integer> LEAFY = ids(
		410, 411, 7405, // Kurask (normal, big), King kurask
		427, 428, 429, 430, 10397); // Turoth (dad, mum, child, baby), Spiked Turoth

	private static final Set<Integer> KALPHITE = ids(
		955, 957, 958, 959, 960, 962, 963, 965, // Kalphite worker, soldiers, guardians, queen
		7032, // Reanimated kalphite
		729, 794, 797, 799, 1127, 1782, 4192, 6343, 11508, 14126, // scarabs
		795, 796, 800, 801); // locust riders

	/** Vampyre tier: 1 any weapon, 2 silver weapons only, 3 Ivandis flail / blisterwood only. */
	private static final Map<Integer, Integer> VAMPYRE_TIER = new HashMap<>();

	static
	{
		tier(1,
			3137, 3234, 3237, 3707, 5640, 5641, 5642, // Feral vampyres
			3692, 4436, 9731, // Vampyre juveniles
			1128, 3459, 6344, 3481, 6332, 6393, 9560); // Dessous, Count Draynor, Kroy
		tier(2,
			3694, 4427, 4442, 4443, 5634, 5635, 5636, 9614, 9727); // Vampyre juvinates
		tier(3,
			3709, 3710, 3711, 3712, 8252, // Vyrewatch
			9756, 9757, 9758, 9759, 9760, 9761, 9762, 9763, // Vyrewatch sentinels
			8242, 8248, 9561, 9567, // Ranis Drakan, Damien Leucurte, Vanstrom Klause
			15765, 15766, 15767, 15768, 15769, 15770); // Venators

	}

	private MonsterTraits()
	{
	}

	public static boolean isLeafy(Monster m)
	{
		return LEAFY.contains(m.getId());
	}

	public static boolean isKalphite(Monster m)
	{
		return KALPHITE.contains(m.getId());
	}

	/** 1-3, or 0 when the monster is not a vampyre. */
	public static int vampyreTier(Monster m)
	{
		return VAMPYRE_TIER.getOrDefault(m.getId(), 0);
	}

	private static Set<Integer> ids(Integer... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}

	private static void tier(int tier, int... ids)
	{
		for (int id : ids)
		{
			VAMPYRE_TIER.put(id, tier);
		}
	}
}
