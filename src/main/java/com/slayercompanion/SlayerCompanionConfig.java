package com.slayercompanion;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;

@ConfigGroup(SlayerCompanionConfig.GROUP)
public interface SlayerCompanionConfig extends Config
{
	String GROUP = "slayercompanion";

	/** Per-account (RS profile) keys, written with ConfigManager.setRSProfileConfiguration. */
	String OWNED_ITEMS_KEY = "ownedItems";
	String SAFESPOTS_KEY = "safespots";
	/** "{task name}\t{initial amount}" so "of 142" survives a restart mid-task. */
	String INITIAL_AMOUNT_KEY = "initialAmount";
}
