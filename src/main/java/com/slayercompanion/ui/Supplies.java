package com.slayercompanion.ui;

import com.slayercompanion.data.ItemNeed;
import com.slayercompanion.data.Monster;
import com.slayercompanion.session.FoodItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.gameval.ItemID;

/**
 * Supplies to bring when the task data lists none. Rule: always Food; Antivenom if the monster is venomous,
 * otherwise Antipoison if it is poisonous; Prayer potions when the selected location has no known safespot and
 * the monster's max hit is 10 or more.
 */
final class Supplies
{
	static final int PRAYER_MAX_HIT = 10;

	private static final List<Integer> ANTIPOISON = Arrays.asList(
		ItemID._4DOSEANTIPOISON, ItemID._3DOSEANTIPOISON, ItemID._2DOSEANTIPOISON, ItemID._1DOSEANTIPOISON,
		ItemID._4DOSE2ANTIPOISON, ItemID._3DOSE2ANTIPOISON, ItemID._2DOSE2ANTIPOISON, ItemID._1DOSE2ANTIPOISON,
		ItemID.ANTIDOTE_4, ItemID.ANTIDOTE_3, ItemID.ANTIDOTE_2, ItemID.ANTIDOTE_1,
		ItemID.ANTIDOTE__4, ItemID.ANTIDOTE__3, ItemID.ANTIDOTE__2, ItemID.ANTIDOTE__1);
	private static final List<Integer> ANTIVENOM = Arrays.asList(
		ItemID.ANTIVENOM4, ItemID.ANTIVENOM3, ItemID.ANTIVENOM2, ItemID.ANTIVENOM1,
		ItemID.ANTIVENOM_4, ItemID.ANTIVENOM_3, ItemID.ANTIVENOM_2, ItemID.ANTIVENOM_1);
	private static final List<Integer> PRAYER = Arrays.asList(
		ItemID._4DOSEPRAYERRESTORE, ItemID._3DOSEPRAYERRESTORE, ItemID._2DOSEPRAYERRESTORE, ItemID._1DOSEPRAYERRESTORE,
		ItemID._4DOSE2RESTORE, ItemID._3DOSE2RESTORE, ItemID._2DOSE2RESTORE, ItemID._1DOSE2RESTORE);

	private Supplies()
	{
	}

	/** @param monster null when the task has no bundled monster data (then only Food is suggested) */
	static List<ItemNeed> derive(Monster monster, boolean hasSafespot)
	{
		List<ItemNeed> out = new ArrayList<>();
		out.add(need("Food", new ArrayList<>(FoodItems.ids())));
		if (monster == null)
		{
			return out;
		}
		if (monster.isVenomous())
		{
			out.add(need("Antivenom", ANTIVENOM));
		}
		else if (monster.isPoisonous())
		{
			out.add(need("Antipoison", ANTIPOISON));
		}
		if (!hasSafespot && monster.getMaxHit() >= PRAYER_MAX_HIT)
		{
			out.add(need("Prayer potions", PRAYER));
		}
		return out;
	}

	private static ItemNeed need(String name, List<Integer> ids)
	{
		ItemNeed n = new ItemNeed();
		n.setName(name);
		n.setItemIds(new ArrayList<>(ids));
		return n;
	}
}
