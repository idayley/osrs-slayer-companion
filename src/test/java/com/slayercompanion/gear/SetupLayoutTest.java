package com.slayercompanion.gear;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class SetupLayoutTest
{
	private static final int E = SetupLayout.EMPTY;

	@Test
	public void paperDollMatchesEquipmentScreen()
	{
		Map<String, Integer> slots = new LinkedHashMap<>();
		slots.put("head", 1);
		slots.put("cape", 2);
		slots.put("neck", 3);
		slots.put("ammo", 4);
		slots.put("weapon", 5);
		slots.put("body", 6);
		slots.put("shield", 7);
		slots.put("legs", 8);
		slots.put("hands", 9);
		slots.put("feet", 10);
		slots.put("ring", 11);

		int[] layout = SetupLayout.build(slots, Collections.emptyList());

		assertArrayEquals(new int[]{
			E, 1, E, E, E, E, E, E,
			2, 3, 4, E, E, E, E, E,
			5, 6, 7, E, E, E, E, E,
			E, 8, E, E, E, E, E, E,
			9, 10, 11}, layout);
	}

	@Test
	public void bringStartsAfterBlankRowBelowDoll()
	{
		Map<String, Integer> slots = Collections.singletonMap("weapon", 4151);
		int[] layout = SetupLayout.build(slots, Arrays.asList(385, 2434, 385, 4151, -1));

		// Row 5 is blank; bring starts at row 6, col 0. Duplicates and the worn weapon are skipped.
		assertEquals(6 * 8 + 2, layout.length);
		assertEquals(4151, layout[16]);
		for (int i = 5 * 8; i < 6 * 8; i++)
		{
			assertEquals(E, layout[i]);
		}
		assertEquals(385, layout[48]);
		assertEquals(2434, layout[49]);
	}

	@Test
	public void longBringListWrapsToNextRow()
	{
		int[] layout = SetupLayout.build(Collections.singletonMap("head", 1),
			Arrays.asList(10, 11, 12, 13, 14, 15, 16, 17, 18));
		assertEquals(17, layout[55]);
		assertEquals(18, layout[56]);
	}

	@Test
	public void bringOnlyStartsAtTop()
	{
		assertArrayEquals(new int[]{385, 2434}, SetupLayout.build(Collections.emptyMap(), Arrays.asList(385, 2434)));
	}

	@Test
	public void unknownSlotsAreIgnored()
	{
		assertEquals(E, SetupLayout.slotPosition("tail"));
		assertArrayEquals(new int[0], SetupLayout.build(Collections.singletonMap("tail", 5), null));
	}
}
