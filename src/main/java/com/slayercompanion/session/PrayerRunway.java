package com.slayercompanion.session;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Estimates how many more kills the player's prayer lasts.
 * <p>
 * Drain is the sum of every drop in prayer points seen after the task's first kill. Drinking a potion
 * raises points, so increases are ignored and potions need no special handling. Drain per kill only counts
 * kills after the first update, since the drain before it was not watched.
 * <p>
 * Restore per dose (OSRS Wiki): Prayer potion 7 + floor(level / 4); Super restore, Sanfew serum and
 * Blighted super restore 8 + floor(level / 4). Holy wrench and prayer cape bonuses are ignored, as is
 * restoring past the base level, so the estimate errs low for those players.
 */
public class PrayerRunway
{
	private int last = -1;
	private boolean started;
	private int kills;
	private int killsAtStart;
	private long drained;

	/** Clears drain and kills for a new task. The last point reading is kept as the baseline. */
	public void reset()
	{
		started = false;
		kills = 0;
		killsAtStart = 0;
		drained = 0;
	}

	/** Records {@code n} task kills; the first update starts drain counting. */
	public void onKill(int n)
	{
		if (n <= 0)
		{
			return;
		}
		if (!started)
		{
			started = true;
			killsAtStart = n;
		}
		kills += n;
	}

	/** @param current prayer points now (boosted level), from StatChanged */
	public void onPrayerPoints(int current)
	{
		if (last >= 0 && started && current < last)
		{
			drained += last - current;
		}
		last = current;
	}

	/** Prayer points drained since the first kill. */
	public long drained()
	{
		return drained;
	}

	/** Points drained per kill; empty before any measured kill or when no drain was seen (not praying). */
	public OptionalDouble drainPerKill()
	{
		int counted = kills - killsAtStart;
		if (counted <= 0 || drained <= 0)
		{
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(drained / (double) counted);
	}

	/** Points one dose of the family restores at the given base prayer level; 0 for other families. */
	public static int restorePerDose(String family, int prayerLevel)
	{
		int bonus = Math.max(0, prayerLevel) / 4;
		if ("Prayer potion".equals(family))
		{
			return 7 + bonus;
		}
		if ("Super restore".equals(family) || "Sanfew serum".equals(family)
			|| "Blighted super restore".equals(family))
		{
			return 8 + bonus;
		}
		return 0;
	}

	/**
	 * Kills left before prayer runs out, counting carried doses at the Prayer potion rate (the lower one).
	 * Empty when no drain was seen; show plain points then.
	 */
	public OptionalInt killsLeft(int currentPoints, int dosesCarried, int prayerLevel)
	{
		long points = currentPoints + (long) dosesCarried * restorePerDose("Prayer potion", prayerLevel);
		return killsLeftFor(points);
	}

	/** Kills left, counting each carried dose family from the tracker at its own restore rate. */
	public OptionalInt killsLeft(int currentPoints, SupplyTracker supplies, int prayerLevel)
	{
		long points = currentPoints;
		for (String family : SupplyTracker.PRAYER_FAMILIES)
		{
			points += (long) supplies.doses(Set.of(family)) * restorePerDose(family, prayerLevel);
		}
		return killsLeftFor(points);
	}

	private OptionalInt killsLeftFor(long points)
	{
		OptionalDouble perKill = drainPerKill();
		if (!perKill.isPresent())
		{
			return OptionalInt.empty();
		}
		return OptionalInt.of((int) Math.floor(Math.max(0, points) / perKill.getAsDouble()));
	}

	/** "Lasts ~25 kills", or plain points ("43 prayer points") when there is no estimate. */
	public static String line(OptionalInt killsLeft, int currentPoints)
	{
		if (!killsLeft.isPresent())
		{
			return currentPoints + " prayer points";
		}
		int k = killsLeft.getAsInt();
		return "Lasts ~" + k + (k == 1 ? " kill" : " kills");
	}

	/** "Prayer runs out ~15 kills before task end" when prayer will not last the task; empty otherwise. */
	public static Optional<String> warning(OptionalInt killsLeft, int killsRemaining)
	{
		if (!killsLeft.isPresent() || killsLeft.getAsInt() >= killsRemaining)
		{
			return Optional.empty();
		}
		int gap = killsRemaining - killsLeft.getAsInt();
		return Optional.of("Prayer runs out ~" + gap + (gap == 1 ? " kill" : " kills") + " before task end");
	}
}
