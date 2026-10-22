package com.slayercompanion.session;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.api.ItemID;

/**
 * What charged weapons use up per attack: trident runes, blowpipe scales and darts, revenant ether and so on.
 * Charges live on the server; the client has no varbit for them and only learns the count from "Check"
 * messages. So, like the Weapon Charges plugin hub plugin, this counts attacks instead: one charge per
 * attack, an attack being the wielded weapon's attack animation (or, for Tumeken's shadow, its cast graphic)
 * starting this tick. The blowpipe loops one animation without restarting it, so a blowpipe shot is its
 * animation at frame 0. Each charge is costed as the materials it is made from (OSRS wiki), which the
 * caller passes to {@link SupplyTracker#addChargeUsed}. As with item supplies, attacks before the task's
 * first kill are not counted.
 * <p>
 * Blowpipe darts: each shot uses 2/3 of a Zulrah's scale on average, and loses a dart at the rate of the
 * worn ranged cape (Ava's assembler or Dizana's quiver 20%, accumulator 28%, attractor 40%, none 100%).
 * The dart type is learnt from the "Check" message or from darts leaving the inventory while a blowpipe is
 * carried (loading it); until then darts are not costed.
 * <p>
 * Crystal shards cannot be traded, so crystal weapon charges are priced through the enhanced crystal
 * teleport seed that Amrod trades for 150 shards (100 charges per shard).
 */
public class WeaponCharges
{
	/** Powered staff cast, used by the tridents and the sanguinesti staff. */
	public static final int POWERED_STAFF_ANIMATION = 11430;
	/** Tumeken's shadow's cast graphic; the staff has no animation of its own. */
	public static final int SHADOW_GRAPHIC = 2125;
	public static final Set<Integer> BLOWPIPE_ANIMATIONS = Set.of(5061, 10656);
	public static final Set<Integer> BLOWPIPE_IDS = Set.of(ItemID.TOXIC_BLOWPIPE, ItemID.BLAZING_BLOWPIPE);
	/** Darts a blowpipe can hold. */
	public static final Set<Integer> DART_IDS = Set.of(ItemID.BRONZE_DART, ItemID.IRON_DART, ItemID.STEEL_DART,
		ItemID.BLACK_DART, ItemID.MITHRIL_DART, ItemID.ADAMANT_DART, ItemID.RUNE_DART, ItemID.AMETHYST_DART,
		ItemID.DRAGON_DART);
	/**
	 * Items used only to charge weapons. Charging moves them into the weapon rather than using them, so the
	 * supply tracker should {@link SupplyTracker#ignore} them; they are costed here when the charge is spent.
	 */
	public static final Set<Integer> MATERIAL_IDS = Set.of(ItemID.ZULRAHS_SCALES, ItemID.REVENANT_ETHER,
		ItemID.VIAL_OF_BLOOD);

	private static final double SCALES_PER_SHOT = 2 / 3.0;
	private static final Pattern DARTS_CHECK = Pattern.compile("^Darts: (\\w+)(?: dart)? x [\\d,]+\\. Scales:");
	private static final Pattern NO_DARTS_CHECK = Pattern.compile("^Darts: None\\. Scales:");
	private static final Map<String, Integer> DART_NAMES = Map.of("Bronze", ItemID.BRONZE_DART,
		"Iron", ItemID.IRON_DART, "Steel", ItemID.STEEL_DART, "Black", ItemID.BLACK_DART,
		"Mithril", ItemID.MITHRIL_DART, "Adamant", ItemID.ADAMANT_DART, "Rune", ItemID.RUNE_DART,
		"Amethyst", ItemID.AMETHYST_DART, "Dragon", ItemID.DRAGON_DART);

	/** How a weapon shows an attack, and the materials one charge is made from. */
	@Value
	static class Weapon
	{
		Set<Integer> animations;
		Set<Integer> graphics;
		Map<Integer, Double> perCharge;
	}

	private static final Map<Integer, Weapon> WEAPONS = new HashMap<>();

