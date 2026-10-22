package com.slayercompanion.ui;

import com.slayercompanion.gear.Style;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Prep tab: task header, weaknesses, what to bring and the best gear the player owns. */
class PrepTab extends Ui.Column
{
	private static final Font ROW_FONT = Ui.sans(Font.PLAIN, 12f);
	private static final Font CHIP_FONT = Ui.sans(Font.PLAIN, 10f);
	private static final Font WEAK_FONT = Ui.sans(Font.PLAIN, 11f);
	private static final Font SLOT_FONT = Ui.sans(Font.PLAIN, 11f);
	private static final Font STATUS_FONT = Ui.sans(Font.PLAIN, 12f);
	/** Slot card: half the inner width, less border (2), padding (10), icon square (30) and gap (6). */
	private static final int SLOT_TEXT_WIDTH = (Ui.INNER_WIDTH - 4) / 2 - 2 - 10 - 30 - 6 - 3;
	/** Weak-to tile: half the inner width, less border (2), padding (8), icon (14) and gap (4). */
	private static final int WEAK_TEXT_WIDTH = (Ui.INNER_WIDTH - 5) / 2 - 2 - 8 - 14 - 4 - 3;

	private final IconSource icons;
	private final Consumer<Style> onStyle;
	private final Runnable onToggleHighlight;

	PrepTab(IconSource icons, Consumer<Style> onStyle, Runnable onToggleHighlight)
	{
		super(0);
		this.icons = icons;
		this.onStyle = onStyle;
		this.onToggleHighlight = onToggleHighlight;
	}

	void show(PrepModel m)
	{
		removeAll();
		rebuild(m);
		revalidate();
		repaint();
	}

	private void rebuild(PrepModel m)
	{
		if (m.isNoTask())
		{
			addRow(Ui.message(m.getMessage()));
			return;
		}

		addRow(hero(m));

		if (!m.getWeakTo().isEmpty() || !m.getFacts().isEmpty())
		{
			Ui.Column weak = Ui.section("Weak to");
			if (!m.getWeakTo().isEmpty())
			{
				weak.addRow(weakTiles(m.getWeakTo()));
			}
			if (!m.getFacts().isEmpty())
			{
				weak.addRow(chips(m.getFacts()));
			}
			addRow(weak);
		}

		Ui.Column bring = Ui.section("Bring");
		if (m.isNoSpecialItem())
		{
			bring.addRow(Ui.label("No slayer item required", Ui.small(), Ui.OK));
		}
		for (PrepModel.BringLine line : m.getBring())
		{
			bring.addRow(bringRow(line));
		}
		addRow(bring);

		Ui.Column gear = Ui.section("Best owned gear");
		gear.addRow(styleSwitch(m.getStyle()));
		if (!m.getSlots().isEmpty())
		{
			JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
			grid.setOpaque(false);
			for (PrepModel.Slot s : m.getSlots())
			{
				grid.add(slot(s));
			}
			gear.addRow(grid);
		}
		if (m.getWornText() != null)
		{
			gear.addRow(wornNote(m.getWornText()));
		}
		if (m.getBoostNote() != null)
		{
			gear.addRow(Ui.wrapped(m.getBoostNote(), Ui.small(), Ui.FAINT, Ui.INNER_WIDTH));
		}
		if (m.getGearMessage() != null)
		{
			gear.addRow(m.isGearWarning() ? new WarnBox(m.getGearMessage(), Ui.INNER_WIDTH)
				: Ui.wrapped(m.getGearMessage(), Ui.body(), Ui.MUTED, Ui.INNER_WIDTH));
		}
		if (m.getUpgradeItem() != null)
		{
			gear.addRow(upgradeCard(m));
		}
		if (!m.getSlots().isEmpty())
		{
			JPanel button = new JPanel(new BorderLayout());
			button.setOpaque(false);
			button.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
			button.add(Ui.ghost(m.getSetupButtonText(), onToggleHighlight).setPrimary(!m.isHighlighting()));
			gear.addRow(button);
		}
		addRow(gear);

		addRow(Ui.note(m.getFooter()));
	}

