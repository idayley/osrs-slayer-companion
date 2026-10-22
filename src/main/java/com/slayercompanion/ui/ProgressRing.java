package com.slayercompanion.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import javax.swing.JComponent;

/** Task ring (.ring): dark track, green arc clockwise from the top, big number with a small label inside. 70x70. */
class ProgressRing extends JComponent
{
	private static final int SIZE = 70;
	private static final float STROKE = 7.5f;

	private final double fraction;
	private final String number;
	private final String label;

	ProgressRing(double fraction, String number, String label)
	{
		this.fraction = Double.isNaN(fraction) ? 0 : Math.max(0, Math.min(1, fraction));
		this.number = number;
		this.label = label;
		setOpaque(false);
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(SIZE, SIZE);
	}

	@Override
	public Dimension getMinimumSize()
	{
		return getPreferredSize();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		int d = Math.min(getWidth(), getHeight());
		double r = d / 2.0 - STROKE / 2 - 1;
		double cx = getWidth() / 2.0;
		double cy = getHeight() / 2.0;
		g2.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		g2.setColor(Ui.EDGE_DARK);
		g2.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 0, 360, Arc2D.OPEN));
		if (fraction > 0)
		{
			g2.setColor(Ui.OK);
			g2.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 90, -360 * fraction, Arc2D.OPEN));
		}

		Font big = Ui.sans(Font.BOLD, 22f);
		Font small = Ui.small();
		FontMetrics fb = g2.getFontMetrics(big);
		FontMetrics fs = g2.getFontMetrics(small);
		boolean hasLabel = label != null && !label.isEmpty();
		int total = fb.getAscent() - 2 + (hasLabel ? fs.getAscent() : 0);
		int y = (int) Math.round(cy - total / 2.0) + fb.getAscent() - 2;
		int x = (int) Math.round(cx - fb.stringWidth(number) / 2.0);
		g2.setFont(big);
		g2.setColor(Color.BLACK);
		g2.drawString(number, x + 1, y + 1);
		g2.setColor(Ui.YELLOW);
		g2.drawString(number, x, y);
		if (hasLabel)
		{
			g2.setFont(small);
			g2.setColor(Ui.MUTED);
			g2.drawString(label, (int) Math.round(cx - fs.stringWidth(label) / 2.0), y + fs.getAscent());
		}
		g2.dispose();
	}
}
