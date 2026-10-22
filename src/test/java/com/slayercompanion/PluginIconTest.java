package com.slayercompanion;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class PluginIconTest
{
	@Test
	public void iconShipsInsidePluginJar()
	{
		// A sideloaded plugin can't read RuneLite's own resources, so the icon must be ours.
		assertNotNull(SlayerCompanionPlugin.class.getResource("icon.png"));
	}
}
