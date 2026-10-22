package com.slayercompanion.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/**
 * In-game style button (.btn): brown gradient, 2px dark edge, light top line, bold text with a black shadow.
 * Gold text normally, yellow when primary; brighter on hover, nudged down while pressed.
 */
class StoneButton extends JComponent
{
	private String text;
	private boolean primary;
	private boolean small;
	private boolean hover;
	private boolean pressed;

	StoneButton(String text, boolean primary, Runnable onClick)
	{
		this.text = text;
		this.primary = primary;
		setFont(Ui.bold());
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = false;
				pressed = false;
				repaint();
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				pressed = true;
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				pressed = false;
				repaint();
			}
		});
	}

	/** Compact variant for buttons inside cards (.btn.sm). */
	StoneButton small()
	{
		small = true;
		setFont(Ui.bold().deriveFont(11f));
		revalidate();
		return this;
	}

	StoneButton setPrimary(boolean primary)
	{
		this.primary = primary;
		repaint();
		return this;
	}

	void setText(String text)
	{
		this.text = text;
		revalidate();
		repaint();
	}

	String getText()
	{
		return text;
	}

	@Override
	public Dimension getPreferredSize()
	{
		FontMetrics fm = getFontMetrics(getFont());
		int pad = small ? 5 : 7;
		return new Dimension(fm.stringWidth(text) + 24, fm.getHeight() + 2 * pad + 4);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		int w = getWidth();
		int h = getHeight();
		Color top = Ui.BUTTON_TOP;
		Color bottom = Ui.BUTTON_BOTTOM;
		if (hover)
		{
			top = brighter(top);
			bottom = brighter(bottom);
		}
		g2.setPaint(new GradientPaint(0, 2, top, 0, h - 2, bottom));
		g2.fillRect(2, 2, w - 4, h - 4);
		g2.setColor(hover ? brighter(Ui.BUTTON_SHINE) : Ui.BUTTON_SHINE);
		g2.fillRect(2, 2, w - 4, 1);
		g2.setColor(Ui.EDGE_DARK);
		g2.fillRect(0, 0, w, 2);
		g2.fillRect(0, h - 2, w, 2);
		g2.fillRect(0, 0, 2, h);
		g2.fillRect(w - 2, 0, 2, h);

		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Font f = getFont();
		g2.setFont(f);
		FontMetrics fm = g2.getFontMetrics();
		int x = (w - fm.stringWidth(text)) / 2;
		int y = (h - fm.getHeight()) / 2 + fm.getAscent() + (pressed ? 1 : 0);
		g2.setColor(Color.BLACK);
		g2.drawString(text, x + 1, y + 1);
		g2.setColor(primary ? Ui.YELLOW : Ui.TEXT);
		g2.drawString(text, x, y);
		g2.dispose();
	}

	private static Color brighter(Color c)
	{
		return new Color(Math.min(255, (int) (c.getRed() * 1.15f)), Math.min(255, (int) (c.getGreen() * 1.15f)),
			Math.min(255, (int) (c.getBlue() * 1.15f)));
	}
}
