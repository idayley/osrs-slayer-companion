package com.slayercompanion.locations;

import static org.junit.Assert.assertEquals;

import com.slayercompanion.data.Drop;
import com.slayercompanion.data.Location;
import com.slayercompanion.data.Monster;
import org.junit.Test;

public class EstimatesTest
{
	@Test
	public void killsPerHourFormula()
	{
		Monster m = new Monster();
		m.setHitpoints(111);
		Location l = new Location();
		l.setOverheadSeconds(4);
		l.setKillSpeedFactor(1.0);
		assertEquals(150, Estimates.killsPerHour(5.55, m, l), 0.01);
	}

	@Test
	public void killSpeedFactorScales()
	{
		Monster m = new Monster();
		m.setHitpoints(100);
		Location l = new Location();
		l.setOverheadSeconds(0);
		l.setKillSpeedFactor(2.0);
		assertEquals(3600 / 20.0 * 2, Estimates.killsPerHour(5, m, l), 0.01);
	}

	@Test
	public void xpPerHour()
	{
		Monster m = new Monster();
		m.setSlayerXp(50);
		assertEquals(5000, Estimates.xpPerHour(100, m), 0.001);
	}

	@Test
	public void gpPerKillUsesAverageQuantity()
	{
		Monster m = new Monster();
		Drop d = new Drop();
		d.setItemId(1);
		d.setRate(0.5);
		d.setQtyMin(2);
		d.setQtyMax(4);
		m.getDrops().add(d);
		assertEquals(0.5 * 3 * 10, Estimates.gpPerKill(m, id -> 10), 0.001);
	}

	@Test
	public void gpPerHourAddsBonus()
	{
		Location l = new Location();
		l.setBonusGpPerKill(100);
		assertEquals(10 * 300, Estimates.gpPerHour(10, 200, l), 0.001);
	}
}
