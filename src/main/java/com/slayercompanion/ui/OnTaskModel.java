package com.slayercompanion.ui;

import com.slayercompanion.data.Monster;
import com.slayercompanion.session.CannonRunway;
import com.slayercompanion.session.FinishingItems;
import com.slayercompanion.session.PrayerRunway;
import com.slayercompanion.session.SlayerPoints;
import com.slayercompanion.session.TaskSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import lombok.Value;
import net.runelite.api.ItemID;

/** What the On task tab shows: progress, live stats, supplies and worth-knowing lines. */
@Value
public class OnTaskModel
{
	static final String STARTS_COUNTING = "Tracking starts at first kill";
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

	/** One row of the Supplies list: what it is, how long it lasts and how that compares to the task. */
	@Value
	public static class Supply
	{
		/** Shown name: "Food", "Prayer", "Fungicide charges", "Cannonballs". */
		String label;
		/** Item picture id, or -1 to use {@link #skillIcon}. */
		int iconItemId;
		/** Bundled skill icon name ("prayer"), or null. */
		String skillIcon;
		/** Right-hand text, e.g. "lasts ~120 kills". */
		String value;
		/** Shortfall warning shown above the row, or null. */
		String warning;
		/** Kills this supply lasts, or -1 before there is an estimate (no gauge then). */
		int killsItLasts;
		/** Bar fill, min(1, lasts / kills left); -1 with no estimate. */
		double fraction;
		/** Where the task ends on the bar (kills left / lasts) when the supply outlasts the task; -1 for none. */
		double taskEndAt;
		/** Runs out before the task ends. */
		boolean runsShort;

		static Supply of(String label, int iconItemId, String skillIcon, String value, String warning,
			OptionalInt lasts, int killsLeft)
		{
			if (!lasts.isPresent() || killsLeft <= 0)
			{
				return new Supply(label, iconItemId, skillIcon, value, warning, lasts.orElse(-1), -1, -1,
					warning != null);
			}
			int k = Math.max(0, lasts.getAsInt());
			double fraction = Math.min(1, k / (double) killsLeft);
			double end = k > killsLeft ? killsLeft / (double) k : -1;
			return new Supply(label, iconItemId, skillIcon, value, warning, k, fraction, end, k < killsLeft);
		}
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
	/** "{n} left" for food in the inventory, plus " · lasts ~N kills" when known; null when unknown. */
	String foodLine;
	String prayerWarning;
	String prayerLabel;
	/** "Lasts ~25 kills", or "43 prayer points" before any drain is seen. */
	String prayer;
	String finishingWarning;
	/** Finishing item label, e.g. "Ice coolers"; null for tasks that need none. */
	String finishingLabel;
	/** "23 · 40 left"; null for tasks that need none. */
	String finishingLine;
	String cannonWarning;
	/** "~30 kills · 412 balls"; null with no cannon placed. */
	String cannonLine;
	String superiors;
	String bestDrop;
	/** "Toward lvl {n+1} by task end"; null at 99. */
	String levelLabel;
	String levelValue;
	String nextTask;
	/** "412 → 422", or just the current points when the master is unknown; null when points are unknown. */
	String slayerPoints;
	String note;
	/** Task name alone, for the hero title. */
	String taskName;
	/** Selected location, or null. */
	String locationName;
	/** "55 of 142 done", plus " · ~46 min left" once timing is known. */
	String progressLine;
	/** Supplies rows in display order (food, prayer, finishing item, cannonballs); only rows that apply. */
	List<Supply> supplies;
	/** Item id of the best drop for its icon, or -1. */
	int bestDropItemId;
	/** Fraction (0-1) toward the next level at task end; -1 at 99. */
	double levelFraction;
	/** Profit per kill is known and below zero. */
	boolean profitNegative;

	public static OnTaskModel noTask()
	{
		return new OnTaskModel(true, PrepModel.NO_TASK, null, 0, 0, 0, Collections.emptyList(), true, null, null,
			null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
			null, null, null, Collections.emptyList(), -1, -1, false);
	}

	/**
	 * @param locationName      selected location, or null
	 * @param monster           monster at the selected location (for xp per kill), or null
	 * @param foodInInventory   food items in the inventory, or negative when unknown
	 * @param slayerXp          current Slayer experience
	 * @param prayerCurrent     prayer points now
	 * @param prayerMax         base prayer level
	 * @param streak            completed-task streak
	 * @param slayerPoints      current slayer reward points, or negative when unknown
	 * @param master            lowercase master id, or null when unknown
	 * @param pointsStreak      streak that decides this master's points (Krystilia's wilderness streak for her)
	 */
	public static OnTaskModel build(String taskName, String locationName, TaskSession s, Monster monster,
		int foodInInventory, int prayerCurrent, int prayerMax, int slayerXp, int streak, int slayerPoints,
		String master, int pointsStreak)
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
			: new Stat("Per kill", String.valueOf(Math.round(perKill.getAsDouble())), "s"));
		OptionalDouble cpk = s.supplies().costPerKill();
		stats.add(new Stat("GP per kill", gpk.isPresent() ? Format.compact(gpk.getAsDouble()) : NONE, ""));
		stats.add(new Stat("Supplies / kill", cpk.isPresent() ? Format.compact(cpk.getAsDouble()) : NONE, ""));
		stats.add(new Stat("Profit / kill", gpk.isPresent() && cpk.isPresent()
			? Format.compact(gpk.getAsDouble() - cpk.getAsDouble()) : NONE, ""));
		stats.add(new Stat("Loot so far", Format.compact(s.lootTotal()), ""));
		stats.add(hide || !kph.isPresent() ? new Stat("Kills / hr", NONE, "")
			: new Stat("Kills / hr", String.valueOf(Math.round(kph.getAsDouble())), ""));
		stats.add(hide || !kph.isPresent() ? new Stat("Slayer XP / hr", NONE, "")
			: new Stat("Slayer XP / hr", Format.compact(s.slayerXpPerHour(xpPerKill)), ""));

