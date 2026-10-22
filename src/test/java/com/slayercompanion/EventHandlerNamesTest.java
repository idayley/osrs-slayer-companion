package com.slayercompanion;

import com.slayercompanion.safespot.SafespotMenu;
import com.slayercompanion.ui.BankSetupTab;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.eventbus.Subscribe;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** RuneLite refuses to start a plugin whose @Subscribe method isn't named on + the event's class name. */
public class EventHandlerNamesTest
{
	@Test
	public void everyHandlerIsNamedAfterItsEvent()
	{
		List<String> wrong = new ArrayList<>();
		for (Class<?> c : new Class<?>[]{SlayerCompanionPlugin.class, SafespotMenu.class, BankSetupTab.class})
		{
			for (Method m : c.getDeclaredMethods())
			{
				if (m.isAnnotationPresent(Subscribe.class)
					&& !m.getName().equals("on" + m.getParameterTypes()[0].getSimpleName()))
				{
					wrong.add(c.getSimpleName() + "." + m.getName());
				}
			}
		}
		assertEquals(new ArrayList<>(), wrong);
	}
}
