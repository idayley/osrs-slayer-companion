package com.slayercompanion;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(name = "Slayer Companion")
public class SlayerCompanionPlugin extends Plugin
{
	@Provides
	SlayerCompanionConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerCompanionConfig.class);
	}
}