	static
	{
		Set<Integer> staff = Set.of(POWERED_STAFF_ANIMATION, 1167);
		Map<Integer, Double> seas = Map.of(ItemID.DEATH_RUNE, 1.0, ItemID.CHAOS_RUNE, 1.0, ItemID.FIRE_RUNE, 5.0,
			ItemID.COINS_995, 10.0);
		Map<Integer, Double> swamp = Map.of(ItemID.DEATH_RUNE, 1.0, ItemID.CHAOS_RUNE, 1.0, ItemID.FIRE_RUNE, 5.0,
			ItemID.ZULRAHS_SCALES, 1.0);
		add(staff, Set.of(), seas, ItemID.TRIDENT_OF_THE_SEAS, ItemID.TRIDENT_OF_THE_SEAS_FULL,
			ItemID.TRIDENT_OF_THE_SEAS_E, ItemID.TRIDENT_OF_THE_SEAS_O, ItemID.TRIDENT_OF_THE_SEAS_FULL_O,
			ItemID.TRIDENT_OF_THE_SEAS_E_O);
		add(staff, Set.of(), swamp, ItemID.TRIDENT_OF_THE_SWAMP, ItemID.TRIDENT_OF_THE_SWAMP_E,
			ItemID.TRIDENT_OF_THE_SWAMP_O, ItemID.TRIDENT_OF_THE_SWAMP_E_O);
		add(staff, Set.of(), Map.of(ItemID.BLOOD_RUNE, 2.0), ItemID.SANGUINESTI_STAFF, ItemID.HOLY_SANGUINESTI_STAFF);
		add(Set.of(), Set.of(SHADOW_GRAPHIC), Map.of(ItemID.SOUL_RUNE, 2.0, ItemID.CHAOS_RUNE, 5.0),
			ItemID.TUMEKENS_SHADOW);
		add(Set.of(8056), Set.of(), Map.of(ItemID.BLOOD_RUNE, 2.0, ItemID.VIAL_OF_BLOOD, 0.01),
			ItemID.SCYTHE_OF_VITUR, ItemID.HOLY_SCYTHE_OF_VITUR, ItemID.SANGUINE_SCYTHE_OF_VITUR);

		Map<Integer, Double> ether = Map.of(ItemID.REVENANT_ETHER, 1.0);
		add(Set.of(1167, 1978, 1979, 1162, POWERED_STAFF_ANIMATION, 7855, 811, 9961), Set.of(), ether,
			ItemID.ACCURSED_SCEPTRE, ItemID.ACCURSED_SCEPTRE_A, ItemID.THAMMARONS_SCEPTRE, ItemID.THAMMARONS_SCEPTRE_A);
		add(Set.of(426, 9964), Set.of(), ether, ItemID.CRAWS_BOW, ItemID.WEBWEAVER_BOW);
		add(Set.of(245, 9963), Set.of(), ether, ItemID.VIGGORAS_CHAINMACE, ItemID.URSINE_CHAINMACE);

		Map<Integer, Double> crystal = Map.of(ItemID.ENHANCED_CRYSTAL_TELEPORT_SEED, 1 / 15000.0);
		add(Set.of(426), Set.of(), crystal, ItemID.BOW_OF_FAERDHINEN, ItemID.CRYSTAL_BOW);
		add(Set.of(390, 386), Set.of(), crystal, ItemID.BLADE_OF_SAELDOR);
	}

	private static void add(Set<Integer> animations, Set<Integer> graphics, Map<Integer, Double> perCharge,
		int... itemIds)
	{
		Weapon w = new Weapon(animations, graphics, perCharge);
		for (int id : itemIds)
		{
			WEAPONS.put(id, w);
		}
	}

	private boolean started;
	private long attacks;
	private int dartId = -1;
	private Map<Integer, Integer> lastCarried;

	/** Clears attacks for a new task. The dart type is kept. */
	public void reset()
	{
		started = false;
		attacks = 0;
	}

	/** Records {@code n} task kills; the first one starts counting. */
	public void onKill(int n)
	{
		if (n > 0)
		{
			started = true;
		}
	}

