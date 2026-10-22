package com.slayercompanion.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Where to go tab: ranked locations as stone cards, Take me there per card, and sending safespots in. */
class WhereToGoTab extends Ui.Column
{
	/** Width inside a card: the section's inner width less the card's 1px edge and 8px padding. */
	private static final int CARD_INNER = Ui.INNER_WIDTH - 18;
	private static final Color LOCKED_BG = new Color(0x26201a);

	private final IconSource icons;
	private final Consumer<String> onSelect;
	private final Runnable onTakeMeThere;
	private final Runnable onSendSafespots;
	private WhereToGoModel model;
	private String actionMessage;

	WhereToGoTab(IconSource icons, Consumer<String> onSelect, Runnable onTakeMeThere, Runnable onSendSafespots)
	{
		super(0);
		this.icons = icons;
		this.onSelect = onSelect;
		this.onTakeMeThere = onTakeMeThere;
		this.onSendSafespots = onSendSafespots;
	}

	void show(WhereToGoModel m)
	{
		model = m;
		refresh();
	}

	/** Result line under the buttons ("Shortest Path plugin not installed", "Copied 3 safespots"); null clears it. */
	void showActionMessage(String message)
	{
		actionMessage = message;
		refresh();
	}

	private void refresh()
	{
		removeAll();
		if (model != null)
		{
			rebuild(model);
		}
		revalidate();
		repaint();
	}

