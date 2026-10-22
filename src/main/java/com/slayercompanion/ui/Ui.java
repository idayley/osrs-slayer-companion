package com.slayercompanion.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
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
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Shared look for the panel: the in-game "stone" theme (brown frames, gold text) and small building blocks.
 * Pixel font only for titles, tabs and section headers; everything else is an antialiased sans.
 */
final class Ui
{
	// Stone surfaces
	static final Color PANEL = new Color(0x3e3529);
	static final Color CARD = new Color(0x2e271e);
	static final Color EDGE_DARK = new Color(0x1c1712);
	static final Color EDGE_LIGHT = new Color(0x5a4d3b);
	static final Color HEADER_BG = new Color(0x4a3f30);
	static final Color TAB_BG = new Color(0x2b241b);
	static final Color TAB_ACTIVE = new Color(0x5a4d3b);
	/** Divider under a section (.sec border). */
	static final Color SECTION_LINE = new Color(0x2b241b);
	static final Color BUTTON_TOP = new Color(0x6b5a42);
	static final Color BUTTON_BOTTOM = new Color(0x4a3f30);
	static final Color BUTTON_SHINE = new Color(0x8a7656);
	/** Inner line of the orange "best pick" card. */
	static final Color BEST_INNER = new Color(0x6b4a1c);

	// Text and accents
	static final Color TEXT = new Color(0xffcc33);
	static final Color MUTED = new Color(0xd6c6a0);
	static final Color FAINT = new Color(0x9c8a66);
	static final Color ORANGE = new Color(0xff981f);
	static final Color YELLOW = new Color(0xffff00);
	static final Color OK = new Color(0x3dd34b);
	static final Color BAD = new Color(0xff4d3d);
	static final Color BLUE = new Color(0x4fc3ff);
	static final Color WARN_BG = new Color(0x5a1f12);
	static final Color WARN_TEXT = new Color(0xffb38a);

	// Older names, kept so existing call sites read the stone palette.
	static final Color BG = PANEL;
	static final Color ROW = CARD;
	static final Color ROW2 = HEADER_BG;
	static final Color LINE = EDGE_DARK;
	static final Color GREEN = OK;
	static final Color GOLD = YELLOW;
	static final Color RISK = BAD;
	static final Color SELECTED_BG = CARD;

	static final int PAD = 9;
	/** Width available inside a padded section. */
	static final int INNER_WIDTH = PluginPanel.PANEL_WIDTH - 2 * PAD;

	private static final String SANS = Font.SANS_SERIF;
	private static final Font BODY = new Font(SANS, Font.PLAIN, 12);
	private static final Font BODY_BOLD = new Font(SANS, Font.BOLD, 12);
	private static final Font SMALL = new Font(SANS, Font.PLAIN, 10);
	private static final Font SMALL_BOLD = new Font(SANS, Font.BOLD, 10);

	private Ui()
	{
	}

	/** Body text: sans 12. */
	static Font body()
	{
		return BODY;
	}

	/** Small labels (slot names, stat captions, tags): sans 10. */
	static Font small()
	{
		return SMALL;
	}

	static Font smallBold()
	{
		return SMALL_BOLD;
	}

	/** Bold body (names, buttons): sans bold 12. */
	static Font bold()
	{
		return BODY_BOLD;
	}

	/** Sans at any size, for big numbers (stat values 16, ring number 22). */
	static Font sans(int style, float size)
	{
		return BODY.deriveFont(style, size);
	}

	/** Pixel font for titles (RuneScape bold, 16). */
	static Font titleFont()
	{
		// deriveFont drops the UIResource marker, so the look and feel can't swap the font back out.
		Font f = FontManager.getRunescapeBoldFont();
		return f.deriveFont(f.getStyle(), f.getSize2D());
	}

	/** Pixel font for section headers (RuneScape bold). */
	static Font headerFont()
	{
		return titleFont();
	}

	/** Pixel font for the tab strip (RuneScape regular). */
	static Font pixelFont()
	{
		Font f = FontManager.getRunescapeFont();
		return f.deriveFont(f.getStyle(), f.getSize2D());
	}

	static boolean isPixel(Font f)
	{
		return f != null && f.getFamily().toLowerCase().contains("runescape");
	}

	/** Text antialiasing on for sans text, off for the pixel fonts (they are drawn at their native size). */
	static <T extends JComponent> T aa(T c)
	{
		c.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, isPixel(c.getFont())
			? RenderingHints.VALUE_TEXT_ANTIALIAS_OFF : RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		return c;
	}

