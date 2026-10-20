package com.slayercompanion.data;

import lombok.Data;

@Data
public class Drop
{
	private int itemId;
	private String name;
	/** Per-kill probability, e.g. 1/128 = 0.0078125. */
	private double rate;
	private int qtyMin;
	private int qtyMax;
}
