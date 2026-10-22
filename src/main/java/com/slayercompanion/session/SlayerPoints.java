package com.slayercompanion.session;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/**
 * Slayer reward points per completed task, from the OSRS Wiki "Slayer reward point" table
 * (checked 2026-10-21). A task pays the master's base points, times 5 on every 10th task in the streak,
 * 15 on every 50th, 25 on every 100th, 35 on every 250th and 50 on every 1000th (the largest applies).
 * <p>
 * The first four tasks of a streak pay nothing. The wiki ties this to the streak, not the account: after
 * Turael resets the streak to zero, four more tasks are needed before points resume. So the streak varbit
 * alone is enough and no lifetime task count is needed. (One line on the wiki page says "first 5"; the
 * rule text and the Turael note both say four.)
 * <p>
 * Krystilia keeps a separate wilderness streak ({@link #WILDERNESS_STREAK_VARBIT}); pass that streak for her.
 */
public final class SlayerPoints
{
	/** Current slayer reward points (varbit 4068). */
	public static final int POINTS_VARBIT = VarbitID.SLAYER_POINTS;
	/** Current task streak for standard masters (varbit 4069). */
	public static final int STREAK_VARBIT = VarbitID.SLAYER_TASKS_COMPLETED;
	/** Current task streak for Krystilia (varbit 5617). */
	public static final int WILDERNESS_STREAK_VARBIT = VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED;

	/** Tasks at the start of a streak that pay no points. */
	public static final int UNPAID_TASKS = 4;

	/** Base points: { normal, with the relevant elite diary }. */
	private static final Map<String, int[]> BASE = new HashMap<>();

	static
	{
		base(0, 0, "Turael", "Spria", "Aya");
		base(6, 6, "Mazchna", "Achtryn");
		base(8, 8, "Vannaka");
		base(10, 10, "Chaeldar");
		base(12, 15, "Nieve", "Steve");
		base(15, 15, "Duradel", "Kuradal");
		base(18, 20, "Konar", "Konar quo Maten");
		base(25, 25, "Krystilia");
	}

	private SlayerPoints()
	{
	}

	private static void base(int normal, int diary, String... masters)
	{
		for (String m : masters)
		{
			BASE.put(key(m), new int[]{normal, diary});
		}
	}

	private static String key(String master)
	{
		return master == null ? "" : master.trim().toLowerCase(Locale.ROOT);
	}

	/** Whether the master is in the points table. */
	public static boolean isKnown(String master)
	{
		return BASE.containsKey(key(master));
	}

	/**
	 * Base points per task. Elite diary raises Nieve/Steve (Western Provinces) and Konar (Kourend &amp; Kebos).
	 * Unknown masters (including Mortimer) give 0.
	 */
	public static int basePoints(String master, boolean eliteDiary)
	{
		int[] b = BASE.get(key(master));
		return b == null ? 0 : b[eliteDiary ? 1 : 0];
	}

	/** Milestone multiplier for the n-th task of a streak (1 for ordinary tasks). */
	public static int multiplier(int taskNumber)
	{
		if (taskNumber <= 0)
		{
			return 1;
		}
		if (taskNumber % 1000 == 0)
		{
			return 50;
		}
		if (taskNumber % 250 == 0)
		{
			return 35;
		}
		if (taskNumber % 100 == 0)
		{
			return 25;
		}
		if (taskNumber % 50 == 0)
		{
			return 15;
		}
		if (taskNumber % 10 == 0)
		{
			return 5;
		}
		return 1;
	}

	/** Points for finishing the next task, given the streak before it (the streak varbit's current value). */
	public static int pointsForTask(String master, int completedStreak)
	{
		return pointsForTask(master, completedStreak, false);
	}

	/** Points for finishing the next task, given the streak before it and the master's elite diary. */
	public static int pointsForTask(String master, int completedStreak, boolean eliteDiary)
	{
		int next = Math.max(0, completedStreak) + 1;
		if (next <= UNPAID_TASKS)
		{
			return 0;
		}
		return basePoints(master, eliteDiary) * multiplier(next);
	}

	/** "412 → 422": current points and the total after the award. */
	public static String summary(int current, int awarded)
	{
		return current + " → " + (current + awarded);
	}
}
