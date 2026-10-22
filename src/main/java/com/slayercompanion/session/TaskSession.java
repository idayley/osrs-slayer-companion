package com.slayercompanion.session;

import com.slayercompanion.data.Drop;
import com.slayercompanion.data.Monster;
import com.slayercompanion.task.CurrentTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Optional;
import lombok.Value;
import net.runelite.api.ItemID;

/**
 * Stats for the current slayer task. Timing starts at the first kill and excludes time spent logged out.
 */
public class TaskSession
{
	@Value
	public static class BestDrop
	{
		int itemId;
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
	/** Kills seen in the update that started the clock; time per kill only counts kills after these. */
	private int killsAtStart;
	private boolean started;
	private boolean loggedIn = true;
	private Instant runningSince;
	private Duration accumulated = Duration.ZERO;
	private int superiors;
	private BestDrop best;
	private final SupplyTracker supplies;
	private final PrayerRunway prayer = new PrayerRunway();
	private final CannonRunway cannon = new CannonRunway();
	private final WeaponCharges charges = new WeaponCharges();
	private final PickedUpLoot pickedUp = new PickedUpLoot();
	private final BagPickups bags = new BagPickups();

	/** Supplies are tracked without prices or item names (no cost, no dose families). */
	public TaskSession(Clock clock)
	{
		this(clock, new SupplyTracker(id -> 0, id -> null));
	}

	public TaskSession(Clock clock, SupplyTracker supplies)
	{
		this.clock = clock;
		this.supplies = supplies;
		// Loading the cannon is not using balls; fired balls are reported by the cannon runway instead.
		supplies.ignore(CannonRunway.BALL_IDS);
		// Charging a weapon is not using its materials; spent charges are reported by the weapon charges instead.
		supplies.ignore(WeaponCharges.MATERIAL_IDS);
		supplies.ignoreWhileCarried(WeaponCharges.BLOWPIPE_IDS, WeaponCharges.DART_IDS);
	}

	/** How long prayer lasts this task. */
	public PrayerRunway prayer()
	{
		return prayer;
	}

	/** How long cannonballs last this task. */
	public CannonRunway cannon()
	{
		return cannon;
	}

	/** @param current prayer points now (boosted level) */
	public void onPrayerPoints(int current)
	{
		prayer.onPrayerPoints(current);
	}

	/**
	 * Once per game tick, after {@link #onSupplies}: takes the cannon reading and costs fired balls as supplies.
	 *
	 * @param placed cannon set up
	 * @param ammo   balls in the cannon
	 */
	public void onCannon(boolean placed, int ammo)
	{
		cannon.update(placed, ammo, supplies.count(ItemID.STEEL_CANNONBALL), supplies.count(ItemID.GRANITE_CANNONBALL))
			.forEach(supplies::addUsed);
	}

	/** Attacks made with charged weapons this task, and the blowpipe's dart type. */
	public WeaponCharges weaponCharges()
	{
		return charges;
	}

	/**
	 * Once per game tick: counts a charged-weapon attack and costs the charge's materials as supplies.
	 * See {@link WeaponCharges#onTick} for the parameters.
	 */
	public void onWeaponTick(int weaponId, int capeId, int animation, int animationFrame, boolean animationStarted,
		int graphic, boolean graphicStarted)
	{
		charges.onTick(weaponId, capeId, animation, animationFrame, animationStarted, graphic, graphicStarted)
			.forEach(supplies::addChargeUsed);
	}

	/** A game message (tags removed); the blowpipe's "Check" message names its darts. */
	public void onGameMessage(String message)
	{
		charges.onChatMessage(message);
	}

	/** What the player has used up this task. */
	public SupplyTracker supplies()
	{
		return supplies;
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
				killsAtStart = delta;
				if (loggedIn)
				{
					runningSince = clock.instant();
				}
			}
			kills += delta;
			supplies.onKill(delta);
			prayer.onKill(delta);
			cannon.onKill(delta);
			charges.onKill(delta);
			pickedUp.onKill(now());
		}
	}

	private void reset()
	{
		remaining = 0;
		total = 0;
		kills = 0;
		killsAtStart = 0;
		started = false;
		runningSince = null;
		accumulated = Duration.ZERO;
		pickedUp.reset();
		superiors = 0;
		best = null;
		supplies.reset();
		prayer.reset();
		cannon.reset();
		charges.reset();
	}

	/**
	 * @param itemName the game's name for the item, used when the drop isn't in the selected
	 *                 monster's table (may be null)
	 */
	public void onLoot(int itemId, String itemName, int qty, long gpEach)
	{
		long value = qty * gpEach;
		pickedUp.onDrop(itemId, itemName, qty, gpEach, now());
		if (best == null || value > best.getValue())
		{
			best = new BestDrop(itemId, nameOf(itemId, itemName), value, rateOf(itemId));
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

	/** The player chose "Take" on a ground item; an open gem bag's message doesn't say which. */
	public void onGroundTake(int itemId)
	{
		bags.onTake(itemId);
	}

	/** A game message, in case an open herb sack, gem bag or seed box took task loot off the floor. */
	public void onBagMessage(String message)
	{
		BagPickups.Bagged b = bags.parse(message);
		if (b == null)
		{
			return;
		}
		if (b.name != null)
		{
			pickedUp.onBagged(b.name, b.qty, now());
		}
		else
		{
			pickedUp.onBagged(b.itemId, b.qty, now());
		}
	}

	/**
	 * While a bank or deposit box is open, items leaving the player were banked, not used. Snapshots taken
	 * while it is open become the new baseline.
	 */
	public void setBankOpen(boolean open)
	{
		supplies.setBankOpen(open);
		cannon.setBankOpen(open);
		pickedUp.setBankOpen(open);
	}

	/**
	 * @param counts item id to quantity across inventory, worn equipment and rune pouch
	 * @param looted the same plus the looting bag, for loot picked up
	 */
	public void onSupplies(Map<Integer, Integer> counts, Map<Integer, Integer> looted)
	{
		supplies.onSnapshot(counts);
		charges.onCarried(counts);
		pickedUp.onCarried(looted, now());
	}

	/** Inventory, worn and rune pouch only (no looting bag). */
	public void onSupplies(Map<Integer, Integer> counts)
	{
		onSupplies(counts, counts);
	}

	private Duration now()
	{
		return Duration.between(Instant.EPOCH, clock.instant());
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

	/**
	 * The clock starts at the first kill, so the time divides over the kills after it
	 * (usually kills - 1). Empty until there is at least one timed kill (kills >= 2).
	 */
	public OptionalDouble secondsPerKill()
	{
		int timedKills = kills - killsAtStart;
		if (!started || timedKills <= 0)
		{
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(activeSeconds() / timedKills);
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
		return kills > 0 ? OptionalDouble.of((double) pickedUp.total() / kills) : OptionalDouble.empty();
	}

	/** GP value of task drops picked up so far (left on the floor doesn't count). */
	public long lootTotal()
	{
		return pickedUp.total();
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
		return supplies.killsUntilFoodRunsOut();
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
