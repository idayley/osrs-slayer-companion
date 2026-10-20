package com.slayercompanion.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class ShortestPathTest
{
	private static Plugin plugin(String name)
	{
		Plugin p = mock(Plugin.class);
		when(p.getName()).thenReturn(name);
		return p;
	}

	@Test
	public void postsTargetWhenInstalled()
	{
		EventBus bus = mock(EventBus.class);
		PluginManager pm = mock(PluginManager.class);
		Plugin sp = plugin("Shortest Path");
		when(pm.getPlugins()).thenReturn(Collections.singletonList(sp));
		when(pm.isPluginEnabled(sp)).thenReturn(true);

		WorldPoint point = new WorldPoint(3164, 3483, 0);
		assertTrue(new ShortestPath(bus, pm).pathTo(point));

		ArgumentCaptor<PluginMessage> cap = ArgumentCaptor.forClass(PluginMessage.class);
		verify(bus).post(cap.capture());
		assertEquals("shortestpath", cap.getValue().getNamespace());
		assertEquals("path", cap.getValue().getName());
		assertEquals(point, cap.getValue().getData().get("target"));
	}

	@Test
	public void doesNothingWhenMissing()
	{
		EventBus bus = mock(EventBus.class);
		PluginManager pm = mock(PluginManager.class);
		Plugin other = plugin("Other");
		when(pm.getPlugins()).thenReturn(Collections.singletonList(other));

		ShortestPath sp = new ShortestPath(bus, pm);
		assertFalse(sp.available());
		assertFalse(sp.pathTo(new WorldPoint(1, 2, 0)));
		verify(bus, never()).post(any());
	}

	@Test
	public void doesNothingWhenDisabled()
	{
		EventBus bus = mock(EventBus.class);
		PluginManager pm = mock(PluginManager.class);
		Plugin sp = plugin("Shortest Path");
		when(pm.getPlugins()).thenReturn(Collections.singletonList(sp));
		when(pm.isPluginEnabled(sp)).thenReturn(false);

		assertFalse(new ShortestPath(bus, pm).pathTo(new WorldPoint(1, 2, 0)));
		verify(bus, never()).post(any());
	}
}
