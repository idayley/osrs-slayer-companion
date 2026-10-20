package com.slayercompanion.ui;

import com.slayercompanion.gear.Style;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Prep tab: task header, weaknesses, what to bring and the best gear the player owns. */
class PrepTab extends Ui.Column
{
	private static final int SLOT_TEXT_WIDTH = (Ui.INNER_WIDTH - 4) / 2 - 14;

	private final Consumer<Style> onStyle;
	private final Runnable onToggleHighlight;

	PrepTab(Consumer<Style> onStyle, Runnable onToggleHighlight)
	{
		super(0);
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

		Ui.Column header = Ui.section();
		header.addRow(Ui.wrapped(m.getTitle(), Ui.bold().deriveFont(20f), Ui.ORANGE, Ui.INNER_WIDTH));
		header.addRow(Ui.wrapped(m.getSubtitle(), Ui.body(), Ui.MUTED, Ui.INNER_WIDTH));
		addRow(header);

		if (!m.getWeakTo().isEmpty() || !m.getFacts().isEmpty())
		{
			Ui.Column weak = Ui.section();
			weak.addRow(Ui.caption("Weak to"));
			if (!m.getWeakTo().isEmpty())
			{
				weak.addRow(chips(m.getWeakTo()));
			}
			if (!m.getFacts().isEmpty())
			{
				weak.addRow(chips(m.getFacts()));
			}
			addRow(weak);
		}

		Ui.Column bring = Ui.section();
		bring.addRow(Ui.caption("Bring"));
		if (m.isNoSpecialItem())
		{
			bring.addRow(Ui.label("No special slayer item needed", Ui.body(), Ui.GREEN));
		}
		for (PrepModel.BringLine line : m.getBring())
		{
			JPanel row = new JPanel(new BorderLayout(6, 0));
			row.setOpaque(false);
			row.add(Ui.wrapped(Format.capitalize(line.getName()), Ui.body(), Ui.TEXT, Ui.INNER_WIDTH - 70),
				BorderLayout.CENTER);
			row.add(Ui.label(line.getStatus(), Ui.body(), line.isHave() ? Ui.GREEN : Ui.RISK), BorderLayout.EAST);
			bring.addRow(row);
		}
		addRow(bring);

		Ui.Column gear = Ui.section();
		gear.addRow(Ui.caption("Best gear you own"));
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
		if (m.getGearMessage() != null)
		{
			gear.addRow(Ui.wrapped(m.getGearMessage(), Ui.body(), Ui.MUTED, Ui.INNER_WIDTH));
		}
		if (m.getUpgradeItem() != null)
		{
			// Keep the item name on one line (non-breaking spaces) so it can be coloured after wrapping.
			String item = Ui.esc(m.getUpgradeItem().replace(' ', '\u00a0'));
			String html = Ui.wrapHtml(m.getUpgradeText().replace(m.getUpgradeItem(), m.getUpgradeItem().replace(' ', '\u00a0')),
				Ui.body(), Ui.INNER_WIDTH - 6, false);
			html = html.replace(item, "<font color='" + Ui.hex(Ui.GOLD) + "'>" + item + "</font>");
			gear.addRow(Ui.label(html, Ui.body(), Ui.MUTED));
		}
		if (!m.getSlots().isEmpty())
		{
			Ui.FlatButton highlight = Ui.ghost("Highlight these in my bank", onToggleHighlight);
			if (m.isHighlighting())
			{
				highlight.setForeground(Ui.ORANGE);
				highlight.setColors(Ui.ROW2, Ui.ROW2);
			}
			gear.addRow(highlight);
		}
		addRow(gear);

		addRow(Ui.note(m.getFooter()));
	}

	private static JPanel chips(java.util.List<PrepModel.Chip> chips)
	{
		JPanel flow = Ui.flow();
		for (PrepModel.Chip c : chips)
		{
			Color fg = c.getKind() == PrepModel.ChipKind.BLUE ? Ui.BLUE
				: c.getKind() == PrepModel.ChipKind.GOOD ? Ui.GREEN : Ui.TEXT;
			String html = (c.getBold().isEmpty() ? "" : "<b>" + Ui.esc(c.getBold()) + "</b>") + Ui.esc(c.getRest());
			flow.add(Ui.chip(html, fg, Ui.ROW));
		}
		return flow;
	}

	private JPanel styleSwitch(Style selected)
	{
		JPanel seg = new JPanel(new GridLayout(1, 3, 2, 0));
		seg.setBackground(Ui.ROW);
		seg.setOpaque(true);
		seg.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		for (Style s : Style.values())
		{
			boolean on = s == selected;
			Ui.FlatButton b = new Ui.FlatButton(Format.capitalize(s.name().toLowerCase()), on ? Ui.ORANGE : Ui.MUTED,
				on ? Ui.ROW2 : Ui.ROW, Ui.ROW2, () -> onStyle.accept(s));
			b.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			seg.add(b);
		}
		return seg;
	}

	private static JPanel slot(PrepModel.Slot s)
	{
		JPanel p = new JPanel(new BorderLayout(0, 1));
		p.setBackground(Ui.ROW);
		p.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		p.add(Ui.label(s.getLabel(), Ui.small(), Ui.FAINT), BorderLayout.NORTH);
		JLabel item = Ui.wrapped(s.getItem(), Ui.body(), Ui.TEXT, SLOT_TEXT_WIDTH);
		p.add(item, BorderLayout.CENTER);
		return p;
	}
}
