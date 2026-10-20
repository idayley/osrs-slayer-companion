package com.slayercompanion.session;

import java.util.Set;
import net.runelite.api.ItemID;

/** Common foods used to estimate food consumption per kill. */
public final class FoodItems
{
	private static final Set<Integer> FOOD = Set.of(
		ItemID.SHARK, ItemID.MANTA_RAY, ItemID.ANGLERFISH, ItemID.MONKFISH,
		ItemID.COOKED_KARAMBWAN, ItemID.LOBSTER, ItemID.SWORDFISH, ItemID.TUNA,
		ItemID.SALMON, ItemID.TROUT, ItemID.DARK_CRAB, ItemID.POTATO_WITH_CHEESE,
		ItemID.PINEAPPLE_PIZZA, ItemID._12_PINEAPPLE_PIZZA,
		ItemID.CAKE, ItemID._23_CAKE, ItemID.SLICE_OF_CAKE);

	private FoodItems()
	{
	}

	public static boolean isFood(int itemId)
	{
		return FOOD.contains(itemId);
	}
}
