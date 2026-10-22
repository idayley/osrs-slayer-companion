package com.slayercompanion.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bank tab layout for the recommended setup, in the style of Inventory Setups: the equipment paper-doll in
 * columns 0-2, then a blank row, then the items to bring. Positions index an 8-wide bank grid (row * 8 + col);
 * -1 marks an empty position.
 */
public final class SetupLayout
{
	public static final int COLUMNS = 8;
	public static final int EMPTY = -1;
	/** Rows used by the paper-doll (head row through gloves/boots/ring row). */
	static final int DOLL_ROWS = 5;

	private static final Map<String, Integer> SLOT_POS = new HashMap<>();

	static
	{
		SLOT_POS.put("head", pos(0, 1));
		SLOT_POS.put("cape", pos(1, 0));
		SLOT_POS.put("neck", pos(1, 1));
		SLOT_POS.put("ammo", pos(1, 2));
		SLOT_POS.put("weapon", pos(2, 0));
		SLOT_POS.put("body", pos(2, 1));
		SLOT_POS.put("shield", pos(2, 2));
		SLOT_POS.put("legs", pos(3, 1));
		SLOT_POS.put("hands", pos(4, 0));
		SLOT_POS.put("feet", pos(4, 1));
		SLOT_POS.put("ring", pos(4, 2));
	}

	private SetupLayout()
	{
	}

	static int pos(int row, int col)
	{
		return row * COLUMNS + col;
	}

	/** Bank position of an equipment slot ("head", "weapon", ...), or -1 for an unknown slot. */
	public static int slotPosition(String slot)
	{
		return SLOT_POS.getOrDefault(slot, EMPTY);
	}

	/**
	 * @param slotItems item id per equipment slot name (as in {@link Loadout#getBySlot()})
	 * @param bring     items to bring, in display order; duplicates and items already in the doll are skipped
	 * @return item id per bank position; empty when there is nothing to show
	 */
	public static int[] build(Map<String, Integer> slotItems, List<Integer> bring)
	{
		List<Integer> out = new ArrayList<>();
		Set<Integer> placed = new HashSet<>();
		for (Map.Entry<String, Integer> e : slotItems.entrySet())
		{
			int p = slotPosition(e.getKey());
			Integer id = e.getValue();
			if (p < 0 || id == null || id < 0)
			{
				continue;
			}
			set(out, p, id);
			placed.add(id);
		}

		// Bring items start on a fresh row, one blank row below the doll (or at the top when there is no gear).
		int next = placed.isEmpty() ? 0 : pos(DOLL_ROWS + 1, 0);
		for (Integer id : bring == null ? Collections.<Integer>emptyList() : bring)
		{
			if (id == null || id < 0 || !placed.add(id))
			{
				continue;
			}
			set(out, next++, id);
		}

		int[] layout = new int[out.size()];
		for (int i = 0; i < layout.length; i++)
		{
			layout[i] = out.get(i);
		}
		return layout;
	}

	private static void set(List<Integer> out, int pos, int id)
	{
		while (out.size() <= pos)
		{
			out.add(EMPTY);
		}
		out.set(pos, id);
	}
}