	/**
	 * Takes this tick's attack reading.
	 *
	 * @param weaponId         item in the weapon slot, or -1
	 * @param capeId           item in the cape slot, or -1 (for the blowpipe's dart loss)
	 * @param animation        the player's animation now
	 * @param animationFrame   its frame now
	 * @param animationStarted the animation was set this tick
	 * @param graphic          the player's graphic now
	 * @param graphicStarted   the graphic was set this tick
	 * @return materials one attack used, item id to quantity (fractional); empty when there was no attack
	 */
	public Map<Integer, Double> onTick(int weaponId, int capeId, int animation, int animationFrame,
		boolean animationStarted, int graphic, boolean graphicStarted)
	{
		if (!started)
		{
			return Collections.emptyMap();
		}
		if (BLOWPIPE_IDS.contains(weaponId))
		{
			if (!BLOWPIPE_ANIMATIONS.contains(animation) || animationFrame != 0)
			{
				return Collections.emptyMap();
			}
			attacks++;
			Map<Integer, Double> m = new HashMap<>();
			m.put(ItemID.ZULRAHS_SCALES, SCALES_PER_SHOT);
			if (dartId != -1)
			{
				m.put(dartId, dartLoss(capeId));
			}
			return m;
		}
		Weapon w = WEAPONS.get(weaponId);
		if (w == null
			|| !(animationStarted && w.getAnimations().contains(animation)
			|| graphicStarted && w.getGraphics().contains(graphic)))
		{
			return Collections.emptyMap();
		}
		attacks++;
		return w.getPerCharge();
	}

	/** Darts lost per blowpipe shot with this cape worn. */
	static double dartLoss(int capeId)
	{
		switch (capeId)
		{
			case ItemID.AVAS_ASSEMBLER:
			case ItemID.AVAS_ASSEMBLER_L:
			case ItemID.ASSEMBLER_MAX_CAPE:
			case ItemID.ASSEMBLER_MAX_CAPE_L:
			case ItemID.MASORI_ASSEMBLER:
			case ItemID.MASORI_ASSEMBLER_L:
			case ItemID.MASORI_ASSEMBLER_MAX_CAPE:
			case ItemID.MASORI_ASSEMBLER_MAX_CAPE_L:
			case ItemID.DIZANAS_QUIVER:
			case ItemID.DIZANAS_QUIVER_L:
			case ItemID.BLESSED_DIZANAS_QUIVER:
			case ItemID.BLESSED_DIZANAS_QUIVER_L:
			case ItemID.DIZANAS_QUIVER_UNCHARGED:
			case ItemID.DIZANAS_QUIVER_L_UNCHARGED:
			case ItemID.DIZANAS_MAX_CAPE:
			case ItemID.DIZANAS_MAX_CAPE_L:
				return 0.2;
			case ItemID.AVAS_ACCUMULATOR:
			case ItemID.ACCUMULATOR_MAX_CAPE:
			case ItemID.RANGING_CAPE:
			case ItemID.RANGING_CAPET:
			case ItemID.MAX_CAPE:
				return 0.28;
			case ItemID.AVAS_ATTRACTOR:
				return 0.4;
			default:
				return 1.0;
		}
	}

	/** Learns the blowpipe's dart type from its "Check" message (tags already removed). */
	public void onChatMessage(String message)
	{
		Matcher m = DARTS_CHECK.matcher(message);
		if (m.find())
		{
			dartId = DART_NAMES.getOrDefault(m.group(1), dartId);
		}
		else if (NO_DARTS_CHECK.matcher(message).find())
		{
			dartId = -1;
		}
	}

	/**
	 * Learns the blowpipe's dart type from darts leaving the player while a blowpipe is carried (loading it).
	 *
	 * @param counts item id to quantity across inventory, worn equipment and rune pouch
	 */
	public void onCarried(Map<Integer, Integer> counts)
	{
		Map<Integer, Integer> before = lastCarried;
		lastCarried = new HashMap<>(counts);
		if (before == null || !carriesBlowpipe(before) || !carriesBlowpipe(counts))
		{
			return;
		}
		for (int id : DART_IDS)
		{
			if (counts.getOrDefault(id, 0) < before.getOrDefault(id, 0))
			{
				dartId = id;
			}
		}
	}

	private static boolean carriesBlowpipe(Map<Integer, Integer> counts)
	{
		for (int id : BLOWPIPE_IDS)
		{
			if (counts.getOrDefault(id, 0) > 0)
			{
				return true;
			}
		}
		return false;
	}

	/** The blowpipe's dart item id, or -1 while unknown. */
	public int dartId()
	{
		return dartId;
	}

	/** Charged-weapon attacks counted this task. */
	public long attacks()
	{
		return attacks;
	}

	/** Whether an item id is a weapon this tracker counts charges for. */
	public static boolean isChargedWeapon(int itemId)
	{
		return BLOWPIPE_IDS.contains(itemId) || WEAPONS.containsKey(itemId);
	}
}
