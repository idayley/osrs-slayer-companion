package com.slayercompanion.session;

import com.slayercompanion.data.Drop;
import com.slayercompanion.data.Monster;
import com.slayercompanion.task.CurrentTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Optional;
import lombok.Value;

/**
 * Stats for the current slayer task. Timing starts at the first kill and excludes time spent logged out.
 */
public class TaskSession
{
	@Value
	public static class BestDrop
	{
		String name;
		long value;
		double rate;
	}

	private final Clock clock;

	private Monster monster;
	private String taskName;
	private int remaining;
	private int total;
	private boolean hasTask;
	private int kills;
	private boolean started;
	private boolean loggedIn = true;
	private Instant runningSince;
	private Duration accumulated = Duration.ZERO;
	private long lootTotal;
	private int superiors;
	private BestDrop best;
	private int lastFood = -1;
	private int foodEaten;
	private boolean bankOpen;

	public TaskSession(Clock clock)
	{
		this.clock = clock;
	}

	/** Sets the monster being killed so drop rates can be looked up by item id. */
	public void setMonster(Monster monster)
	{
		this.monster = monster;
	}

	public void onTaskUpdate(CurrentTask current)
	{
		if (current == null)
		{
			reset();
			hasTask = false;
			return;
		}
		String name = current.getTask() == null ? null : current.getTask().getName();
		boolean sameTask = hasTask && java.util.Objects.equals(name, taskName)
			&& current.getRemaining() <= remaining;
		if (!sameTask)
		{
			reset();
			hasTask = true;
			taskName = name;
			remaining = current.getRemaining();
			total = current.getInitialAmount();
			return;
		}
		int delta = remaining - current.getRemaining();
		remaining = current.getRemaining();
		total = current.getInitialAmount();
		if (delta > 0)
		{
			if (!started)
			{
				started = true;
				if (loggedIn)
				{
					runningSince = clock.instant();
				}
			}
			kills += delta;
		}
	}

	private void reset()
	{
		remaining = 0;
		total = 0;
		kills = 0;
		started = false;
		runningSince = null;
		accumulated = Duration.ZERO;
		lootTotal = 0;
		superiors = 0;
		best = null;
		foodEaten = 0;
	}

	/**
	 * @param itemName the game's name for the item, used when the drop isn't in the selected
	 *                 monster's table (may be null)
	 */
	public void onLoot(int itemId, String itemName, int qty, long gpEach)
	{
		long value = qty * gpEach;
		lootTotal += value;
		if (best == null || value > best.getValue())
		{
			best = new BestDrop(nameOf(itemId, itemName), value, rateOf(itemId));
		}
	}

	private Drop dropOf(int itemId)
	{
		if (monster == null)
		{
			return null;
		}
		for (Drop d : monster.getDrops())
		{
			if (d.getItemId() == itemId)
			{
				return d;
			}
		}
		return null;
	}

	private String nameOf(int itemId, String itemName)
	{
		Drop d = dropOf(itemId);
		if (d != null && d.getName() != null)
		{
			return d.getName();
		}
		return itemName != null && !itemName.isEmpty() ? itemName : "Item " + itemId;
	}

	private double rateOf(int itemId)
	{
		Drop d = dropOf(itemId);
		return d == null ? 0 : d.getRate();
	}

	public void onSuperior()
	{
		superiors++;
	}

	public void onLoggedOut()
	{
		if (runningSince != null)
		{
			accumulated = accumulated.plus(Duration.between(runningSince, clock.instant()));
			runningSince = null;
		}
		loggedIn = false;
	}

	public void onLoggedIn()
	{
		loggedIn = true;
		if (started && runningSince == null)
		{
			runningSince = clock.instant();
		}
	}

	/**
	 * While a bank or deposit box is open, food leaving the inventory was banked, not eaten.
	 * On close the count is re-baselined to {@code foodInInventory}.
	 */
	public void setBankOpen(boolean open, int foodInInventory)
	{
		bankOpen = open;
		if (!open)
		{
			lastFood = foodInInventory;
		}
	}

	public void onFoodCount(int foodInInventory)
	{
		if (lastFood >= 0 && started && !bankOpen && foodInInventory < lastFood)
		{
			foodEaten += lastFood - foodInInventory;
		}
		lastFood = foodInInventory;
	}

	public int remaining()
	{
		return remaining;
	}

	public int total()
	{
		return total;
	}

	public int kills()
	{
		return kills;
	}

	private double activeSeconds()
	{
		Duration d = accumulated;
		if (runningSince != null)
		{
			d = d.plus(Duration.between(runningSince, clock.instant()));
		}
		return d.toMillis() / 1000.0;
	}

	public OptionalDouble secondsPerKill()
	{
		if (!started || kills <= 0)
		{
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(activeSeconds() / kills);
	}

	public OptionalDouble killsPerHour()
	{
		OptionalDouble spk = secondsPerKill();
		return spk.isPresent() && spk.getAsDouble() > 0 ? OptionalDouble.of(3600.0 / spk.getAsDouble()) : OptionalDouble.empty();
	}

	public OptionalDouble secondsLeft()
	{
		OptionalDouble spk = secondsPerKill();
		return spk.isPresent() ? OptionalDouble.of(spk.getAsDouble() * remaining) : OptionalDouble.empty();
	}

	public OptionalDouble gpPerKill()
	{
		return kills > 0 ? OptionalDouble.of((double) lootTotal / kills) : OptionalDouble.empty();
	}

	public long lootTotal()
	{
		return lootTotal;
	}

	public double slayerXpPerHour(int xpPerKill)
	{
		OptionalDouble kph = killsPerHour();
		return kph.isPresent() ? kph.getAsDouble() * xpPerKill : 0;
	}

	public int superiors()
	{
		return superiors;
	}

	public Optional<BestDrop> bestDrop()
	{
		return Optional.ofNullable(best);
	}

	public OptionalInt killsUntilFoodRunsOut()
	{
		if (kills <= 0 || foodEaten <= 0 || lastFood < 0)
		{
			return OptionalInt.empty();
		}
		double perKill = (double) foodEaten / kills;
		return OptionalInt.of((int) Math.floor(lastFood / perKill));
	}

	/** Fraction (0-1) of the way to the next level after finishing the task. */
	public double levelProgressAtTaskEnd(int currentXp, int xpPerKill)
	{
		long xp = (long) currentXp + (long) remaining * xpPerKill;
		int level = levelForXp(xp);
		if (level >= 99)
		{
			return 1.0;
		}
		long lo = xpForLevel(level);
		long hi = xpForLevel(level + 1);
		return (xp - lo) / (double) (hi - lo);
	}

	static long xpForLevel(int level)
	{
		double points = 0;
		for (int l = 1; l < level; l++)
		{
			points += Math.floor(l + 300 * Math.pow(2, l / 7.0));
		}
		return (long) Math.floor(points / 4);
	}

	public static int levelForXp(long xp)
	{
		int level = 1;
		while (level < 99 && xpForLevel(level + 1) <= xp)
		{
			level++;
		}
		return level;
	}

	/** True when the next task's streak value is a multiple of 10. */
	public boolean nextTaskIsBonus(int streak)
	{
		return (streak + 1) % 10 == 0;
	}
}
