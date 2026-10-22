package com.slayercompanion.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** On task tab: progress ring, live stats, profit, supply runways and task info. */
class OnTaskTab extends Ui.Column
{
	private static final int RING_GAP = 10;
	private static final String NONE = "—";

	private final IconSource icons;

	OnTaskTab(IconSource icons)
	{
		super(0);
		this.icons = icons;
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
		addRow(hero(m));
		addRow(stats(m));
		addRow(supplies(m));
		addRow(taskInfo(m));
		addRow(Ui.note(m.getNote()));
	}


	/** Ring with kills left beside the task title, location and progress line. */
	private static JComponent hero(OnTaskModel m)
	{
		Ui.Column hero = Ui.hero();
		JPanel row = new JPanel(new BorderLayout(RING_GAP, 0));
		row.setOpaque(false);
		row.add(new ProgressRing(m.getProgress(), String.valueOf(m.getRemaining()), "left"), BorderLayout.WEST);

		int textWidth = Ui.INNER_WIDTH - 70 - RING_GAP - 16;
		Ui.Column text = new Ui.Column(1);
		String title = m.getTaskName() != null ? m.getTaskName() : m.getHeader();
		text.addRow(Ui.title(title, Ui.ORANGE, textWidth));
		if (m.getLocationName() != null)
		{
			text.addRow(Ui.wrapped(m.getLocationName(), Ui.sans(Font.PLAIN, 11.5f), Ui.MUTED, textWidth));
		}
		if (m.getProgressLine() != null)
		{
			text.addRow(Ui.wrapped(m.getProgressLine(), Ui.sans(Font.PLAIN, 11.5f), Ui.MUTED, textWidth));
		}
		JPanel center = new JPanel(new java.awt.GridBagLayout());
		center.setOpaque(false);
		java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
		g.weightx = 1;
		g.fill = java.awt.GridBagConstraints.HORIZONTAL;
		center.add(text, g);
		row.add(center, BorderLayout.CENTER);
		hero.addRow(row);
		return hero;
	}

	/** 2x2 stat tiles, then the profit sum card. */
	private static JComponent stats(OnTaskModel m)
	{
		Ui.Column sec = Ui.section();
		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
		grid.setOpaque(false);
		grid.add(tile(m, "Per kill", Ui.TEXT));
		grid.add(tile(m, "Kills / hr", Ui.TEXT));
		grid.add(tile(m, "Slayer XP / hr", Ui.TEXT));
		grid.add(tile(m, "Loot so far", Ui.YELLOW));
		sec.addRow(grid);

		Card profit = new Card(2, 7);
		profit.addRow(sumRow("Loot / kill", stat(m, "GP per kill"), Ui.YELLOW, false));
		String cost = stat(m, "Supplies / kill");
		profit.addRow(sumRow("Supplies / kill", NONE.equals(cost) ? cost : "− " + cost, Ui.BAD, false));
		JPanel line = new JPanel();
		line.setBackground(Ui.EDGE_LIGHT);
		line.setPreferredSize(new Dimension(10, 1));
		profit.addRow(spaced(line, 2));
		profit.addRow(sumRow("Profit / kill", stat(m, "Profit / kill"), m.isProfitNegative() ? Ui.BAD : Ui.OK, true));
		sec.addRow(profit);
		return sec;
	}