	private void rebuild(WhereToGoModel m)
	{
		if (m.isNoTask())
		{
			addRow(Ui.message(m.getMessage()));
			return;
		}
		if (m.getTitle() != null)
		{
			addRow(hero(m));
		}

		Ui.Column list = new Ui.Column(6);
		list.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, Ui.SECTION_LINE),
			BorderFactory.createEmptyBorder(Ui.PAD, Ui.PAD, Ui.PAD, Ui.PAD)));
		for (WhereToGoModel.Row r : m.getRows())
		{
			list.addRow(r.isLocked() ? locked(r) : card(r));
		}
		StoneButton export = Ui.ghost("Export safespots", onSendSafespots).small();
		list.addRow(export);
		if (actionMessage != null)
		{
			list.addRow(Ui.wrapped(actionMessage, Ui.small(), Ui.WARN_TEXT, Ui.INNER_WIDTH));
		}
		addRow(list);
		addRow(Ui.note(m.getNote()));
	}

	private static JPanel hero(WhereToGoModel m)
	{
		Ui.Column hero = Ui.hero();
		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setOpaque(false);
		top.add(ItemIcon.skill("slayer", new Dimension(22, 22)), BorderLayout.WEST);
		Ui.Column text = new Ui.Column(1);
		int width = Ui.INNER_WIDTH - 2 - 30;
		text.addRow(Ui.title(m.getTitle(), Ui.ORANGE, width));
		if (m.getSubtitle() != null)
		{
			text.addRow(Ui.wrapped(m.getSubtitle(), Ui.sans(Font.PLAIN, 11), Ui.MUTED, width));
		}
		top.add(text, BorderLayout.CENTER);
		hero.addRow(top);
		return hero;
	}

	private Card card(WhereToGoModel.Row r)
	{
		boolean best = r.isRecommended();
		Card c = new Card(0, 8);
		if (best)
		{
			// The "Best pick" badge sits flush against the orange top edge, so no top padding or inner top line.
			c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.ORANGE),
				BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, Ui.BEST_INNER),
					BorderFactory.createEmptyBorder(0, 7, 7, 7))));
		}
		else if (r.isSelected())
		{
			c.setRaised();
		}

		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		JLabel right;
		if (best)
		{
			right = badge("Best pick");
		}
		else
		{
			right = Ui.label(r.isSelected() ? "Selected · " + r.getTravel() : r.getTravel(), Ui.small(),
				r.isSelected() ? Ui.ORANGE : Ui.FAINT);
		}
		JPanel east = new JPanel(new BorderLayout());
		east.setOpaque(false);
		east.add(right, BorderLayout.NORTH);
		top.add(east, BorderLayout.EAST);
		int nameWidth = CARD_INNER - right.getPreferredSize().width - 8;
		JLabel name = Ui.wrapped(r.getName(), nameFont(), Ui.TEXT, nameWidth);
		if (best)
		{
			name.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		}
		top.add(name, BorderLayout.CENTER);
		c.addRow(top);

		if (r.getWhy() != null || best)
		{
			JPanel why = new JPanel(new BorderLayout(6, 0));
			why.setOpaque(false);
			why.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
			JLabel travel = Ui.label(r.getTravel(), Ui.small(), Ui.FAINT);
			if (r.getWhy() != null)
			{
				why.add(Ui.wrapped(r.getWhy(), Ui.sans(Font.PLAIN, 11), Ui.ORANGE,
					CARD_INNER - travel.getPreferredSize().width - 6), BorderLayout.CENTER);
			}
			why.add(travel, BorderLayout.EAST);
			c.addRow(why);
		}

		JPanel tags = new JPanel(new Ui.FlowWrap(3, 3));
		tags.setOpaque(false);
		// FlowWrap measures against the current width; start at the card's, so the first height is right.
		tags.setSize(CARD_INNER, 1);
		for (WhereToGoModel.Tag t : r.getTags())
		{
			tags.add(new TagPill(t.getText(), pillKind(t.getKind())));
		}
		tags.setBorder(BorderFactory.createEmptyBorder(5, 0, 3, 0));
		c.addRow(tags);

		c.addRow(bars(r));

		StoneButton go = new StoneButton(best ? "➜ Take me there" : "Take me there", best, () ->
		{
			if (!r.isSelected())
			{
				onSelect.accept(r.getLocationId());
			}
			onTakeMeThere.run();
		}).small();
		JPanel goWrap = new JPanel(new BorderLayout());
		goWrap.setOpaque(false);
		goWrap.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		goWrap.add(go, BorderLayout.CENTER);
		c.addRow(goWrap);

		Ui.onClick(c, () -> onSelect.accept(r.getLocationId()));
		return c;
	}

	/** Dim card: faint name and a padlock with the reason. Still clickable, so it can be inspected in Prep. */
	private Card locked(WhereToGoModel.Row r)
	{
		Card c = new Card(3, 8);
		c.setBackground(LOCKED_BG);
		if (r.isSelected())
		{
			c.setRaised();
		}
		c.addRow(Ui.wrapped(r.getName(), nameFont(), Ui.FAINT, CARD_INNER));
		for (WhereToGoModel.Tag t : r.getTags())
		{
			JLabel why = Ui.label(Ui.wrapHtml(t.getText(), Ui.sans(Font.PLAIN, 11), CARD_INNER - 14, false),
				Ui.sans(Font.PLAIN, 11), Ui.FAINT);
			why.setIcon(new Padlock());
			why.setIconTextGap(5);
			c.addRow(why);
		}
		Ui.onClick(c, () -> onSelect.accept(r.getLocationId()));
		return c;
	}

	private static Font nameFont()
	{
		return Ui.sans(Font.BOLD, 12f);
	}

	/** Orange "Best pick" tab with dark text. */
	private static JLabel badge(String text)
	{
		JLabel b = Ui.label(text, Ui.sans(Font.BOLD, 10), new Color(0x1a1100));
		b.setOpaque(true);
		b.setBackground(Ui.ORANGE);
		b.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
		return b;
	}

	static TagPill.Kind pillKind(WhereToGoModel.TagKind kind)
	{
		switch (kind)
		{
			case SAFE:
				return TagPill.Kind.OK;
			case BLUE:
				return TagPill.Kind.BLUE;
			case GOLD:
				return TagPill.Kind.GOLD;
			case RISK:
				return TagPill.Kind.BAD;
			default:
				return TagPill.Kind.NEUTRAL;
		}
	}

	private static JPanel bars(WhereToGoModel.Row r)
	{
		JPanel g = new JPanel(new GridBagLayout());
		g.setOpaque(false);
		addBar(g, 0, "XP", r.getXpFraction(), Ui.BLUE, r.getXpText());
		addBar(g, 1, "GP", r.getGpFraction(), Ui.YELLOW, r.getGpText());
		return g;
	}

	private static void addBar(JPanel g, int y, String label, double fraction, Color color, String value)
	{
		Font valueFont = Ui.sans(Font.PLAIN, 11);
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = y;
		c.insets = new Insets(3, 0, 0, 6);
		c.anchor = GridBagConstraints.WEST;
		c.gridx = 0;
		JLabel l = Ui.label(label, Ui.sans(Font.BOLD, 9.5f), Ui.FAINT);
		l.setPreferredSize(new Dimension(18, l.getPreferredSize().height));
		g.add(l, c);
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		g.add(new Gauge(fraction, color, 8), c);
		c.gridx = 2;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(3, 0, 0, 0);
		JLabel v = Ui.label(value, valueFont, Ui.TEXT);
		v.setHorizontalAlignment(JLabel.RIGHT);
		int w = Math.max(32, v.getFontMetrics(valueFont).stringWidth("000k") + 2);
		v.setPreferredSize(new Dimension(w, v.getPreferredSize().height));
		g.add(v, c);
	}

	/** A tiny drawn padlock (the emoji glyph doesn't render in Swing's sans font). */
	private static final class Padlock implements Icon
	{
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			g.setColor(Ui.FAINT);
			// Shackle.
			g.drawRect(x + 2, y + 1, 4, 5);
			// Body.
			g.fillRect(x, y + 5, 9, 6);
			g.setColor(LOCKED_BG);
			g.fillRect(x + 3, y + 1 + 1, 3, 3);
			g.fillRect(x + 4, y + 7, 1, 2);
		}

		@Override
		public int getIconWidth()
		{
			return 9;
		}

		@Override
		public int getIconHeight()
		{
			return 11;
		}
	}
}
