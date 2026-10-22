package com.slayercompanion.session;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import net.runelite.api.ItemID;
import net.runelite.api.gameval.VarPlayerID;

/**
 * How many kills the dwarf multicannon's cannonballs last, from balls in the cannon plus balls carried.
 * <p>
 * Feed it once per game tick with {@link #update}, so the cannon varp and the inventory are read together:
 * loading moves balls from the inventory into the cannon and leaves the total unchanged. Rule: a decrease
 * of the total (in cannon + carried) while the cannon stays placed, no bank is open, and after the task's
 * first kill, was fired. Placing or picking up the cannon resets the baseline, so balls returned on pickup
 * are not counted either way.
 * <p>
 * Supply cost: cannonballs leave the inventory when loaded, not when fired. Call
 * {@link SupplyTracker#ignore} with {@link #BALL_IDS} so loading is not costed, and pass the balls
 * {@link #update} reports as fired to {@link SupplyTracker#addUsed}; each ball is then costed once, when fired.
 */
public class CannonRunway
{
	/** Cannonballs in the cannon. RuneLite {@code VarPlayerID.ROCKTHROWER} (3), read by RuneLite's CannonPlugin. */
	public static final int AMMO_VARP = VarPlayerID.ROCKTHROWER;
	/** Cannon state. RuneLite {@code VarPlayerID.DROPCANNON} (2); CannonPlugin treats {@link #STATE_PLACED} as placed. */
	public static final int STATE_VARP = VarPlayerID.DROPCANNON;
	public static final int STATE_PLACED = 4;
	/** Ammo the dwarf multicannon fires: steel and granite cannonballs. */
	public static final Set<Integer> BALL_IDS = Set.of(ItemID.STEEL_CANNONBALL, ItemID.GRANITE_CANNONBALL);

	private boolean placed;
	private boolean bankOpen;
	private boolean started;
	private int kills;
	private long used;
	private int ammo;
	private int steel;
	private int granite;
	/** The ball type last seen going into the cannon; fired balls are costed as this type. */
	private int loadedId = ItemID.STEEL_CANNONBALL;

	/** Whether a cannon state varp value means the cannon is set up. */
	public static boolean isPlaced(int stateVarp)
	{
		return stateVarp == STATE_PLACED;
	}

	/** Clears usage and kills for a new task. The cannon and ball counts are kept. */
	public void reset()
	{
		started = false;
		kills = 0;
		used = 0;
	}

	/** Records {@code n} task kills; the first one starts counting. */
	public void onKill(int n)
	{
		if (n > 0)
		{
			started = true;
			kills += n;
		}
	}

	/** While a bank or deposit box is open, carried balls going down were banked, not fired. */
	public void setBankOpen(boolean open)
	{
		bankOpen = open;
	}

	/**
	 * Takes this tick's cannon and carried-ball readings.
	 *
	 * @param placed  cannon set up ({@link #isPlaced} of the {@link #STATE_VARP} value)
	 * @param ammo    {@link #AMMO_VARP} value
	 * @param steel   steel cannonballs carried
	 * @param granite granite cannonballs carried
	 * @return balls fired since the last update, item id to quantity; empty when none
	 */
	public Map<Integer, Integer> update(boolean placed, int ammo, int steel, int granite)
	{
		ammo = Math.max(0, ammo);
		Map<Integer, Integer> fired = Collections.emptyMap();
		if (placed && this.placed)
		{
			if (ammo > this.ammo)
			{
				if (granite < this.granite)
				{
					loadedId = ItemID.GRANITE_CANNONBALL;
				}
				else if (steel < this.steel)
				{
					loadedId = ItemID.STEEL_CANNONBALL;
				}
			}
			int diff = (this.ammo + this.steel + this.granite) - (ammo + steel + granite);
			if (diff > 0 && started && !bankOpen)
			{
				used += diff;
				fired = Collections.singletonMap(loadedId, diff);
			}
		}
		this.placed = placed;
		this.ammo = placed ? ammo : 0;
		this.steel = steel;
		this.granite = granite;
		return fired;
	}

	public boolean placed()
	{
		return placed;
	}

	public int kills()
	{
		return kills;
	}

	/** Balls fired this task. */
	public long ballsUsed()
	{
		return used;
	}

	/** Balls in the cannon plus carried. */
	public int ballsLeft()
	{
		return ammo + steel + granite;
	}

	/** Balls fired per kill; empty before the first kill. */
	public OptionalDouble ballsPerKill()
	{
		return kills > 0 ? OptionalDouble.of(used / (double) kills) : OptionalDouble.empty();
	}

	/** Kills until the balls run out; empty with no cannon placed or no balls fired yet. */
	public OptionalInt killsLeft()
	{
		if (!placed || kills <= 0 || used <= 0)
		{
			return OptionalInt.empty();
		}
		return OptionalInt.of((int) Math.floor(ballsLeft() / (used / (double) kills)));
	}

	/** Panel line, e.g. "~30 kills · 412 balls"; empty with no cannon placed. */
	public Optional<String> summary()
	{
		return placed ? Optional.of(line(killsLeft(), ballsLeft())) : Optional.empty();
	}

	/** Shortfall warning against the kills left on task; empty with no cannon placed or no shortfall. */
	public Optional<String> warning(int taskRemaining)
	{
		return placed ? warning(killsLeft(), taskRemaining) : Optional.empty();
	}

	/** "~30 kills · 412 balls", or just "412 balls" before a rate is known. */
	public static String line(OptionalInt killsLeft, int balls)
	{
		String b = balls + (balls == 1 ? " ball" : " balls");
		if (!killsLeft.isPresent())
		{
			return b;
		}
		int k = killsLeft.getAsInt();
		return "~" + k + (k == 1 ? " kill · " : " kills · ") + b;
	}

	/** "Cannonballs run out ~12 kills before task end" when {@code killsLeft} falls short of the task. */
	public static Optional<String> warning(OptionalInt killsLeft, int taskRemaining)
	{
		if (!killsLeft.isPresent() || killsLeft.getAsInt() >= taskRemaining)
		{
			return Optional.empty();
		}
		int shortBy = taskRemaining - killsLeft.getAsInt();
		return Optional.of("Cannonballs run out ~" + shortBy + (shortBy == 1 ? " kill" : " kills") + " before task end");
	}
}