		OptionalInt foodKills = s.killsUntilFoodRunsOut();
		String foodWarning = foodKills.isPresent() && foodKills.getAsInt() < remaining
			? "Food runs out in ~" + foodKills.getAsInt() + " kills" : null;
		String foodLine = foodInInventory < 0 ? null : foodInInventory + " left"
			+ (foodKills.isPresent() ? " · lasts ~" + foodKills.getAsInt() + " kills" : "");

		OptionalInt prayerKills = s.prayer().killsLeft(prayerCurrent, s.supplies(), prayerMax);
		String prayerWarning = PrayerRunway.warning(prayerKills, remaining).orElse(null);

		String finishingLabel = null;
		String finishingLine = null;
		String finishingWarning = null;
		FinishingItems.Need need = FinishingItems.forTask(taskName).orElse(null);
		if (need != null && remaining > 0)
		{
			int uses = need.usesCarried(s.supplies());
			finishingLabel = need.getLabel();
			finishingLine = (uses > 0 ? String.valueOf(uses) : "None") + " · " + remaining + " left";
			finishingWarning = need.warning(uses, remaining).orElse(null);
		}

		CannonRunway cannon = s.cannon();
		String cannonLine = cannon.summary().orElse(null);
		String cannonWarning = cannon.warning(remaining).orElse(null);

		String points = null;
		if (slayerPoints >= 0)
		{
			points = master != null && SlayerPoints.isKnown(master)
				? SlayerPoints.summary(slayerPoints, SlayerPoints.pointsForTask(master, pointsStreak))
				: String.valueOf(slayerPoints);
		}

		String bestDrop = s.bestDrop().map(b -> b.getName() + " · " + Format.compact(b.getValue())
			+ (b.getRate() > 0 ? " 1/" + Math.round(1 / b.getRate()) : "")).orElse("None yet");

		String levelLabel = null;
		String levelValue = null;
		double levelFraction = -1;
		int endLevel = TaskSession.levelForXp((long) slayerXp + (long) remaining * xpPerKill);
		if (endLevel < 99)
		{
			levelLabel = "Toward lvl " + (endLevel + 1) + " by task end";
			levelFraction = s.levelProgressAtTaskEnd(slayerXp, xpPerKill);
			levelValue = Math.round(levelFraction * 100) + "%";
		}

		// The streak counts finished tasks, so the one in progress is streak + 1.
		int thisTask = streak + 1;
		String nextTask = s.nextTaskIsBonus(streak) ? Format.ordinal(thisTask) + " · bonus points"
			: Format.ordinal(thisTask) + " · bonus on " + Format.ordinal((thisTask / 10 + 1) * 10);

		List<Supply> supplies = new ArrayList<>();
		if (foodLine != null)
		{
			int foodId = s.supplies().mainFoodId();
			supplies.add(Supply.of("Food", foodId > 0 ? foodId : ItemID.SHARK, null, foodLine, foodWarning, foodKills,
				remaining));
		}
		String prayerLine = PrayerRunway.line(prayerKills, prayerCurrent);
		supplies.add(Supply.of("Prayer", -1, "prayer", prayerLine, prayerWarning, prayerKills, remaining));
		if (finishingLine != null)
		{
			supplies.add(Supply.of(finishingLabel, finishingIcon(need, s), null, finishingLine, finishingWarning,
				OptionalInt.of(need.usesCarried(s.supplies())), remaining));
		}
		if (cannonLine != null)
		{
			supplies.add(Supply.of("Cannonballs", ItemID.STEEL_CANNONBALL, null, cannonLine, cannonWarning,
				cannon.killsLeft(), remaining));
		}

		String progressLine = (total - remaining) + " of " + total + " done"
			+ (hide || !left.isPresent() ? "" : " · ~" + Math.round(left.getAsDouble() / 60) + " min left");
		boolean profitNegative = gpk.isPresent() && cpk.isPresent()
			&& Math.round(gpk.getAsDouble() - cpk.getAsDouble()) < 0;
		int bestId = s.bestDrop().map(TaskSession.BestDrop::getItemId).orElse(-1);

		String header = locationName == null ? taskName : taskName + " · " + locationName;
		return new OnTaskModel(false, null, header, remaining, total, progress, stats, hide, foodWarning, foodLine,
			prayerWarning, "Prayer", PrayerRunway.line(prayerKills, prayerCurrent),
			finishingWarning, finishingLabel, finishingLine, cannonWarning, cannonLine,
			String.valueOf(s.superiors()), bestDrop, levelLabel, levelValue, nextTask, points,
			hide ? STARTS_COUNTING : "Estimated from kills this task", taskName, locationName, progressLine,
			supplies, bestId, levelFraction, profitNegative);
	}

	/** The carried item of a finishing need with the most uses (a fungicide spray), else its fullest item. */
	private static int finishingIcon(FinishingItems.Need need, TaskSession s)
	{
		int icon = -1;
		int best = -1;
		for (java.util.Map.Entry<Integer, Integer> e : need.getUsesPerItem().entrySet())
		{
			int score = e.getValue() + (s.supplies().count(e.getKey()) > 0 ? 1000 : 0);
			if (score > best)
			{
				best = score;
				icon = e.getKey();
			}
		}
		return icon;
	}
}
