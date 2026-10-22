package com.slayercompanion.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import javax.swing.JComponent;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/** An item or skill picture fitted (aspect kept, pixelated) into a fixed box; repaints when an async image loads. */
class ItemIcon extends JComponent
{
	/** Mockup sizes: inventory row 22x20, gear slot 24x22, upgrade 30x28, inline 16x15. */
	static final Dimension ROW = new Dimension(22, 20);
	static final Dimension SLOT = new Dimension(24, 22);
	static final Dimension LARGE = new Dimension(30, 28);
	static final Dimension INLINE = new Dimension(16, 15);

	private final BufferedImage image;
	private final Dimension size;
	private boolean dimmed;
	private BufferedImage dimmedImage;

	ItemIcon(BufferedImage image, Dimension size)
	{
		this.image = image;
		this.size = size;
		setOpaque(false);
		if (image instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) image).onLoaded(() ->
			{
				dimmedImage = null;
				repaint();
			});
		}
	}

	static ItemIcon item(IconSource icons, int itemId, Dimension size)
	{
		return new ItemIcon(icons.icon(itemId), size);
	}

	/** A bundled skill icon ("slayer", "prayer", ...). */
	static ItemIcon skill(String skill, Dimension size)
	{
		return new ItemIcon(SkillIcons.get(skill), size);
	}

	/** Grey and darkened, for things the player is missing. */
	ItemIcon setDimmed(boolean dimmed)
	{
		this.dimmed = dimmed;
		dimmedImage = null;
		repaint();
		return this;
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(size);
	}

	@Override
	public Dimension getMinimumSize()
	{
		return getPreferredSize();
	}

	@Override
	public Dimension getMaximumSize()
	{
		return getPreferredSize();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		BufferedImage img = shown();
		if (img == null || img.getWidth() <= 1)
		{
			return;
		}
		double scale = Math.min((double) getWidth() / img.getWidth(), (double) getHeight() / img.getHeight());
		int w = Math.max(1, (int) Math.round(img.getWidth() * scale));
		int h = Math.max(1, (int) Math.round(img.getHeight() * scale));
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale < 1
			? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g2.drawImage(img, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
		g2.dispose();
	}

	private BufferedImage shown()
	{
		if (image == null || !dimmed)
		{
			return image;
		}
		if (dimmedImage == null)
		{
			BufferedImage grey = ImageUtil.grayscaleImage(image);
			dimmedImage = new RescaleOp(new float[]{0.6f, 0.6f, 0.6f, 1f}, new float[4], null).filter(grey, null);
		}
		return dimmedImage;
	}
}
