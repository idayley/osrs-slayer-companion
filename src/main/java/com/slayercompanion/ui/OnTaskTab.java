package com.slayercompanion.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** On task tab: progress, live stats, supplies and worth-knowing lines. */
class OnTaskTab extends Ui.Column
{
	OnTaskTab()
	{
		super(0);
	}

	void show(OnTaskModel m)
	{
		removeAll();
		rebuild(m);
		revalidate();
		repaint();
	}

	private void rebuild(OnTaskModel m)
	{
		if (m.isNoTask())
		{
			addRow(Ui.message(m.getMessage()));
			return;
		}

		Ui.Column head = Ui.section();
		head.addRow(Ui.caption(m.getHeader()));
		JPanel big = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		big.setOpaque(false);
		big.add(Ui.label(String.valueOf(m.getRemaining()), Ui.bold().deriveFont(36f), Ui.ORANGE));
		JLabel of = Ui.label("  left of " + m.getTotal(), Ui.body(), Ui.MUTED);
		big.add(of);
		head.addRow(big);
		head.addRow(new Ui.Bar(m.getProgress(), Ui.ORANGE, 8));
		addRow(head);

		Ui.Column statsSec = Ui.section();
		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
		grid.setOpaque(false);
		for (OnTaskModel.Stat s : m.getStats())
		{
			grid.add(stat(s));
		}
		statsSec.addRow(grid);
		addRow(statsSec);

		Ui.Column supplies = Ui.section();
		supplies.addRow(Ui.caption("Supplies"));
		if (m.getFoodWarning() != null)
		{
			JLabel warn = Ui.wrapped(m.getFoodWarning(), Ui.body(), Ui.WARN_TEXT, Ui.INNER_WIDTH - 12);
			warn.setOpaque(true);
			warn.setBackground(Ui.WARN_BG);
			warn.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
			supplies.addRow(warn);
		}
		if (m.getFoodLine() != null)
		{
			supplies.addRow(Ui.kv("Food", m.getFoodLine(), Ui.TEXT));
		}
		supplies.addRow(Ui.kv(m.getPrayerLabel(), m.getPrayer(), Ui.TEXT));
		addRow(supplies);

		Ui.Column worth = Ui.section();
		worth.addRow(Ui.caption("Worth knowing"));
		worth.addRow(Ui.kv("Superiors this task", m.getSuperiors(), Ui.TEXT));
		worth.addRow(Ui.kv("Best drop", m.getBestDrop(), Ui.GOLD));
		if (m.getLevelLabel() != null)
		{
			worth.addRow(Ui.kv(m.getLevelLabel(), m.getLevelValue(), Ui.TEXT));
		}
		worth.addRow(Ui.kv("Next task", m.getNextTask(), Ui.GOLD));
		addRow(worth);

		addRow(Ui.note(m.getNote()));
	}

	private static JPanel stat(OnTaskModel.Stat s)
	{
		JPanel p = new JPanel(new BorderLayout(0, 2));
		p.setBackground(Ui.ROW);
		p.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
		p.add(Ui.label(s.getLabel(), Ui.small(), Ui.FAINT), BorderLayout.NORTH);
		JLabel v = Ui.label("<html>" + Ui.esc(s.getValue()) + (s.getUnit().isEmpty() ? ""
			: "<font color='" + Ui.hex(Ui.MUTED) + "'>" + Ui.esc(s.getUnit()) + "</font>") + "</html>",
			Ui.bold().deriveFont(18f), Ui.TEXT);
		p.add(v, BorderLayout.CENTER);
		return p;
	}
}
