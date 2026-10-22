package com.slayercompanion.session;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the game messages for items an open herb sack, gem bag or seed box takes straight off the floor.
 * None of these bags is a readable item container, so the message is the only sign. The herb sack and seed
 * box messages name the item; the gem bag's doesn't, so it is the ground item the player last chose to take.
 */
public class BagPickups
{
	/** One pickup: a name (herb sack, seed box) or an item id (gem bag). */
	public static final class Bagged
	{
		final String name;
		final int itemId;
		final int qty;

		Bagged(String name, int itemId, int qty)
		{
			this.name = name;
			this.itemId = itemId;
			this.qty = qty;
		}
	}

	private static final Pattern HERB = Pattern.compile("You put the (.+?)(?: herb)? into your (?:open )?herb sack\\.");
	private static final Pattern SEED = Pattern.compile("You put (\\d+) x (.+?) straight into your open seed box\\.");
	private static final Pattern GEM = Pattern.compile("You put it straight into your open gem \\w+\\.");

	private int lastTaken = -1;

	/** The player chose "Take" on a ground item. */
	public void onTake(int itemId)
	{
		lastTaken = itemId;
	}

	/** @return what a bag took, or null if the message isn't a bag pickup */
	public Bagged parse(String message)
	{
		Matcher m = HERB.matcher(message);
		if (m.matches())
		{
			return new Bagged(m.group(1), -1, 1);
		}
		m = SEED.matcher(message);
		if (m.matches())
		{
			return new Bagged(m.group(2), -1, Integer.parseInt(m.group(1)));
		}
		if (GEM.matcher(message).matches() && lastTaken != -1)
		{
			Bagged b = new Bagged(null, lastTaken, 1);
			lastTaken = -1;
			return b;
		}
		return null;
	}
}
