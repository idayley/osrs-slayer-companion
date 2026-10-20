package com.slayercompanion.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Shared look for the panel: mockup colours on RuneLite's ColorScheme greys, and small building blocks. */
final class Ui
{
	static final Color BG = ColorScheme.DARKER_GRAY_COLOR;
	static final Color ROW = ColorScheme.DARK_GRAY_COLOR;
	static final Color ROW2 = new Color(50, 50, 50);
	static final Color LINE = new Color(58, 58, 58);
	static final Color TEXT = new Color(226, 226, 226);
	static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	static final Color FAINT = new Color(110, 110, 110);
	static final Color ORANGE = ColorScheme.BRAND_ORANGE;
	static final Color GREEN = new Color(79, 191, 106);
	static final Color BLUE = new Color(90, 160, 216);
	static final Color GOLD = new Color(217, 181, 74);
	static final Color RISK = new Color(201, 106, 74);
	static final Color SELECTED_BG = new Color(36, 31, 22);
	static final Color WARN_BG = new Color(42, 33, 20);
	static final Color WARN_TEXT = new Color(240, 180, 74);

	static final int PAD = 8;
	/** Width available inside a padded section. */
	static final int INNER_WIDTH = PluginPanel.PANEL_WIDTH - 2 * PAD;

	private Ui()
	{
	}

	static Font body()
	{
		return FontManager.getRunescapeFont();
	}

	static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	static Font bold()
	{
		return FontManager.getRunescapeBoldFont();
	}

	static JLabel label(String text, Font font, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		return l;
	}

	/** A label showing plain text word-wrapped to width (measured with the font, so pixel fonts wrap right). */
	static JLabel wrapped(String text, Font font, Color color, int width)
	{
		return label(wrapHtml(text, font, width, false), font, color);
	}

