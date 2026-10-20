package com.slayercompanion.locations;

import com.slayercompanion.data.Drop;
import com.slayercompanion.data.Location;
import com.slayercompanion.data.Monster;
import java.util.function.IntToLongFunction;

public final class Estimates
{
	private Estimates()
	{
	}

	public static double killsPerHour(double dps, Monster monster, Location location)
	{
		if (dps <= 0)
		{
			return 0;
		}
		double secondsPerKill = monster.getHitpoints() / dps + location.getOverheadSeconds();
		return 3600 / secondsPerKill * location.getKillSpeedFactor();
	}

	public static double xpPerHour(double killsPerHour, Monster monster)
	{
		return killsPerHour * monster.getSlayerXp();
	}

	public static double gpPerKill(Monster monster, IntToLongFunction price)
	{
		double total = 0;
		for (Drop d : monster.getDrops())
		{
			total += d.getRate() * (d.getQtyMin() + d.getQtyMax()) / 2.0 * price.applyAsLong(d.getItemId());
		}
		return total;
	}

	public static double gpPerHour(double killsPerHour, double gpPerKill, Location location)
	{
		return killsPerHour * (gpPerKill + location.getBonusGpPerKill());
	}
}