	/** Slayer icon beside the orange pixel title and the sans subtitle. */
	private static JPanel hero(PrepModel m)
	{
		Ui.Column hero = Ui.hero();
		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setOpaque(false);
		JPanel icon = new JPanel(new GridBagLayout());
		icon.setOpaque(false);
		icon.add(ItemIcon.skill("slayer", new Dimension(22, 22)));
		top.add(icon, BorderLayout.WEST);
		int textWidth = Ui.INNER_WIDTH - 2 - 22 - 8 - 4;
		Ui.Column text = new Ui.Column(1);
		text.addRow(Ui.title(m.getTitle(), Ui.ORANGE, textWidth));
		text.addRow(Ui.wrapped(m.getSubtitle(), Ui.sans(Font.PLAIN, 11f), Ui.MUTED, textWidth));
		top.add(text, BorderLayout.CENTER);
		hero.addRow(top);
		return hero;
	}

	/** Weaknesses as tiles, two per row: style icon, name (blue for the best) and a faint detail line. */
	private static JPanel weakTiles(List<PrepModel.Weakness> weak)
	{
		JPanel grid = new JPanel(new GridLayout(0, 2, 5, 5));
		grid.setOpaque(false);
		for (PrepModel.Weakness w : weak)
		{
			JPanel tile = new JPanel(new BorderLayout(4, 0));
			tile.setBackground(Ui.CARD);
			tile.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.EDGE_DARK),
				BorderFactory.createEmptyBorder(5, 4, 5, 4)));
			tile.add(centred(ItemIcon.skill(w.getIcon(), new Dimension(14, 14))), BorderLayout.WEST);
			Ui.Column text = new Ui.Column(0);
			text.addRow(Ui.wrapped(w.getName(), WEAK_FONT, w.isBest() ? Ui.BLUE : Ui.TEXT, WEAK_TEXT_WIDTH));
			text.addRow(Ui.wrapped(w.getDetail(), Ui.small(), Ui.FAINT, WEAK_TEXT_WIDTH));
			tile.add(text, BorderLayout.CENTER);
			grid.add(tile);
		}
		return grid;
	}

	/** Boxed fact chips (max hit with the hitpoints icon, attack style, poison in green or red). */
	private static JPanel chips(List<PrepModel.Chip> chips)
	{
		JPanel flow = Ui.flow();
		for (PrepModel.Chip c : chips)
		{
			Color fg = c.getKind() == PrepModel.ChipKind.BLUE ? Ui.BLUE
				: c.getKind() == PrepModel.ChipKind.GOOD ? Ui.OK
				: c.getKind() == PrepModel.ChipKind.BAD ? Ui.BAD : Ui.MUTED;
			JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			chip.setBackground(Ui.CARD);
			chip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.EDGE_DARK),
				BorderFactory.createEmptyBorder(2, 4, 2, 4)));
			if (c.getIcon() != null)
			{
				chip.add(ItemIcon.skill(c.getIcon(), new Dimension(13, 13)));
				chip.add(javax.swing.Box.createHorizontalStrut(4));
			}
			chip.add(Ui.label(c.getBold() + c.getRest(), CHIP_FONT, fg));
			flow.add(chip);
		}
		return flow;
	}

	/** Item icon (dimmed when missing), name, and bank count or "Missing" on the right. */
	private JPanel bringRow(PrepModel.BringLine line)
	{
		JPanel row = new JPanel(new BorderLayout(7, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		row.add(centred(ItemIcon.item(icons, line.getItemId(), ItemIcon.ROW).setDimmed(!line.isHave())),
			BorderLayout.WEST);
		JLabel status = Ui.label(line.getStatus(), STATUS_FONT, line.isHave() ? Ui.OK : Ui.BAD);
		int nameWidth = Ui.INNER_WIDTH - ItemIcon.ROW.width - 14 - status.getPreferredSize().width - 4;
		row.add(Ui.wrapped(Format.capitalize(line.getName()), ROW_FONT, Ui.TEXT, nameWidth), BorderLayout.CENTER);
		row.add(status, BorderLayout.EAST);
		return row;
	}

	/** Melee / Ranged / Magic segmented control, each with its skill icon. */
	private JPanel styleSwitch(Style selected)
	{
		JPanel seg = new JPanel(new GridLayout(1, 3, 2, 0));
		seg.setBackground(Ui.TAB_BG);
		seg.setOpaque(true);
		seg.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.EDGE_DARK),
			BorderFactory.createEmptyBorder(2, 2, 2, 2)));
		for (Style s : Style.values())
		{
			seg.add(segCell(s, s == selected));
		}
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		wrap.add(seg);
		return wrap;
	}

	private JPanel segCell(Style s, boolean on)
	{
		String skill = s == Style.MELEE ? "attack" : s == Style.RANGED ? "ranged" : "magic";
		JPanel cell = new JPanel(new GridBagLayout());
		cell.setOpaque(true);
		Color base = on ? Ui.BUTTON_TOP : Ui.TAB_BG;
		Color hover = on ? Ui.BUTTON_TOP : Ui.HEADER_BG;
		cell.setBackground(base);
		cell.setBorder(on
			? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.BUTTON_SHINE),
				BorderFactory.createEmptyBorder(4, 2, 5, 2))
			: BorderFactory.createEmptyBorder(5, 2, 5, 2));
		GridBagConstraints g = new GridBagConstraints();
		g.insets = new Insets(0, 0, 0, 4);
		cell.add(ItemIcon.skill(skill, new Dimension(15, 15)), g);
		g.insets = new Insets(0, 0, 0, 0);
		cell.add(Ui.label(Format.capitalize(s.name().toLowerCase()), Ui.sans(Font.PLAIN, 11f),
			on ? Ui.YELLOW : Ui.FAINT), g);
		cell.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		cell.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onStyle.accept(s);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				cell.setBackground(hover);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				cell.setBackground(base);
			}
		});
		return cell;
	}

	/** Slot card: icon in a stone square (green when worn), faint slot name over the item name. */
	private JPanel slot(PrepModel.Slot s)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setBackground(Ui.CARD);
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.EDGE_DARK),
			BorderFactory.createEmptyBorder(4, 5, 4, 5)));
		ItemIcon icon = s.getItemId() > 0 ? ItemIcon.item(icons, s.getItemId(), ItemIcon.SLOT)
			: ItemIcon.skill("magic", new Dimension(16, 16));
		p.add(centred(new IconSlot(icon).setWorn(s.isWorn())), BorderLayout.WEST);
		Ui.Column text = new Ui.Column(0);
		text.addRow(Ui.label(s.getLabel(), Ui.small(), Ui.FAINT));
		text.addRow(Ui.wrapped(s.getItem(), SLOT_FONT, s.isWorn() ? Ui.OK : Ui.TEXT, SLOT_TEXT_WIDTH));
		JPanel middle = new JPanel(new GridBagLayout());
		middle.setOpaque(false);
		GridBagConstraints g = new GridBagConstraints();
		g.weightx = 1;
		g.fill = GridBagConstraints.HORIZONTAL;
		middle.add(text, g);
		p.add(middle, BorderLayout.CENTER);
		return p;
	}

	/** "● 3 of 6 worn". */
	private static JPanel wornNote(String text)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
		row.add(new Dot(Ui.OK));
		row.add(javax.swing.Box.createHorizontalStrut(5));
		row.add(Ui.label(text, Ui.sans(Font.PLAIN, 11f), Ui.FAINT));
		return row;
	}

	/** Raised card: large item icon, "Biggest upgrade", the item in yellow, dps gain on the right, cost and needs. */
	private JPanel upgradeCard(PrepModel m)
	{
		Card card = new Card(3, 7);
		card.setRaised();
		JPanel top = new JPanel(new BorderLayout(7, 0));
		top.setOpaque(false);
		top.add(centred(ItemIcon.item(icons, m.getUpgradeItemId(), ItemIcon.LARGE)), BorderLayout.WEST);
		JLabel gain = Ui.label(m.getUpgradeGain(), Ui.sans(Font.BOLD, 11f), Ui.OK);
		int textWidth = Ui.INNER_WIDTH - 16 - ItemIcon.LARGE.width - 14 - gain.getPreferredSize().width - 4;
		Ui.Column text = new Ui.Column(0);
		text.addRow(Ui.label("Biggest upgrade", Ui.small(), Ui.FAINT));
		text.addRow(Ui.wrapped(m.getUpgradeItem(), Ui.bold(), Ui.YELLOW, textWidth));
		top.add(text, BorderLayout.CENTER);
		top.add(gain, BorderLayout.EAST);
		card.addRow(top);
		if (m.getUpgradeCost() != null)
		{
			JPanel cost = new JPanel(new BorderLayout(6, 0));
			cost.setOpaque(false);
			cost.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
			cost.add(Ui.label("Cost", ROW_FONT, Ui.FAINT), BorderLayout.WEST);
			boolean tradeable = !PrepModel.UNTRADEABLE.equals(m.getUpgradeCost());
			JPanel value = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			value.setOpaque(false);
			if (tradeable)
			{
				value.add(ItemIcon.item(icons, 995, ItemIcon.INLINE));
				value.add(javax.swing.Box.createHorizontalStrut(4));
			}
			value.add(Ui.label(m.getUpgradeCost(), ROW_FONT, tradeable ? Ui.TEXT : Ui.MUTED));
			cost.add(value, BorderLayout.EAST);
			card.addRow(cost);
			if (m.getUpgradeSource() != null)
			{
				card.addRow(sourceRow(m.getUpgradeSource(), Ui.INNER_WIDTH - 16));
			}
			card.addRow(needsRow(m.getUpgradeNeeds(), Ui.INNER_WIDTH - 16));
		}
		return card;
	}

	/** "Get" key with how to get an untradeable upgrade, right-aligned and wrapped to the width. */
	private static JPanel sourceRow(String source, int rowWidth)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JLabel key = Ui.label("Get", ROW_FONT, Ui.FAINT);
		key.setVerticalAlignment(JLabel.TOP);
		row.add(key, BorderLayout.WEST);
		String html = Ui.wrapHtml(source, ROW_FONT, rowWidth - key.getPreferredSize().width - 12, false)
			.replaceFirst("^<html>", "<html><div align='right'>").replaceFirst("</html>$", "</div></html>");
		row.add(Ui.label(html, ROW_FONT, Ui.TEXT), BorderLayout.EAST);
		return row;
	}

	/** "Needs" key with comma-separated requirements, each green with a tick when met, wrapped to the width. */
	private static JPanel needsRow(List<PrepModel.Need> needs, int rowWidth)
	{
		if (needs.isEmpty())
		{
			JPanel p = new JPanel(new BorderLayout(6, 0));
			p.setOpaque(false);
			p.add(Ui.label("Needs", ROW_FONT, Ui.FAINT), BorderLayout.WEST);
			p.add(Ui.label(PrepModel.NO_REQUIREMENTS, ROW_FONT, Ui.MUTED), BorderLayout.EAST);
			return p;
		}
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JLabel key = Ui.label("Needs", ROW_FONT, Ui.FAINT);
		key.setVerticalAlignment(JLabel.TOP);
		row.add(key, BorderLayout.WEST);
		int width = rowWidth - key.getPreferredSize().width - 12;
		FontMetrics fm = key.getFontMetrics(ROW_FONT);
		StringBuilder html = new StringBuilder("<html><div align='right'>");
		int lineWidth = 0;
		for (int i = 0; i < needs.size(); i++)
		{
			PrepModel.Need n = needs.get(i);
			String text = n.getText() + (n.isMet() ? " ✓" : "") + (i < needs.size() - 1 ? "," : "");
			int w = fm.stringWidth(text) + (n.isMet() ? 4 : 0);
			if (lineWidth > 0)
			{
				if (lineWidth + fm.stringWidth(" ") + w > width)
				{
					html.append("<br>");
					lineWidth = 0;
				}
				else
				{
					html.append(' ');
					lineWidth += fm.stringWidth(" ");
				}
			}
			// Non-breaking spaces keep one requirement on one line.
			html.append("<font color='").append(Ui.hex(n.isMet() ? Ui.OK : Ui.BAD)).append("'>")
				.append(glyphs(Ui.esc(text).replace(" ", "&nbsp;"))).append("</font>");
			lineWidth += w;
		}
		row.add(Ui.label(html.append("</div></html>").toString(), ROW_FONT, Ui.TEXT), BorderLayout.EAST);
		return row;
	}

	/** Draw characters the sans font lacks (✓) in a fallback font so they don't show as boxes. */
	private static String glyphs(String html)
	{
		StringBuilder out = new StringBuilder();
		for (char c : html.toCharArray())
		{
			if (c > 127 && !ROW_FONT.canDisplay(c))
			{
				out.append("<font face='Dialog'>").append(c).append("</font>");
			}
			else
			{
				out.append(c);
			}
		}
		return out.toString();
	}

	/** Wraps a fixed-size component so it sits vertically centred in a BorderLayout side. */
	private static JPanel centred(JComponent c)
	{
		JPanel p = new JPanel(new GridBagLayout());
		p.setOpaque(false);
		p.add(c);
		return p;
	}

	/** Small filled circle (.dot). */
	private static final class Dot extends JComponent
	{
		private final Color color;

		Dot(Color color)
		{
			this.color = color;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(7, 7);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			g2.fillOval(0, 0, 7, 7);
			g2.dispose();
		}
	}
}