	static String wrapHtml(String text, Font font, int width, boolean center)
	{
		FontMetrics fm = new JLabel().getFontMetrics(font);
		StringBuilder html = new StringBuilder(center ? "<html><center>" : "<html>");
		StringBuilder line = new StringBuilder();
		for (String word : (text == null ? "" : text).split(" "))
		{
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && fm.stringWidth(candidate) > width)
			{
				html.append(esc(line.toString())).append("<br>");
				line.setLength(0);
				line.append(word);
			}
			else
			{
				line.setLength(0);
				line.append(candidate);
			}
		}
		html.append(esc(line.toString()));
		return html.append(center ? "</center></html>" : "</html>").toString();
	}

	static JLabel caption(String text)
	{
		return label(text.toUpperCase(), small(), MUTED);
	}

	static String esc(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	/** A vertical stack of full-width children. */
	static class Column extends JPanel
	{
		private final int gap;
		private int row;

		Column(int gap)
		{
			super(new GridBagLayout());
			this.gap = gap;
			setOpaque(false);
		}

		Component addRow(Component c)
		{
			GridBagConstraints g = new GridBagConstraints();
			g.gridx = 0;
			g.gridy = row++;
			g.weightx = 1;
			g.fill = GridBagConstraints.HORIZONTAL;
			g.anchor = GridBagConstraints.WEST;
			g.insets = new Insets(row == 1 ? 0 : gap, 0, 0, 0);
			add(c, g);
			return c;
		}
	}

	/** Padded block with a divider underneath, like the mockup's .sec. */
	static Column section()
	{
		Column c = new Column(5);
		c.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
			BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD)));
		return c;
	}

	static JPanel kv(String key, String value, Color valueColor)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		p.add(label(key, body(), MUTED), BorderLayout.WEST);
		p.add(label(value, body(), valueColor), BorderLayout.EAST);
		return p;
	}

	static JLabel chip(String html, Color fg, Color bg)
	{
		JLabel l = label("<html>" + html + "</html>", small(), fg);
		l.setOpaque(true);
		l.setBackground(bg);
		l.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
		return l;
	}

	static JPanel flow()
	{
		JPanel p = new JPanel(new FlowWrap(4, 4));
		p.setOpaque(false);
		return p;
	}

	/** Clickable flat label; L&F independent so it looks the same in RuneLite and headless renders. */
	static class FlatButton extends JLabel
	{
		private Color base;
		private Color hover;

		FlatButton(String text, Color fg, Color bg, Color hover, Runnable onClick)
		{
			super(text, SwingConstants.CENTER);
			setFont(bold());
			setForeground(fg);
			setOpaque(true);
			setColors(bg, hover);
			setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
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
					setBackground(FlatButton.this.hover);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					setBackground(base);
				}
			});
		}

		void setColors(Color bg, Color hover)
		{
			this.base = bg;
			this.hover = hover;
			setBackground(bg);
		}
	}

	static FlatButton primary(String text, Runnable onClick)
	{
		return new FlatButton(text, new Color(26, 26, 26), ORANGE, new Color(240, 154, 16), onClick);
	}

	static FlatButton ghost(String text, Runnable onClick)
	{
		return new FlatButton(text, TEXT, ROW, ROW2, onClick);
	}

	static void onClick(JComponent c, Runnable r)
	{
		c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		c.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				r.run();
			}
		});
	}

	/** Horizontal track with a fill, for progress and the XP/GP bars. */
	static class Bar extends JComponent
	{
		private final double fraction;
		private final Color fill;
		private final int height;

		Bar(double fraction, Color fill, int height)
		{
			this.fraction = Math.max(0, Math.min(1, fraction));
			this.fill = fill;
			this.height = height;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(20, height);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = Math.min(height, getHeight());
			int y = (getHeight() - h) / 2;
			g2.setColor(ROW);
			g2.fillRoundRect(0, y, w, h, h, h);
			int fw = (int) Math.round(w * fraction);
			if (fw > 0)
			{
				g2.setColor(fill);
				g2.fillRoundRect(0, y, Math.max(fw, h), h, h, h);
			}
			g2.dispose();
		}
	}

	static Border leftAccent()
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 3, 0, 0, ORANGE),
				BorderFactory.createEmptyBorder(PAD, PAD - 3, PAD, PAD)));
	}

	static JPanel message(String text)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.setBorder(BorderFactory.createEmptyBorder(24, PAD, 24, PAD));
		JLabel l = label(text, body(), MUTED);
		l.setHorizontalAlignment(SwingConstants.CENTER);
		p.add(l, BorderLayout.CENTER);
		return p;
	}

	static JLabel note(String text)
	{
		JLabel l = label(wrapHtml(text, small(), INNER_WIDTH, true), small(), FAINT);
		l.setHorizontalAlignment(SwingConstants.CENTER);
		l.setBorder(BorderFactory.createEmptyBorder(6, PAD, PAD, PAD));
		return l;
	}

	/** FlowLayout that wraps, reporting the height it needs for the container's (or the panel's) width. */
	static class FlowWrap implements LayoutManager
	{
		private final int hgap;
		private final int vgap;

		FlowWrap(int hgap, int vgap)
		{
			this.hgap = hgap;
			this.vgap = vgap;
		}

		private int width(Container parent)
		{
			int w = parent.getWidth();
			if (w <= 0)
			{
				w = INNER_WIDTH;
			}
			Insets in = parent.getInsets();
			return w - in.left - in.right;
		}

		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			int max = width(parent);
			int x = 0;
			int y = 0;
			int rowH = 0;
			for (Component c : parent.getComponents())
			{
				Dimension d = c.getPreferredSize();
				if (x > 0 && x + d.width > max)
				{
					y += rowH + vgap;
					x = 0;
					rowH = 0;
				}
				x += d.width + hgap;
				rowH = Math.max(rowH, d.height);
			}
			Insets in = parent.getInsets();
			return new Dimension(max + in.left + in.right, y + rowH + in.top + in.bottom);
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			return preferredLayoutSize(parent);
		}

		@Override
		public void layoutContainer(Container parent)
		{
			int max = width(parent);
			Insets in = parent.getInsets();
			int x = 0;
			int y = 0;
			int rowH = 0;
			for (Component c : parent.getComponents())
			{
				Dimension d = c.getPreferredSize();
				if (x > 0 && x + d.width > max)
				{
					y += rowH + vgap;
					x = 0;
					rowH = 0;
				}
				c.setBounds(in.left + x, in.top + y, Math.min(d.width, max), d.height);
				x += d.width + hgap;
				rowH = Math.max(rowH, d.height);
			}
		}

		@Override
		public void addLayoutComponent(String name, Component comp)
		{
		}

		@Override
		public void removeLayoutComponent(Component comp)
		{
		}
	}
}