	static JLabel label(String text, Font font, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		return aa(l);
	}

	/** Pixel-font title with a 1px black shadow, word-wrapped to width. */
	static ShadowLabel title(String text, Color color, int width)
	{
		ShadowLabel l = new ShadowLabel(text).wrap(width);
		l.setFont(titleFont());
		l.setForeground(color);
		return aa(l);
	}

	/** Section header: pixel font, orange, upper case, 1px shadow. */
	static ShadowLabel header(String text)
	{
		ShadowLabel l = new ShadowLabel(text.toUpperCase());
		l.setFont(headerFont());
		l.setForeground(ORANGE);
		return aa(l);
	}

	/** Section header with a faint note on the right ("bar end = task end"). */
	static JPanel header(String text, String note)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		p.add(header(text), BorderLayout.WEST);
		p.add(label(note, small(), FAINT), BorderLayout.EAST);
		return p;
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

	/** Old name for {@link #header(String)}. */
	static JLabel caption(String text)
	{
		return header(text);
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

	/** Padded block with a 2px divider underneath, like the mockup's .sec. */
	static Column section()
	{
		Column c = new Column(5);
		c.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, SECTION_LINE),
			BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD)));
		return c;
	}

	/** Section with a pixel-font header as its first row. */
	static Column section(String header)
	{
		Column c = section();
		c.addRow(header(header));
		return c;
	}

	/** Task header block: lighter stone with a 2px dark edge underneath. */
	static Column hero()
	{
		Column c = new Column(4);
		c.setOpaque(true);
		c.setBackground(HEADER_BG);
		c.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, EDGE_DARK),
			BorderFactory.createEmptyBorder(10, 10, 10, 10)));
		return c;
	}

	/** Key in faint, value on the right in the given colour (.kv). */
	static JPanel kv(String key, String value, Color valueColor)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		p.add(label(key, body(), FAINT), BorderLayout.WEST);
		p.add(label(value, body(), valueColor), BorderLayout.EAST);
		return p;
	}

	/** Small boxed chip on inner stone (.chip). */
	static JLabel chip(String html, Color fg, Color bg)
	{
		JLabel l = label("<html>" + html + "</html>", small(), fg);
		l.setOpaque(true);
		l.setBackground(bg);
		l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(EDGE_DARK),
			BorderFactory.createEmptyBorder(2, 5, 2, 5)));
		return l;
	}

	static JPanel flow()
	{
		JPanel p = new JPanel(new FlowWrap(4, 4));
		p.setOpaque(false);
		return p;
	}

	/** Clickable flat label (segmented-control cells); L&F independent so it renders the same headless. */
	static class FlatButton extends JLabel
	{
		private Color base;
		private Color hover;

		FlatButton(String text, Color fg, Color bg, Color hover, Runnable onClick)
		{
			super(text, SwingConstants.CENTER);
			setFont(body());
			setForeground(fg);
			setOpaque(true);
			setColors(bg, hover);
			setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			aa(this);
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

	/** Stone button with yellow text. */
	static StoneButton primary(String text, Runnable onClick)
	{
		return new StoneButton(text, true, onClick);
	}

	/** Stone button with gold text. */
	static StoneButton ghost(String text, Runnable onClick)
	{
		return new StoneButton(text, false, onClick);
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

	/** Orange 1px frame with an inner brown line, for the selected / best-pick row. */
	static Border leftAccent()
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ORANGE),
			BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BEST_INNER),
				BorderFactory.createEmptyBorder(PAD - 2, PAD - 2, PAD - 2, PAD - 2)));
	}

	static JPanel message(String text)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.setBorder(BorderFactory.createEmptyBorder(24, PAD, 24, PAD));
		JLabel l = wrapped(text, body(), MUTED, INNER_WIDTH);
		l.setHorizontalAlignment(SwingConstants.CENTER);
		p.add(l, BorderLayout.CENTER);
		return p;
	}

	/** Centred faint footer line (.foot). */
	static JLabel note(String text)
	{
		JLabel l = label(wrapHtml(text, small(), INNER_WIDTH, true), small(), FAINT);
		l.setHorizontalAlignment(SwingConstants.CENTER);
		l.setBorder(BorderFactory.createEmptyBorder(8, PAD, 8, PAD));
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
