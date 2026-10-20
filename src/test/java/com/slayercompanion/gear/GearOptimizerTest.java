package com.slayercompanion.gear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.Monster;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

public class GearOptimizerTest
{
	private static final Equipment WHIP = eq(4151, "Abyssal whip", "weapon", 4, "slash", 82, "str", 82, null);
	private static final Equipment SCIM = eq(1333, "Rune scimitar", "weapon", 4, "slash", 45, "str", 44, null);
	private static final Equipment PRICEY = eq(9999, "Scythe of Vitur", "weapon", 5, "slash", 110, "str", 130, null);
	private static final Equipment HIGHREQ = eq(9998, "Godsword", "weapon", 6, "slash", 132, "str", 132, 70);

	private static Equipment eq(int id, String name, String slot, int speed, String atk, int a, String str, int s, Integer attackReq)
	{
		Equipment e = new Equipment();
		e.setItemId(id);
		e.setName(name);
		e.setSlot(slot);
		e.setSpeedTicks(speed);
		e.setCategory("Slash Sword");
		e.getBonuses().put(atk, a);
		e.getBonuses().put(str, s);
		if (attackReq != null)
		{
			e.getSkillReqs().put("attack", attackReq);
		}
		return e;
	}

	private static Monster giant()
	{
		Monster m = new Monster();
		m.setId(1);
		m.setName("Fire giant");
		m.setDefenceLevel(65);
		m.setMagicLevel(1);
		m.getDefensiveBonuses().put("slash", 20);
		m.getDefensiveBonuses().put("stab", 20);
		m.getDefensiveBonuses().put("crush", 20);
		return m;
	}

	private static CombatStats stats(int atk)
	{
		return new CombatStats(atk, 99, 99, 99, 99, 99, 99, 99);
	}

	private static OwnedItems owned(Equipment... items)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (Equipment e : items)
		{
			m.put(e.getItemId(), 1);
		}
		OwnedItems o = new OwnedItems();
		o.updateBank(m, Instant.now());
		return o;
	}

	@Test
	public void prefersWhipOverRuneScimitar()
	{
		GearOptimizer g = new GearOptimizer(List.of(WHIP, SCIM));
		Optional<Loadout> l = g.best(stats(99), Style.MELEE, giant(), false, owned(WHIP, SCIM));
		assertEquals("Abyssal whip", l.get().getBySlot().get("weapon").getName());
	}

	@Test
	public void onlyUsesOwnedItems()
	{
		GearOptimizer g = new GearOptimizer(List.of(WHIP, SCIM, PRICEY));
		OwnedItems o = owned(SCIM);
		Loadout l = g.best(stats(99), Style.MELEE, giant(), false, o).get();
		assertFalse(l.getBySlot().isEmpty());
		l.getBySlot().values().forEach(e -> assertTrue(o.count(e.getItemId()) > 0));
	}

	@Test
	public void emptyWhenBankNeverSeen()
	{
		GearOptimizer g = new GearOptimizer(List.of(WHIP));
		assertFalse(g.best(stats(99), Style.MELEE, giant(), false, new OwnedItems()).isPresent());
	}

	@Test
	public void upgradeSkipsUnmetRequirements()
	{
		GearOptimizer g = new GearOptimizer(List.of(SCIM, HIGHREQ));
		Loadout cur = new Loadout();
		cur.getBySlot().put("weapon", SCIM);
		Optional<GearOptimizer.Upgrade> u = g.biggestUpgrade(stats(60), new HashSet<>(), cur, giant(), false, owned(SCIM));
		assertFalse(u.isPresent());
	}

	@Test
	public void upgradeIgnoresPrice()
	{
		GearOptimizer g = new GearOptimizer(List.of(SCIM, WHIP, PRICEY, HIGHREQ));
		Loadout cur = new Loadout();
		cur.getBySlot().put("weapon", SCIM);
		GearOptimizer.Upgrade u = g.biggestUpgrade(stats(60), new HashSet<>(), cur, giant(), false, owned(SCIM)).get();
		assertEquals("Scythe of Vitur", u.getItem().getName());
		assertTrue(u.getDpsGainPercent() > 0);
	}

	@Test
	public void upgradeSkipsOwnedItems()
	{
		GearOptimizer g = new GearOptimizer(List.of(SCIM, WHIP, PRICEY));
		Loadout cur = new Loadout();
		cur.getBySlot().put("weapon", SCIM);
		GearOptimizer.Upgrade u = g.biggestUpgrade(stats(60), new HashSet<>(), cur, giant(), false, owned(SCIM, PRICEY)).get();
		assertEquals("Abyssal whip", u.getItem().getName());
	}
}
