package com.slayercompanion.session;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PickedUpLootTest
{
	private static final int RUNE_SCIM = 1333;
	private static final int BONES = 532;
	private static final int COINS = 995;
	private static final int RANARR = 207;
	private static final int UNCUT_RUBY = 1619;

	private Duration now = Duration.ZERO;
	private final PickedUpLoot loot = new PickedUpLoot();

	private static Map<Integer, Integer> inv(int... idQty)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			m.put(idQty[i], idQty[i + 1]);
		}
		return m;
	}

	@Test
	public void countsOnlyWhatIsPickedUp()
	{
		loot.onDrop(RUNE_SCIM, 1, 15_000, now);
		loot.onDrop(BONES, 1, 100, now);
		loot.onCarried(inv(), now);
		loot.onCarried(inv(RUNE_SCIM, 1), now.plusSeconds(5)); // bones left on the floor
		assertEquals(15_000, loot.total());
	}

	@Test
	public void stackablesCountUpToWhatDropped()
	{
		loot.onDrop(COINS, 300, 1, now);
		loot.onCarried(inv(COINS, 1000), now);
		loot.onCarried(inv(COINS, 1300), now);
		assertEquals(300, loot.total());
		loot.onCarried(inv(COINS, 1700), now); // more coins than dropped: not task loot
		assertEquals(300, loot.total());
	}

	@Test
	public void dropsExpireOffTheGround()
	{
		loot.onDrop(RUNE_SCIM, 1, 15_000, now);
		loot.onCarried(inv(), now);
		loot.onCarried(inv(RUNE_SCIM, 1), now.plus(PickedUpLoot.GROUND_TIME).plusSeconds(1));
		assertEquals(0, loot.total());
	}

	@Test
	public void bankWithdrawalsAreNotLoot()
	{
		loot.onDrop(RUNE_SCIM, 1, 15_000, now);
		loot.onCarried(inv(), now);
		loot.setBankOpen(true);
		loot.onCarried(inv(RUNE_SCIM, 1), now);
		loot.setBankOpen(false);
		loot.onCarried(inv(RUNE_SCIM, 1), now);
		assertEquals(0, loot.total());
	}

	@Test
	public void droppingAndRepickingCountsOnce()
	{
		loot.onDrop(RUNE_SCIM, 1, 15_000, now);
		loot.onCarried(inv(), now);
		loot.onCarried(inv(RUNE_SCIM, 1), now);
		loot.onCarried(inv(), now);
		loot.onCarried(inv(RUNE_SCIM, 1), now);
		assertEquals(15_000, loot.total());
	}

	@Test
	public void ringOfWealthCoinsCountWhenTheyArriveWithAKill()
	{
		loot.onCarried(inv(COINS, 1000), now);
		loot.onKill(now);
		loot.onCarried(inv(COINS, 1250), now.plusMillis(600)); // never hit the ground
		assertEquals(250, loot.total());
	}

	@Test
	public void ringOfWealthCoinsJustBeforeTheKillCountToo()
	{
		loot.onCarried(inv(COINS, 1000), now);
		loot.onCarried(inv(COINS, 1250), now);
		loot.onKill(now.plusMillis(600));
		assertEquals(250, loot.total());
	}

	@Test
	public void coinsLongAfterAKillAreNotLoot()
	{
		loot.onCarried(inv(COINS, 1000), now);
		loot.onKill(now);
		loot.onCarried(inv(COINS, 5000), now.plusSeconds(30)); // e.g. sold something
		assertEquals(0, loot.total());
	}

	@Test
	public void groundCoinsAreNotCountedTwice()
	{
		loot.onCarried(inv(COINS, 1000), now);
		loot.onKill(now);
		loot.onDrop(COINS, 300, 1, now);
		loot.onCarried(inv(COINS, 1300), now.plusMillis(600));
		assertEquals(300, loot.total());
	}

	@Test
	public void herbsIntoAnOpenSackCountByName()
	{
		loot.onDrop(RANARR, "Grimy ranarr weed", 1, 7_000, now);
		loot.onCarried(inv(), now);
		loot.onBagged("grimy ranarr weed", 1, now.plusSeconds(5)); // never reaches the inventory
		assertEquals(7_000, loot.total());
		loot.onBagged("Grimy ranarr weed", 1, now.plusSeconds(6)); // e.g. from a herbiboar, not dropped
		assertEquals(7_000, loot.total());
	}

	@Test
	public void gemsIntoAnOpenBagCountById()
	{
		loot.onDrop(UNCUT_RUBY, 1, 1_000, now);
		loot.onBagged(UNCUT_RUBY, 1, now.plusSeconds(5));
		assertEquals(1_000, loot.total());
	}

	@Test
	public void baggedItemsMustMatchAPendingDrop()
	{
		loot.onDrop(RANARR, "Grimy ranarr weed", 1, 7_000, now);
		loot.onBagged("Grimy toadflax", 1, now);
		loot.onBagged(UNCUT_RUBY, 1, now);
		loot.onBagged("Grimy ranarr weed", 1, now.plus(PickedUpLoot.GROUND_TIME).plusSeconds(1)); // despawned
		assertEquals(0, loot.total());
	}

	@Test
	public void baggedSeedsCountUpToWhatDropped()
	{
		loot.onDrop(5295, "Ranarr seed", 2, 30_000, now);
		loot.onBagged("Ranarr seed", 3, now);
		assertEquals(60_000, loot.total());
	}
}
