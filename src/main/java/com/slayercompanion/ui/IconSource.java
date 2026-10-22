package com.slayercompanion.ui;

import java.awt.image.BufferedImage;

/** Where item pictures come from: RuneLite's ItemManager in the client, PNG files in tests. */
public interface IconSource
{
	/** The item's inventory image (36x32), or null when there is none. May fill in later (AsyncBufferedImage). */
	BufferedImage icon(int itemId);

	/** No pictures at all. */
	IconSource NONE = id -> null;
}
