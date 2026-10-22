package com.slayercompanion.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class WhereToGoModelTest
{
	@Test
	public void noTask()
	{
		WhereToGoModel m = WhereToGoModel.noTask();
		assertTrue(m.isNoTask());
		assertEquals("No slayer task", m.getMessage());
	}

	@Test
	public void topPickIsRecommendedWithWhyAndSelectedByDefault()
	{
		WhereToGoModel m = WhereToGoModel.build(Samples.fireGiantRanking(), null);
		WhereToGoModel.Row top = m.getRows().get(0);
		assertTrue(top.isRecommended());
		assertEquals("Safespot · Closest", top.getWhy());
		assertTrue(top.isSelected());
		assertEquals("fire-giants--waterfall-dungeon", m.getSelectedId());
		assertEquals("~1m 20s away", top.getTravel());
		assertEquals("~2m", m.getRows().get(1).getTravel());
	}

	@Test
	public void explicitSelectionWins()
	{
		WhereToGoModel m = WhereToGoModel.build(Samples.fireGiantRanking(), "fire-giants--catacombs-of-kourend");
		assertFalse(m.getRows().get(0).isSelected());
		assertTrue(m.getRows().get(1).isSelected());
		assertEquals("fire-giants--catacombs-of-kourend", m.getSelectedId());
	}

	@Test
	public void unknownSelectionFallsBackToTopPick()
	{
		assertEquals("fire-giants--waterfall-dungeon",
			WhereToGoModel.effectiveSelection(Samples.fireGiantRanking(), "gone"));
		assertNull(WhereToGoModel.effectiveSelection(Collections.emptyList(), null));
	}

	@Test
	public void noKnownSafespotTagWhereNone()
	{
		WhereToGoModel m = WhereToGoModel.build(Samples.fireGiantRanking(), null);
		List<String> top = m.getRows().get(0).getTags().stream().map(WhereToGoModel.Tag::getText).collect(Collectors.toList());
		assertEquals("Safespot", top.get(0));
		List<String> second = m.getRows().get(1).getTags().stream().map(WhereToGoModel.Tag::getText).collect(Collectors.toList());
		assertEquals("No known safespot", second.get(0));
		// The data's own "Safespot" tag is replaced by what the safespot store knows.
		assertEquals(1, second.stream().filter(t -> t.contains("afespot")).count());
		assertTrue(second.contains("Bonus drops"));
	}

	@Test
	public void barsScaleToMaxAcrossUsableRows()
	{
		WhereToGoModel m = WhereToGoModel.build(Samples.fireGiantRanking(), null);
		WhereToGoModel.Row catacombs = m.getRows().get(1);
		assertEquals(1.0, catacombs.getXpFraction(), 1e-9);
		assertEquals(1.0, catacombs.getGpFraction(), 1e-9);
		assertEquals(31.0 / 39.0, m.getRows().get(0).getXpFraction(), 1e-9);
		assertEquals("31k", m.getRows().get(0).getXpText());
		assertEquals("180k", m.getRows().get(0).getGpText());
		assertTrue(m.getNote().contains("estimates"));
	}

	@Test
	public void lockedRowIsGreyWithReason()
	{
		WhereToGoModel m = WhereToGoModel.build(Samples.fireGiantRanking(), null);
		WhereToGoModel.Row locked = m.getRows().get(3);
		assertTrue(locked.isLocked());
		assertEquals("—", locked.getTravel());
		assertEquals(Collections.singletonList("Needs boots of stone"),
			locked.getTags().stream().map(WhereToGoModel.Tag::getText).collect(Collectors.toList()));
	}

	@Test
	public void actionMessages()
	{
		assertEquals("Shortest Path plugin not installed", WhereToGoModel.takeMeThereMessage(false));
		assertNull(WhereToGoModel.takeMeThereMessage(true));
		assertEquals("Copied 3 safespots", WhereToGoModel.copiedMessage(3));
	}

	@Test
	public void dataTagsGetShortTextAndColour()
	{
		assertEquals(new WhereToGoModel.Tag("Multi", WhereToGoModel.TagKind.BLUE), WhereToGoModel.tag("Multi-combat"));
		assertEquals(new WhereToGoModel.Tag("Bonus drops", WhereToGoModel.TagKind.GOLD),
			WhereToGoModel.tag("Catacombs bonus drops"));
		assertEquals(new WhereToGoModel.Tag("Cannon", WhereToGoModel.TagKind.NORMAL), WhereToGoModel.tag("Cannon allowed"));
		assertEquals(WhereToGoModel.TagKind.RISK, WhereToGoModel.tag("Wilderness").getKind());
		assertEquals(new WhereToGoModel.Tag("Single-combat", WhereToGoModel.TagKind.NORMAL),
			WhereToGoModel.tag("Single-combat"));
	}

	@Test
	public void headerShowsTaskAndSortOrder()
	{
		WhereToGoModel m = WhereToGoModel.build(Samples.fireGiantRanking(), null, "Fire giants",
			com.slayercompanion.gear.Style.MELEE);
		assertEquals("Fire giants", m.getTitle());
		assertEquals("Melee · sorted by travel time", m.getSubtitle());
		assertEquals("Ranged · safespots first, then travel time",
			WhereToGoModel.subtitle(com.slayercompanion.gear.Style.RANGED));
		assertNull(WhereToGoModel.build(Samples.fireGiantRanking(), null).getTitle());
	}
}
