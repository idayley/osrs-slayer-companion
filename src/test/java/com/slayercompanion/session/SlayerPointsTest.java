package com.slayercompanion.session;

import org.junit.Test;
import static org.junit.Assert.*;

public class SlayerPointsTest
{
	@Test
	public void chaeldarBase()
	{
		assertEquals(10, SlayerPoints.pointsForTask("Chaeldar", 11));
		assertEquals(10, SlayerPoints.pointsForTask("chaeldar", 4));
	}

	@Test
	public void chaeldarMilestones()
	{
		assertEquals(50, SlayerPoints.pointsForTask("Chaeldar", 9));
		assertEquals(150, SlayerPoints.pointsForTask("Chaeldar", 49));
		assertEquals(250, SlayerPoints.pointsForTask("Chaeldar", 99));
		assertEquals(350, SlayerPoints.pointsForTask("Chaeldar", 249));
		assertEquals(500, SlayerPoints.pointsForTask("Chaeldar", 999));
		assertEquals(50, SlayerPoints.pointsForTask("Chaeldar", 19));
		assertEquals(150, SlayerPoints.pointsForTask("Chaeldar", 149));
	}

	@Test
	public void turaelGivesNothing()
	{
		assertEquals(0, SlayerPoints.pointsForTask("Turael", 11));
		assertEquals(0, SlayerPoints.pointsForTask("Turael", 9));
		assertEquals(0, SlayerPoints.pointsForTask("Spria", 49));
	}

	@Test
	public void firstFourTasksOfStreakUnpaid()
	{
		assertEquals(0, SlayerPoints.pointsForTask("Chaeldar", 0));
		assertEquals(0, SlayerPoints.pointsForTask("Chaeldar", 3));
		assertEquals(10, SlayerPoints.pointsForTask("Chaeldar", 4));
	}

	@Test
	public void otherMasters()
	{
		assertEquals(6, SlayerPoints.pointsForTask("Mazchna", 20));
		assertEquals(40, SlayerPoints.pointsForTask("Vannaka", 9));
		assertEquals(12, SlayerPoints.pointsForTask("Nieve", 20));
		assertEquals(15, SlayerPoints.pointsForTask("Steve", 20, true));
		assertEquals(225, SlayerPoints.pointsForTask("Duradel", 49));
		assertEquals(18, SlayerPoints.pointsForTask("Konar quo Maten", 20));
		assertEquals(1000, SlayerPoints.pointsForTask("Konar", 999, true));
		assertEquals(1250, SlayerPoints.pointsForTask("Krystilia", 999));
		assertEquals(0, SlayerPoints.pointsForTask("Mortimer", 20));
		assertFalse(SlayerPoints.isKnown("Mortimer"));
	}

	@Test
	public void summary()
	{
		assertEquals("412 → 422", SlayerPoints.summary(412, 10));
	}

	@Test
	public void varbits()
	{
		assertEquals(4068, SlayerPoints.POINTS_VARBIT);
		assertEquals(4069, SlayerPoints.STREAK_VARBIT);
	}
}
