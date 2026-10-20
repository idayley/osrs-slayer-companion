package com.slayercompanion.ui;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Outlines the Prep tab's gear and slayer items in the bank while "Highlight these in my bank" is on. */
public class BankHighlightOverlay extends WidgetItemOverlay
{
	private final ItemManager itemManager;
	private volatile Set<Integer> itemIds = Collections.emptySet();

	@Inject
	public BankHighlightOverlay(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		showOnBank();
	}

	/** Canonical item ids to outline; empty turns highlighting off. */
	public void setItems(Set<Integer> ids)
	{
		itemIds = ids == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(ids));
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Set<Integer> ids = itemIds;
		if (ids.isEmpty() || !(ids.contains(itemId) || ids.contains(itemManager.canonicalize(itemId))))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), ColorScheme.BRAND_ORANGE);
		graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
	}
}
