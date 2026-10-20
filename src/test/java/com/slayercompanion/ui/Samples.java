package com.slayercompanion.ui;

import com.slayercompanion.data.Drop;
import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.ItemNeed;
import com.slayercompanion.data.Location;
import com.slayercompanion.data.Monster;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.gear.Loadout;
import com.slayercompanion.gear.Style;
import com.slayercompanion.locations.RankedLocation;
import com.slayercompanion.task.CurrentTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Sample domain objects shared by the view-model tests and the panel screenshot tool. */
final class Samples
{
	static final int SHARK = 385;
	static final int WHIP = 4151;

	private Samples()
	{
	}

	static class MutableClock extends Clock
	{
		Instant now = Instant.parse("2026-01-01T00:00:00Z");

		void advance(long seconds)
		{
			now = now.plus(Duration.ofSeconds(seconds));
		}

		@Override
		public ZoneId getZone()
		{
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone)
		{
			return this;
		}

		@Override
		public Instant instant()
		{
			return now;
		}
	}

	static SlayerTask fireGiants()
	{
		SlayerTask t = new SlayerTask();
		t.setId("fire-giants");
		t.setName("Fire giants");
		t.setMonsterIds(Arrays.asList(2075, 7252));
		return t;
	}

	static SlayerTask needs(String taskName, String itemName, int... ids)
	{
		SlayerTask t = new SlayerTask();
		t.setId(taskName.toLowerCase().replace(' ', '-'));
		t.setName(taskName);
		ItemNeed need = new ItemNeed();
		need.setName(itemName);
		List<Integer> list = new ArrayList<>();
		for (int id : ids)
		{
			list.add(id);
		}
		need.setItemIds(list);
		t.setRequiredItems(Collections.singletonList(need));
		return t;
	}

	static CurrentTask task(SlayerTask t, int remaining, int initial)
	{
		return new CurrentTask(t, remaining, initial, Optional.empty(), Optional.of("chaeldar"), 49);
	}

	static Monster fireGiant()
	{
		Monster m = new Monster();
		m.setId(2075);
		m.setName("Fire giant (Level 86)");
		m.setHitpoints(111);
		m.setSlayerXp(111);
		m.setMaxHit(11);
		m.setElementalWeakness("water");
		m.setElementalWeaknessPercent(50);
		m.setAttackStyles(Collections.singletonList("melee"));
		Map<String, Integer> def = new HashMap<>();
		def.put("stab", 65);
		def.put("slash", 63);
		def.put("crush", 70);
		m.setDefensiveBonuses(def);
		Drop d = new Drop();
		d.setItemId(1333);
		d.setName("Rune scimitar");
		d.setRate(1.0 / 128);
		d.setQtyMin(1);
		d.setQtyMax(1);
		m.setDrops(Collections.singletonList(d));
		return m;
	}

	static Equipment item(int id, String name, String slot)
	{
		Equipment e = new Equipment();
		e.setItemId(id);
		e.setName(name);
		e.setSlot(slot);
		return e;
	}

	static Loadout meleeLoadout()
	{
		Map<String, Equipment> slots = new HashMap<>();
		slots.put("weapon", item(WHIP, "Abyssal whip", "weapon"));
		slots.put("shield", item(12954, "Dragon defender", "shield"));
		slots.put("head", item(11864, "Slayer helmet", "head"));
		slots.put("body", item(10551, "Fighter torso", "body"));
		slots.put("legs", item(21304, "Obsidian platelegs", "legs"));
		slots.put("neck", item(6585, "Amulet of fury", "neck"));
		return new Loadout(slots, Style.MELEE, "slash", null);
	}

	static Location location(String id, String name, List<String> tags)
	{
		Location l = new Location();
		l.setId(id);
		l.setName(name);
		l.setTaskId("fire-giants");
		l.setMonsterId(2075);
		l.setTags(tags);
		return l;
	}

	static RankedLocation ranked(Location l, boolean usable, String lockedReason, boolean safespot, double travel,
		double xp, double gp, boolean recommended, String why)
	{
		RankedLocation r = new RankedLocation();
		r.setLocation(l);
		r.setUsable(usable);
		r.setLockedReason(lockedReason);
		r.setHasSafespot(safespot);
		r.setTravelSeconds(travel);
		r.setXpPerHour(xp);
		r.setGpPerHour(gp);
		r.setRecommended(recommended);
		r.setWhy(why);
		return r;
	}

	/** Fire giants: Waterfall (recommended, safespot), Catacombs, Stronghold, Karuulm (locked). */
	static List<RankedLocation> fireGiantRanking()
	{
		return Arrays.asList(
			ranked(location("fire-giants--waterfall-dungeon", "Waterfall Dungeon",
				Arrays.asList("Single-combat", "Safespot", "Cannon allowed")),
				true, null, true, 80, 31_000, 180_000, true, "Picked because it has a safespot and is closest."),
			ranked(location("fire-giants--catacombs-of-kourend", "Catacombs of Kourend",
				Arrays.asList("Multi-combat", "Safespot", "Catacombs bonus drops")),
				true, null, false, 120, 39_000, 320_000, false, null),
			ranked(location("fire-giants--stronghold-slayer-cave", "Stronghold Slayer Cave",
				Arrays.asList("Single-combat", "Safespot", "Cannon allowed")),
				true, null, false, 110, 29_000, 165_000, false, null),
			ranked(location("fire-giants--karuulm-slayer-dungeon", "Karuulm Slayer Dungeon",
				Arrays.asList("Single-combat", "Safespot", "Cannon allowed")),
				false, "Needs boots of stone", false, 120, 30_000, 170_000, false, null));
	}
}
