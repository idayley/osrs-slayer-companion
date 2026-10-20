package com.slayercompanion.data;

import lombok.Data;
import net.runelite.api.coords.WorldPoint;

@Data
public class WorldPointDto
{
	private int x;
	private int y;
	private int plane;

	public WorldPoint toWorldPoint()
	{
		return new WorldPoint(x, y, plane);
	}
}
