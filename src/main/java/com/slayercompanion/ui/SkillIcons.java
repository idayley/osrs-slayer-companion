package com.slayercompanion.ui;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Small skill icons bundled with the plugin (resources/com/slayercompanion/skills/{name}.png), since a sideloaded
 * plugin can't read the client's own skill icon resources. Names: slayer, prayer, hitpoints, magic, attack, ranged,
 * defence.
 */
final class SkillIcons
{
	private static final Map<String, BufferedImage> CACHE = new HashMap<>();

	private SkillIcons()
	{
	}

	/** The icon for a skill name (any case), or null if it isn't bundled. */
	static synchronized BufferedImage get(String skill)
	{
		String key = skill.toLowerCase();
		if (CACHE.containsKey(key))
		{
			return CACHE.get(key);
		}
		BufferedImage img = null;
		try (InputStream in = SkillIcons.class.getResourceAsStream("/com/slayercompanion/skills/" + key + ".png"))
		{
			if (in != null)
			{
				img = ImageIO.read(in);
			}
		}
		catch (Exception e)
		{
			img = null;
		}
		CACHE.put(key, img);
		return img;
	}
}