	private static JComponent spaced(JComponent c, int v)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.setBorder(BorderFactory.createEmptyBorder(v, 0, v, 0));
		p.add(c, BorderLayout.CENTER);
		return p;
	}

	private static StatTile tile(OnTaskModel m, String label, Color color)
	{
		OnTaskModel.Stat s = find(m, label);
		if (s == null)
		{
			return new StatTile(label, NONE, "", color);
		}
		return new StatTile(label, s.getValue(), s.getUnit(), color);
	}

	private static String stat(OnTaskModel m, String label)
	{
		OnTaskModel.Stat s = find(m, label);
		return s == null ? NONE : s.getValue();
	}

	private static OnTaskModel.Stat find(OnTaskModel m, String label)
	{
		for (OnTaskModel.Stat s : m.getStats())
		{
			if (s.getLabel().equals(label))
			{
				return s;
			}
		}
		return null;
	}

	private static JPanel sumRow(String key, String value, Color color, boolean big)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		p.add(Ui.label(key, Ui.sans(Font.PLAIN, big ? 13f : 11.5f), Ui.FAINT), BorderLayout.WEST);
		p.add(Ui.label(value, Ui.sans(Font.BOLD, big ? 16f : 12f), color), BorderLayout.EAST);
		return p;
	}

	/** Supply runways: bar end = task end; red when short, white tick where the task ends when it lasts longer. */
	private JComponent supplies(OnTaskModel m)
	{
		Ui.Column sec = Ui.section();
		sec.addRow(Ui.header("Supplies", "bar end = task end"));
		for (OnTaskModel.Supply s : m.getSupplies())
		{
			if (s.getWarning() != null)
			{
				sec.addRow(new WarnBox(s.getWarning(), Ui.INNER_WIDTH));
			}
			sec.addRow(supplyRow(s));
		}
		return sec;
	}

	private JComponent supplyRow(OnTaskModel.Supply s)
	{
		Ui.Column col = new Ui.Column(3);
		JPanel top = row(4);
		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
		left.setOpaque(false);
		ItemIcon icon = s.getSkillIcon() != null ? ItemIcon.skill(s.getSkillIcon(), ItemIcon.INLINE)
			: ItemIcon.item(icons, s.getIconItemId(), ItemIcon.INLINE);
		left.add(icon);
		left.add(Box.createHorizontalStrut(5));
		left.add(Ui.label(s.getLabel(), Ui.sans(Font.PLAIN, 11.5f), Ui.MUTED));
		top.add(left, BorderLayout.CENTER);
		top.add(Ui.label(s.getValue(), Ui.sans(Font.BOLD, 10f), Ui.MUTED), BorderLayout.EAST);
		col.addRow(top);
		if (s.getFraction() >= 0)
		{
			Gauge gauge = new Gauge(s.getFraction(), s.isRunsShort() ? Ui.BAD : Ui.OK, 9);
			if (s.getTaskEndAt() >= 0)
			{
				gauge.marker(s.getTaskEndAt());
			}
			col.addRow(fixed(gauge));
		}
		return col;
	}

	private JComponent taskInfo(OnTaskModel m)
	{
		Ui.Column sec = Ui.section("Task info");
		JComponent dropIcon = m.getBestDropItemId() > 0
			? ItemIcon.item(icons, m.getBestDropItemId(), ItemIcon.INLINE) : null;
		String drop = m.getBestDrop();
		int rateAt = drop.lastIndexOf(" 1/");
		sec.addRow(kv("Best drop", dropIcon, Ui.label(rateAt < 0 ? drop : drop.substring(0, rateAt),
			Ui.body(), Ui.YELLOW)));
		if (rateAt >= 0)
		{
			JLabel rate = Ui.label("drop rate " + drop.substring(rateAt + 1), Ui.small(), Ui.FAINT);
			rate.setHorizontalAlignment(JLabel.RIGHT);
			sec.addRow(rate);
		}
		sec.addRow(Ui.kv("Superiors", m.getSuperiors(), Ui.TEXT));
		if (m.getSlayerPoints() != null)
		{
			sec.addRow(kv("Slayer points", null, points(m.getSlayerPoints())));
		}
		sec.addRow(Ui.kv("This task", m.getNextTask(), Ui.YELLOW));
		if (m.getLevelLabel() != null)
		{
			Ui.Column lvl = new Ui.Column(3);
			JPanel top = row(6);
			JPanel key = new JPanel();
			key.setLayout(new BoxLayout(key, BoxLayout.X_AXIS));
			key.setOpaque(false);
			key.add(ItemIcon.skill("slayer", ItemIcon.INLINE));
			key.add(Box.createHorizontalStrut(4));
			key.add(Ui.label(m.getLevelLabel(), Ui.sans(Font.PLAIN, 11.5f), Ui.FAINT));
			top.add(key, BorderLayout.CENTER);
			top.add(Ui.label(m.getLevelValue(), Ui.body(), Ui.TEXT), BorderLayout.EAST);
			lvl.addRow(top);
			if (m.getLevelFraction() >= 0)
			{
				lvl.addRow(fixed(new Gauge(m.getLevelFraction(), Ui.BLUE, 8)));
			}
			sec.addRow(spaced(lvl, 2));
		}
		return sec;
	}

	/**
	 * A key / value row that never asks for more width than it gets: the value (EAST) keeps its size and the
	 * key (CENTER) is cut short with "..." if both don't fit, so one long line can't squeeze the whole tab.
	 */
	private static JPanel row(int gap)
	{
		JPanel p = new JPanel(new BorderLayout(gap, 0))
		{
			@Override
			public Dimension getPreferredSize()
			{
				return new Dimension(0, super.getPreferredSize().height);
			}

			@Override
			public Dimension getMinimumSize()
			{
				return getPreferredSize();
			}
		};
		p.setOpaque(false);
		return p;
	}

	/** Gauges have no minimum size of their own; keep their height when the column is squeezed. */
	private static Gauge fixed(Gauge g)
	{
		g.setMinimumSize(new Dimension(10, g.getPreferredSize().height));
		return g;
	}

	/** Key on the left; on the right an optional icon then the value component. */
	private static JPanel kv(String key, Component icon, Component value)
	{
		JPanel p = row(6);
		p.add(Ui.label(key, Ui.body(), Ui.FAINT), BorderLayout.CENTER);
		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
		right.setOpaque(false);
		if (icon != null)
		{
			right.add(icon);
			right.add(Box.createHorizontalStrut(4));
		}
		right.add(value);
		p.add(right, BorderLayout.EAST);
		return p;
	}

	/** "412 → 562": the after-value in green, the arrow faint; plain text when there is no arrow. */
	private static JComponent points(String text)
	{
		int i = text.indexOf('→');
		if (i < 0)
		{
			return Ui.label(text, Ui.body(), Ui.TEXT);
		}
		JLabel l = Ui.label("<html>" + Ui.esc(text.substring(0, i).trim())
			+ " <font color='" + Ui.hex(Ui.FAINT) + "'>→</font> <b><font color='" + Ui.hex(Ui.OK) + "'>"
			+ Ui.esc(text.substring(i + 1).trim()) + "</font></b></html>", Ui.body(), Ui.TEXT);
		return l;
	}
}
