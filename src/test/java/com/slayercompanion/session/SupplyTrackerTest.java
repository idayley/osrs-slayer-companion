package com.slayercompanion.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ItemID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class SupplyTrackerTest
{
	private static final int ARROW = ItemID.RUNE_ARROW;
	private static final int BOW = ItemID.MAGIC_SHORTBOW;
	private static final int FIRE = ItemID.FIRE_RUNE;
	private static final int PRAY4 = ItemID.PRAYER_POTION4;
	private static final int PRAY3 = ItemID.PRAYER_POTION3;
	private static final int PRAY2 = ItemID.PRAYER_POTION2;
	private static final int RESTORE1 = ItemID.SUPER_RESTORE1;

	private final Map<Integer, Long> prices = new HashMap<>();
	private final Map<Integer, String> names = new HashMap<>();
	private SupplyTracker t;

	@Before
	public void setUp()
	{
		prices.put(ARROW, 60L);
		prices.put(FIRE, 5L);
		prices.put(PRAY4, 8000L);
		prices.put(PRAY3, 6000L);
		prices.put(ItemID.SHARK, 900L);
		prices.put(ItemID.SUPER_RESTORE4, 12000L);
		prices.put(RESTORE1, 3000L);
		names.put(PRAY4, "Prayer potion(4)");
		names.put(PRAY3, "Prayer potion(3)");
		names.put(PRAY2, "Prayer potion(2)");
		names.put(RESTORE1, "Super restore(1)");
		names.put(ItemID.VIAL, "Vial");
		t = new SupplyTracker(id -> prices.getOrDefault(id, 0L), names::get);
	}

	private static Map<Integer, Integer> items(int... idQty)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			m.put(idQty[i], idQty[i + 1]);
		}
		return m;
	}

	@Test
	public void arrowsFired()
	{
		t.onSnapshot(items(BOW, 1, ARROW, 500));
		t.onKill(1);
		t.onSnapshot(items(BOW, 1, ARROW, 480));
		t.onKill(1);
		t.onSnapshot(items(BOW, 1, ARROW, 460));
		assertEquals(40, t.usedTotal(ARROW));
		assertEquals(20.0, t.usedPerKill(ARROW).getAsDouble(), 1e-9);
		assertEquals(20 * 60.0, t.costPerKill().getAsDouble(), 1e-9);
		assertEquals(460, t.count(ARROW));
	}

	@Test
	public void runesCast()
	{
		t.onSnapshot(items(FIRE, 1000));
		t.onKill(1);
		t.onSnapshot(items(FIRE, 900));
		assertEquals(100, t.usedTotal(FIRE));
		assertEquals(500.0, t.costPerKill().getAsDouble(), 1e-9);
	}

	@Test
	public void potionDoseIsOneDose()
	{
		t.onSnapshot(items(PRAY4, 2));
		t.onKill(1);
		t.onSnapshot(items(PRAY4, 1, PRAY3, 1));
		assertEquals(1, t.dosesUsed("Prayer potion"));
		assertEquals(0, t.usedTotal(PRAY4));
		// one dose at 8000 / 4
		assertEquals(2000.0, t.costPerKill().getAsDouble(), 1e-9);
		t.onKill(1);
		t.onSnapshot(items(PRAY4, 1, PRAY2, 1));
		assertEquals(2, t.dosesUsed("Prayer potion"));
		assertEquals(1.0, t.dosesUsedPerKill(SupplyTracker.PRAYER_FAMILIES).getAsDouble(), 1e-9);
		assertEquals(6, t.doses(SupplyTracker.PRAYER_FAMILIES));
	}

	@Test
	public void lastDoseLeavesEmptyVialAtNoCost()
	{
		names.put(ItemID.SUPER_RESTORE4, "Super restore(4)");
		t.onSnapshot(items(RESTORE1, 1, ItemID.SUPER_RESTORE4, 1));
		t.onKill(1);
		t.onSnapshot(items(ItemID.VIAL, 1, ItemID.SUPER_RESTORE4, 1));
		t.onSnapshot(items(ItemID.SUPER_RESTORE4, 1)); // vial smashed or dropped
		assertEquals(1, t.dosesUsed("Super restore"));
		assertEquals(0, t.usedTotal(ItemID.VIAL));
		assertEquals(3000.0, t.costPerKill().getAsDouble(), 1e-9);
	}

	@Test
	public void bankingNotCounted()
	{
		t.onSnapshot(items(ItemID.SHARK, 10, ARROW, 500));
		t.onKill(1);
		t.setBankOpen(true);
		t.onSnapshot(items(ARROW, 500));
		t.onSnapshot(items(ItemID.SHARK, 20, ARROW, 100));
		t.setBankOpen(false);
		assertEquals(0, t.costPerKill().getAsDouble(), 0.0);
		t.onSnapshot(items(ItemID.SHARK, 19, ARROW, 100));
		assertEquals(1, t.foodUsed());
		assertEquals(1, t.usedTotal(ItemID.SHARK));
		assertEquals(0, t.usedTotal(ARROW));
	}

	@Test
	public void equippingNotCounted()
	{
		// The bow moves from inventory to worn; the combined snapshot is unchanged.
		t.onSnapshot(items(BOW, 1, ARROW, 500));
		t.onKill(1);
		t.onSnapshot(items(BOW, 1, ARROW, 500));
		assertEquals(0, t.usedTotal(BOW));
		assertEquals(0.0, t.costPerKill().getAsDouble(), 0.0);
	}

	@Test
	public void lootIgnored()
	{
		t.onSnapshot(items(ARROW, 500));
		t.onKill(1);
		t.onSnapshot(items(ARROW, 500, FIRE, 50, ItemID.SHARK, 3));
		assertEquals(0.0, t.costPerKill().getAsDouble(), 0.0);
		assertEquals(0, t.usedTotal(FIRE));
	}

	@Test
	public void beforeFirstKillIgnored()
	{
		t.onSnapshot(items(ARROW, 500, ItemID.SHARK, 10));
		t.onSnapshot(items(ARROW, 450, ItemID.SHARK, 8));
		assertFalse(t.costPerKill().isPresent());
		assertEquals(0, t.usedTotal(ARROW));
		t.onKill(1);
		t.onSnapshot(items(ARROW, 440, ItemID.SHARK, 8));
		assertEquals(10, t.usedTotal(ARROW));
		assertEquals(0, t.foodUsed());
	}

	@Test
	public void resetClearsUsageButKeepsBaseline()
	{
		t.onSnapshot(items(ARROW, 500));
		t.onKill(1);
		t.onSnapshot(items(ARROW, 400));
		t.reset();
		assertEquals(0, t.usedTotal(ARROW));
		assertFalse(t.costPerKill().isPresent());
		t.onKill(1);
		t.onSnapshot(items(ARROW, 390));
		assertEquals(10, t.usedTotal(ARROW));
	}

	@Test
	public void foodRunway()
	{
		t.onSnapshot(items(ItemID.SHARK, 20));
		t.onKill(2);
		t.onSnapshot(items(ItemID.SHARK, 16)); // 2 per kill
		assertEquals(8, t.killsUntilFoodRunsOut().getAsInt());
		assertEquals(16, t.foodCount());
	}

	@Test
	public void parsesDoseNames()
	{
		assertEquals(new SupplyTracker.Dose("Prayer potion", 4), SupplyTracker.parseDose("Prayer potion(4)").get());
		assertEquals(new SupplyTracker.Dose("Black mask", 10), SupplyTracker.parseDose("Black mask (10)").get());
		assertFalse(SupplyTracker.parseDose("Black mask (i)").isPresent());
		assertFalse(SupplyTracker.parseDose("Shark").isPresent());
		assertFalse(SupplyTracker.parseDose(null).isPresent());
		assertTrue(SupplyTracker.PRAYER_FAMILIES.containsAll(Set.of("Prayer potion", "Super restore")));
	}

	@Test
	public void chargesUsedAreCostedInFractions()
	{
		prices.put(ItemID.ZULRAHS_SCALES, 150L);
		t.onSnapshot(items());
		t.onKill(1);
		for (int i = 0; i < 3; i++)
		{
			t.addChargeUsed(ItemID.ZULRAHS_SCALES, 2 / 3.0);
		}
		assertEquals(2.0, t.chargeMaterialUsed(ItemID.ZULRAHS_SCALES), 1e-9);
		assertEquals(300.0, t.costTotal(), 1e-6);
		assertEquals(0, t.usedTotal(ItemID.ZULRAHS_SCALES));
		t.reset();
		assertEquals(0.0, t.costTotal(), 0.0);
	}

	@Test
	public void itemsIgnoredWhileTheirCarrierIsCarried()
	{
		int pipe = ItemID.TOXIC_BLOWPIPE;
		int dart = ItemID.DRAGON_DART;
		t.ignoreWhileCarried(Set.of(pipe), Set.of(dart));
		t.onSnapshot(items(pipe, 1, dart, 300));
		t.onKill(1);
		t.onSnapshot(items(pipe, 1, dart, 100)); // loaded into the blowpipe
		assertEquals(0, t.usedTotal(dart));
		t.onSnapshot(items(dart, 100));
		t.onSnapshot(items(dart, 90)); // no blowpipe: thrown
		assertEquals(10, t.usedTotal(dart));
	}
}
