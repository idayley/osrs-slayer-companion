package com.slayercompanion.session;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ItemID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeaponChargesTest
{
	private static final int NO_CAPE = -1;
	private static final int CAST = WeaponCharges.POWERED_STAFF_ANIMATION;

	private WeaponCharges w;

	@Before
	public void setUp()
	{
		w = new WeaponCharges();
	}

	/** One tick where {@code weapon}'s player started {@code animation} (no graphic). */
	private Map<Integer, Double> animate(int weapon, int animation)
	{
		return w.onTick(weapon, NO_CAPE, animation, 0, true, -1, false);
	}

	private static Map<Integer, Double> use(int... idQty)
	{
		Map<Integer, Double> m = new HashMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			m.put(idQty[i], (double) idQty[i + 1]);
		}
		return m;
	}

	private static void assertUse(Map<Integer, Double> expected, Map<Integer, Double> actual)
	{
		assertEquals(expected.keySet(), actual.keySet());
		expected.forEach((id, qty) -> assertEquals("item " + id, qty, actual.get(id), 1e-9));
	}

	@Test
	public void nothingCountsBeforeTheFirstKill()
	{
		assertTrue(animate(ItemID.TRIDENT_OF_THE_SEAS, CAST).isEmpty());
		w.onKill(1);
		assertFalse(animate(ItemID.TRIDENT_OF_THE_SEAS, CAST).isEmpty());
	}

	@Test
	public void tridentOfTheSeasChargeIsRunesAndCoins()
	{
		w.onKill(1);
		Map<Integer, Double> seas = use(ItemID.DEATH_RUNE, 1, ItemID.CHAOS_RUNE, 1, ItemID.FIRE_RUNE, 5,
			ItemID.COINS_995, 10);
		assertUse(seas, animate(ItemID.TRIDENT_OF_THE_SEAS, CAST));
		assertUse(seas, animate(ItemID.TRIDENT_OF_THE_SEAS_FULL, CAST));
		assertUse(seas, animate(ItemID.TRIDENT_OF_THE_SEAS_E, CAST));
	}

	@Test
	public void tridentOfTheSwampChargeUsesAScaleInsteadOfCoins()
	{
		w.onKill(1);
		Map<Integer, Double> swamp = use(ItemID.DEATH_RUNE, 1, ItemID.CHAOS_RUNE, 1, ItemID.FIRE_RUNE, 5,
			ItemID.ZULRAHS_SCALES, 1);
		assertUse(swamp, animate(ItemID.TRIDENT_OF_THE_SWAMP, CAST));
		assertUse(swamp, animate(ItemID.TRIDENT_OF_THE_SWAMP_E, CAST));
	}

	@Test
	public void sanguinestiChargeIsTwoBloodRunes()
	{
		w.onKill(1);
		assertUse(use(ItemID.BLOOD_RUNE, 2), animate(ItemID.SANGUINESTI_STAFF, CAST));
		assertUse(use(ItemID.BLOOD_RUNE, 2), animate(ItemID.HOLY_SANGUINESTI_STAFF, CAST));
	}

	@Test
	public void tumekensShadowCountsByItsCastGraphic()
	{
		w.onKill(1);
		assertTrue(animate(ItemID.TUMEKENS_SHADOW, CAST).isEmpty());
		assertUse(use(ItemID.SOUL_RUNE, 2, ItemID.CHAOS_RUNE, 5),
			w.onTick(ItemID.TUMEKENS_SHADOW, NO_CAPE, -1, 0, false, WeaponCharges.SHADOW_GRAPHIC, true));
		// The graphic still showing on a later tick is the same cast.
		assertTrue(w.onTick(ItemID.TUMEKENS_SHADOW, NO_CAPE, -1, 0, false, WeaponCharges.SHADOW_GRAPHIC, false)
			.isEmpty());
	}

	@Test
	public void revenantWeaponsUseOneEtherPerAttack()
	{
		w.onKill(1);
		assertUse(use(ItemID.REVENANT_ETHER, 1), animate(ItemID.ACCURSED_SCEPTRE, 1167));
		assertUse(use(ItemID.REVENANT_ETHER, 1), animate(ItemID.THAMMARONS_SCEPTRE_A, 1167));
		assertUse(use(ItemID.REVENANT_ETHER, 1), animate(ItemID.WEBWEAVER_BOW, 426));
		assertUse(use(ItemID.REVENANT_ETHER, 1), animate(ItemID.URSINE_CHAINMACE, 245));
	}

	@Test
	public void crystalWeaponsArePricedThroughTheTeleportSeed()
	{
		w.onKill(1);
		// 100 charges per shard, 150 shards per enhanced crystal teleport seed.
		Map<Integer, Double> perShot = Map.of(ItemID.ENHANCED_CRYSTAL_TELEPORT_SEED, 1 / 15000.0);
		assertUse(perShot, animate(ItemID.BOW_OF_FAERDHINEN, 426));
		assertUse(perShot, animate(ItemID.CRYSTAL_BOW, 426));
		assertUse(perShot, animate(ItemID.BLADE_OF_SAELDOR, 390));
		// The corrupted bow never degrades.
		assertTrue(animate(ItemID.BOW_OF_FAERDHINEN_C, 426).isEmpty());
	}

	@Test
	public void scytheUsesBloodRunesAndAHundredthOfAVial()
	{
		w.onKill(1);
		Map<Integer, Double> m = Map.of(ItemID.BLOOD_RUNE, 2.0, ItemID.VIAL_OF_BLOOD, 0.01);
		assertUse(m, animate(ItemID.SCYTHE_OF_VITUR, 8056));
	}

	@Test
	public void onlyTheWeaponsOwnAttackAnimationCounts()
	{
		w.onKill(1);
		assertTrue("eating", animate(ItemID.TRIDENT_OF_THE_SEAS, 829).isEmpty());
		assertTrue("not a charged weapon", animate(ItemID.MAGIC_SHORTBOW, 426).isEmpty());
		assertTrue("nothing wielded", animate(-1, CAST).isEmpty());
		// The animation carrying on from an earlier tick is the same attack.
		assertTrue(w.onTick(ItemID.TRIDENT_OF_THE_SEAS, NO_CAPE, CAST, 5, false, -1, false).isEmpty());
	}

	@Test
	public void blowpipeCountsEachLoopOfItsAnimationAndUsesTwoThirdsOfAScale()
	{
		w.onKill(1);
		int anim = WeaponCharges.BLOWPIPE_ANIMATIONS.iterator().next();
		// The blowpipe loops one animation, so a new shot is frame 0 whether or not the animation was just set.
		assertUse(Map.of(ItemID.ZULRAHS_SCALES, 2 / 3.0), w.onTick(ItemID.TOXIC_BLOWPIPE, NO_CAPE, anim, 0, false, -1, false));
		assertTrue(w.onTick(ItemID.TOXIC_BLOWPIPE, NO_CAPE, anim, 4, false, -1, false).isEmpty());
		assertUse(Map.of(ItemID.ZULRAHS_SCALES, 2 / 3.0), w.onTick(ItemID.BLAZING_BLOWPIPE, NO_CAPE, anim, 0, true, -1, false));
		assertEquals(-1, w.dartId());
	}

	@Test
	public void blowpipeDartsAreCostedOnceTheTypeIsKnown()
	{
		w.onKill(1);
		int anim = WeaponCharges.BLOWPIPE_ANIMATIONS.iterator().next();
		w.onChatMessage("Darts: Dragon dart x 4,321. Scales: 5,000 (30.5%).");
		assertEquals(ItemID.DRAGON_DART, w.dartId());
		assertUse(Map.of(ItemID.ZULRAHS_SCALES, 2 / 3.0, ItemID.DRAGON_DART, 0.2),
			w.onTick(ItemID.TOXIC_BLOWPIPE, ItemID.MASORI_ASSEMBLER, anim, 0, true, -1, false));
		assertUse(Map.of(ItemID.ZULRAHS_SCALES, 2 / 3.0, ItemID.DRAGON_DART, 0.28),
			w.onTick(ItemID.TOXIC_BLOWPIPE, ItemID.AVAS_ACCUMULATOR, anim, 0, true, -1, false));
		assertUse(Map.of(ItemID.ZULRAHS_SCALES, 2 / 3.0, ItemID.DRAGON_DART, 1.0),
			w.onTick(ItemID.TOXIC_BLOWPIPE, NO_CAPE, anim, 0, true, -1, false));

		w.onChatMessage("Darts: None. Scales: 99 (0.6%).");
		assertEquals(-1, w.dartId());
	}

	@Test
	public void loadingDartsIntoTheBlowpipeTeachesTheDartType()
	{
		w.onCarried(Map.of(ItemID.TOXIC_BLOWPIPE, 1, ItemID.AMETHYST_DART, 500));
		w.onCarried(Map.of(ItemID.TOXIC_BLOWPIPE, 1, ItemID.AMETHYST_DART, 200));
		assertEquals(ItemID.AMETHYST_DART, w.dartId());
		// Darts going down without a blowpipe are not loaded into it.
		w.onCarried(Map.of(ItemID.RUNE_DART, 100));
		w.onCarried(Map.of(ItemID.RUNE_DART, 50));
		assertEquals(ItemID.AMETHYST_DART, w.dartId());
	}

	@Test
	public void resetWaitsForTheNextTasksFirstKill()
	{
		w.onKill(1);
		animate(ItemID.SANGUINESTI_STAFF, CAST);
		assertEquals(1, w.attacks());
		w.reset();
		assertEquals(0, w.attacks());
		assertTrue(animate(ItemID.SANGUINESTI_STAFF, CAST).isEmpty());
	}

	@Test
	public void materialsOnlyUsedForChargingAreKnown()
	{
		assertTrue(WeaponCharges.MATERIAL_IDS.contains(ItemID.ZULRAHS_SCALES));
		assertTrue(WeaponCharges.MATERIAL_IDS.contains(ItemID.REVENANT_ETHER));
		assertTrue(WeaponCharges.MATERIAL_IDS.contains(ItemID.VIAL_OF_BLOOD));
		assertFalse("runes are also cast", WeaponCharges.MATERIAL_IDS.contains(ItemID.BLOOD_RUNE));
		assertTrue(WeaponCharges.DART_IDS.contains(ItemID.DRAGON_DART));
		assertTrue(WeaponCharges.BLOWPIPE_IDS.contains(ItemID.TOXIC_BLOWPIPE));
	}
}
