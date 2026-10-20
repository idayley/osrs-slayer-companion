package com.slayercompanion.data;

import lombok.Data;

@Data
public class Safespot
{
	private String locationId;
	private int monsterId;
	private WorldPointDto tile;
	private String note;
	private boolean verified;
	/** "builtin" or "player". */
	private String source;
}
