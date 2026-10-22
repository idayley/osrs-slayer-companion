package com.slayercompanion.ui;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Item pictures for headless renders, from src/test/resources/icons/{id}.png; null when there is no file. */
final class TestIcons implements IconSource
{
	private final Map<Integer, BufferedImage> cache = new HashMap<>();

	@Override
	public BufferedImage icon(int itemId)
	{
		return cache.computeIfAbsent(itemId, id ->
		{
			try (InputStream in = TestIcons.class.getResourceAsStream("/icons/" + id + ".png"))
			{
				return in == null ? null : ImageIO.read(in);
			}
			catch (Exception e)
			{
				return null;
			}
		});
	}
}
