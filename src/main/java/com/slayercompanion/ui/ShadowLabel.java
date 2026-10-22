package com.slayercompanion.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Plain-text label drawn with a 1px black drop shadow, like in-game interface text. Draws its own text (no HTML),
 * so the pixel fonts render crisply; wraps on spaces when given a width.
 */
class ShadowLabel extends JLabel
{
	private int wrapWidth;

	ShadowLabel(String text)
	{
		super(text);
	}

	/** Wrap onto several lines to fit this many pixels (0 = one line). */
	ShadowLabel wrap(int width)
	{
		wrapWidth = width;
		revalidate();
		return this;
	}

	private List<String> lines(FontMetrics fm)
	{
		List<String> out = new ArrayList<>();
		String text = getText() == null ? "" : getText();
		if (wrapWidth <= 0)
		{
			out.add(text);
			return out;
		}
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			String next = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && fm.stringWidth(next) > wrapWidth)
			{
				out.add(line.toString());
				line = new StringBuilder(word);
			}
			else
			{
				line = new StringBuilder(next);
			}
		}
		out.add(line.toString());
		return out;
	}

	@Override
	public Dimension getPreferredSize()
	{
		if (isPreferredSizeSet())
		{
			return super.getPreferredSize();
		}
		FontMetrics fm = getFontMetrics(getFont());
		List<String> lines = lines(fm);
		int w = 0;
		for (String l : lines)
		{
			w = Math.max(w, fm.stringWidth(l));
		}
		Insets in = getInsets();
		return new Dimension(w + 1 + in.left + in.right, lines.size() * fm.getHeight() + 1 + in.top + in.bottom);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		if (isOpaque())
		{
			g.setColor(getBackground());
			g.fillRect(0, 0, getWidth(), getHeight());
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, Ui.isPixel(getFont())
			? RenderingHints.VALUE_TEXT_ANTIALIAS_OFF : RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setFont(getFont());
		FontMetrics fm = g2.getFontMetrics();
		Insets in = getInsets();
		List<String> lines = lines(fm);
		int inner = getWidth() - in.left - in.right;
		int block = lines.size() * fm.getHeight();
		int y = in.top + Math.max(0, (getHeight() - in.top - in.bottom - block) / 2) + fm.getAscent();
		Color fg = getForeground();
		for (String l : lines)
		{
			int x = in.left;
			if (getHorizontalAlignment() == SwingConstants.CENTER)
			{
				x += (inner - fm.stringWidth(l)) / 2;
			}
			else if (getHorizontalAlignment() == SwingConstants.RIGHT || getHorizontalAlignment() == SwingConstants.TRAILING)
			{
				x += inner - fm.stringWidth(l) - 1;
			}
			g2.setColor(Color.BLACK);
			g2.drawString(l, x + 1, y + 1);
			g2.setColor(fg);
			g2.drawString(l, x, y);
			y += fm.getHeight();
		}
		g2.dispose();
	}
}
