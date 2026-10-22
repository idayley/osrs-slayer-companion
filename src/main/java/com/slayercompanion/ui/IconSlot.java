package com.slayercompanion.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import javax.swing.JPanel;

/** An icon in a recessed stone square (.sic); "worn" turns it green. 30x28 by default. */
class IconSlot extends JPanel
{
	private static final Color IN = new Color(0x4a3f30);
	private static final Color OUT = new Color(0x2b241b);
	private static final Color WORN_IN = new Color(0x3b5a2a);
	private static final Color WORN_OUT = new Color(0x22301a);

	private final Dimension size;
	private boolean worn;

	IconSlot(ItemIcon icon)
	{
		this(icon, new Dimension(30, 28));
	}

	IconSlot(ItemIcon icon, Dimension size)
	{
		super(new BorderLayout());
		this.size = size;
		setOpaque(false);
		add(icon, BorderLayout.CENTER);
	}

	IconSlot setWorn(boolean worn)
	{
		this.worn = worn;
		repaint();
		return this;
	}

	boolean isWorn()
	{
		return worn;
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
		Graphics2D g2 = (Graphics2D) g.create();
		int w = getWidth();
		int h = getHeight();
		float r = Math.max(w, h) / 2f * 1.2f;
		g2.setPaint(new RadialGradientPaint(w / 2f, h / 2f, r, new float[]{0f, 1f},
			worn ? new Color[]{WORN_IN, WORN_OUT} : new Color[]{IN, OUT}));
		g2.fillRect(0, 0, w, h);
		g2.setColor(worn ? Ui.OK : Ui.EDGE_DARK);
		g2.drawRect(0, 0, w - 1, h - 1);
		g2.dispose();
	}
}
