package com.slayercompanion.ui;

import com.slayercompanion.data.Monster;
import com.slayercompanion.session.TaskSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import lombok.Value;

/** What the On task tab shows: progress, live stats, supplies and worth-knowing lines. */
@Value
public class OnTaskModel
{
	static final String STARTS_COUNTING = "Starts counting when you get your first kill";
	private static final String NONE = "—";
	/** First kill starts the clock, so timing needs a second kill to mean anything. */
	private static final int MIN_KILLS_FOR_TIMING = 2;

	@Value
	public static class Stat
	{
		String label;
		String value;
		/** Small trailing unit such as " min"; empty for none. */
		String unit;
	}

	boolean noTask;
	String message;
	String header;
	int remaining;
	int total;
	/** Fraction of the task done, 0-1. */
	double progress;
	List<Stat> stats;
	boolean timingHidden;
	String foodWarning;
	/** "{n} left" for food in the inventory, or null when unknown. */
	String foodLine;
	String prayerLabel;
	String prayer;
	String superiors;
	String bestDrop;
	/** "Toward lvl {n+1} by task end"; null at 99. */
	String levelLabel;
	String levelValue;
	String nextTask;
	String note;

	public static OnTaskModel noTask()
	{
		return new OnTaskModel(true, PrepModel.NO_TASK, null, 0, 0, 0, Collections.emptyList(), true, null, null,
			null, null, null, null, null, null, null, null);
	}

	/**
	 * @param locationName      selected location, or null
	 * @param monster           monster at the selected location (for xp per kill), or null
	 * @param foodInInventory   food items in the inventory, or negative when unknown
	 * @param slayerXp          current Slayer experience
	 * @param streak            completed-task streak
	 */
	public static OnTaskModel build(String taskName, String locationName, TaskSession s, Monster monster,
		int foodInInventory, int prayerCurrent, int prayerMax, int slayerXp, int streak)
	{
		int xpPerKill = monster == null ? 0 : monster.getSlayerXp();
		int remaining = s.remaining();
		int total = Math.max(s.total(), remaining);
		double progress = total > 0 ? (total - remaining) / (double) total : 0;
		boolean hide = s.kills() < MIN_KILLS_FOR_TIMING;

		List<Stat> stats = new ArrayList<>();
		OptionalDouble left = s.secondsLeft();
		OptionalDouble perKill = s.secondsPerKill();
		OptionalDouble kph = s.killsPerHour();
		OptionalDouble gpk = s.gpPerKill();
		stats.add(hide || !left.isPresent() ? new Stat("Time left", NONE, "")
			: new Stat("Time left", "~" + Math.round(left.getAsDouble() / 60), " min"));
		stats.add(hide || !perKill.isPresent() ? new Stat("Per kill", NONE, "")
			: new Stat("Per kill", String.valueOf(Math.round(perKill.getAsDouble())), " sec"));
		stats.add(new Stat("GP per kill", gpk.isPresent() ? Format.compact(gpk.getAsDouble()) : NONE, ""));
		stats.add(new Stat("Loot so far", Format.compact(s.lootTotal()), ""));
		stats.add(hide || !kph.isPresent() ? new Stat("Kills / hr", NONE, "")
			: new Stat("Kills / hr", String.valueOf(Math.round(kph.getAsDouble())), ""));
		stats.add(hide || !kph.isPresent() ? new Stat("Slayer XP / hr", NONE, "")
			: new Stat("Slayer XP / hr", Format.compact(s.slayerXpPerHour(xpPerKill)), ""));

		OptionalInt foodKills = s.killsUntilFoodRunsOut();
		String foodWarning = foodKills.isPresent() && foodKills.getAsInt() < remaining
			? "Food runs out in ~" + foodKills.getAsInt() + " kills" : null;

		String bestDrop = s.bestDrop().map(b -> b.getName() + " · " + Format.compact(b.getValue())
			+ (b.getRate() > 0 ? " 1/" + Math.round(1 / b.getRate()) : "")).orElse("None yet");

		String levelLabel = null;
		String levelValue = null;
		int endLevel = TaskSession.levelForXp((long) slayerXp + (long) remaining * xpPerKill);
		if (endLevel < 99)
		{
			levelLabel = "Toward lvl " + (endLevel + 1) + " by task end";
			levelValue = Math.round(s.levelProgressAtTaskEnd(slayerXp, xpPerKill) * 100) + "%";
		}

		int next = streak + 1;
		String nextTask = s.nextTaskIsBonus(streak) ? Format.ordinal(next) + ": bonus points"
			: Format.ordinal(next) + ": no bonus";

		String header = locationName == null ? taskName : taskName + " · " + locationName;
		return new OnTaskModel(false, null, header, remaining, total, progress, stats, hide, foodWarning,
			foodInInventory >= 0 ? foodInInventory + " left" : null,
			"Prayer", prayerCurrent + " / " + prayerMax,
			String.valueOf(s.superiors()), bestDrop, levelLabel, levelValue, nextTask,
			hide ? STARTS_COUNTING : "Times are estimated from your kills so far");
	}
}
