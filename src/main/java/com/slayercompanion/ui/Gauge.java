package com.slayercompanion.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/** Flat bar (.track): dark well with a 1px black edge, a coloured fill, and an optional white marker. */
class Gauge extends JComponent
{
	private final double fraction;
	private final Color fill;
	private final int height;
	private double marker = -1;

	/** @param height total height including the 1px edge; the mockup uses 8 (6px fill) and 9 for supplies. */
	Gauge(double fraction, Color fill, int height)
	{
		this.fraction = clamp(fraction);
		this.fill = fill;
		this.height = height;
		setOpaque(false);
	}

	/** Draws a 2px white tick at this fraction (e.g. where the task ends). */
	Gauge marker(double at)
	{
		marker = clamp(at);
		repaint();
		return this;
	}

	private static double clamp(double f)
	{
		return Double.isNaN(f) ? 0 : Math.max(0, Math.min(1, f));
	}

	@Override
	public Dimension getPreferredSize()
	{
		// Room above and below the bar for the marker overhang.
		return new Dimension(20, height + (marker >= 0 ? 6 : 0));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		int w = getWidth();
		int h = Math.min(height, getHeight());
		int y = (getHeight() - h) / 2;
		g2.setColor(Color.BLACK);
		g2.fillRect(0, y, w, h);
		g2.setColor(Ui.EDGE_DARK);
		g2.fillRect(1, y + 1, w - 2, h - 2);
		int fw = (int) Math.round((w - 2) * fraction);
		if (fw > 0)
		{
			g2.setColor(fill);
			g2.fillRect(1, y + 1, fw, h - 2);
		}
		if (marker >= 0)
		{
			int mx = 1 + (int) Math.round((w - 2) * marker) - 1;
			mx = Math.max(0, Math.min(w - 2, mx));
			g2.setColor(Color.WHITE);
			g2.fillRect(mx, Math.max(0, y - 3), 2, Math.min(getHeight(), h + 6));
		}
		g2.dispose();
	}
}
