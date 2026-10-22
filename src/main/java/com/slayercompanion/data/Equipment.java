package com.slayercompanion.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class Equipment
{
	private int itemId;
	private String name;
	private String slot;
	/**
	 * Keys: stab, slash, crush, magic, ranged (attack); str, ranged_str, magic_str
	 * (tenths of a percent, 15 = +1.5%); prayer.
	 */
	private Map<String, Integer> bonuses = new HashMap<>();
	private int speedTicks;
	private String category;
	private Map<String, Integer> skillReqs = new HashMap<>();
	/** RuneLite Quest enum names. */
	private List<String> questReqs = new ArrayList<>();
	private boolean twoHanded;
	/** How to get it when untradeable ("TzHaar Fight Cave reward", "Made with 55 Crafting"); null otherwise. */
	private String source;
}
