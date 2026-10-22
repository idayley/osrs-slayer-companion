package com.slayercompanion.session;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Value of task loot the player actually picks up. Coins a ring of wealth (i) collects straight into the
 * inventory never touch the ground, so unmatched coins gained within {@link #AUTO_COLLECT} of a task kill count too.
 * Each task kill's drop waits here for {@link #GROUND_TIME};
 * an item of the same id arriving in the player's carried items (inventory, worn, looting bag) within that
 * time counts as picked up, up to the dropped quantity. Open herb sacks, gem bags and seed boxes take items
 * straight off the floor; those arrive via {@link #onBagged} instead. Times are offsets from any fixed start.
 */
public class PickedUpLoot
{
	/** How long a drop stays on the ground (one minute private, two public). */
	static final Duration GROUND_TIME = Duration.ofMinutes(3);

	private static final class OnGround
	{
		final int itemId;
		final String name;
		int qty;
		final long gpEach;
		final Duration expires;

		OnGround(int itemId, String name, int qty, long gpEach, Duration expires)
		{
			this.itemId = itemId;
			this.name = name;
			this.qty = qty;
			this.gpEach = gpEach;
			this.expires = expires;
		}
	}

	/** How close to a kill auto-collected coins arrive, either side (the kill count can update first or last). */
	static final Duration AUTO_COLLECT = Duration.ofSeconds(3);
	private static final int COINS = 995;

	private final List<OnGround> ground = new ArrayList<>();
	private Duration lastKill;
	/** Unmatched coins gained recently, waiting for a kill; and when. */
	private int looseCoins;
	private Duration looseCoinsAt;
	private Map<Integer, Integer> last;
	private boolean bankOpen;
	private long total;

	public void reset()
	{
		ground.clear();
		total = 0;
		lastKill = null;
		looseCoins = 0;
	}

	public void onDrop(int itemId, int qty, long gpEach, Duration now)
	{
		onDrop(itemId, null, qty, gpEach, now);
	}

	/** @param name the game's name for the item, so bag pickups that only name it can match (may be null) */
	public void onDrop(int itemId, String name, int qty, long gpEach, Duration now)
	{
		ground.add(new OnGround(itemId, name, qty, gpEach, now.plus(GROUND_TIME)));
	}

	/** Items an open bag took straight off the floor, named by the game's message. */
	public void onBagged(String name, int qty, Duration now)
	{
		expire(now);
		pickUp(g -> g.name != null && g.name.equalsIgnoreCase(name), qty);
	}

	/** Items an open bag took straight off the floor, known by id. */
	public void onBagged(int itemId, int qty, Duration now)
	{
		expire(now);
		pickUp(g -> g.itemId == itemId, qty);
	}

	private void expire(Duration now)
	{
		ground.removeIf(g -> g.qty <= 0 || now.compareTo(g.expires) > 0);
	}

	/** A task kill: claims coins that arrived just before it and opens a window for ones just after. */
	public void onKill(Duration now)
	{
		lastKill = now;
		if (looseCoins > 0 && within(looseCoinsAt, now))
		{
			total += looseCoins;
		}
		looseCoins = 0;
	}

	private static boolean within(Duration a, Duration b)
	{
		return a != null && a.minus(b).abs().compareTo(AUTO_COLLECT) <= 0;
	}

	/** While a bank or deposit box is open, gains were withdrawn, not picked up. */
	public void setBankOpen(boolean open)
	{
		bankOpen = open;
	}

	/** @param counts item id to quantity across everything the player carries */
	public void onCarried(Map<Integer, Integer> counts, Duration now)
	{
		expire(now);
		if (last != null && !bankOpen)
		{
			for (Map.Entry<Integer, Integer> e : counts.entrySet())
			{
				int gained = e.getValue() - last.getOrDefault(e.getKey(), 0);
				if (gained > 0)
				{
					int itemId = e.getKey();
					int left = pickUp(g -> g.itemId == itemId, gained);
					if (e.getKey() == COINS && left > 0)
					{
						autoCollected(left, now);
					}
				}
			}
		}
		last = new HashMap<>(counts);
	}

	private void autoCollected(int coins, Duration now)
	{
		if (within(lastKill, now))
		{
			total += coins;
		}
		else
		{
			looseCoins = coins;
			looseCoinsAt = now;
		}
	}

	/** @return how much of the gain matched no drop on the ground */
	private int pickUp(Predicate<OnGround> matches, int gained)
	{
		for (Iterator<OnGround> it = ground.iterator(); it.hasNext() && gained > 0; )
		{
			OnGround g = it.next();
			if (!matches.test(g))
			{
				continue;
			}
			int n = Math.min(g.qty, gained);
			total += n * g.gpEach;
			g.qty -= n;
			gained -= n;
			if (g.qty == 0)
			{
				it.remove();
			}
		}
		return gained;
	}

	/** GP value picked up this task. */
	public long total()
	{
		return total;
	}
}
