package com.slayercompanion.ui;

import java.awt.image.BufferedImage;
import net.runelite.client.game.ItemManager;

/** Item pictures from the client's ItemManager; they arrive as AsyncBufferedImages that fill in when loaded. */
public class ItemManagerIcons implements IconSource
{
	private final ItemManager itemManager;

	public ItemManagerIcons(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	@Override
	public BufferedImage icon(int itemId)
	{
		return itemId <= 0 ? null : itemManager.getImage(itemId);
	}
}
