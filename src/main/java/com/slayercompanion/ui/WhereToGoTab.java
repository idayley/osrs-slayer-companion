package com.slayercompanion.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Where to go tab: ranked locations, Take me there, and sending safespots in. */
class WhereToGoTab extends Ui.Column
{
	private final Consumer<String> onSelect;
	private final Runnable onTakeMeThere;
	private final Runnable onSendSafespots;
	private WhereToGoModel model;
	private String actionMessage;

	WhereToGoTab(Consumer<String> onSelect, Runnable onTakeMeThere, Runnable onSendSafespots)
	{
		super(0);
		this.onSelect = onSelect;
		this.onTakeMeThere = onTakeMeThere;
		this.onSendSafespots = onSendSafespots;
	}

	void show(WhereToGoModel m)
	{
		model = m;
		refresh();
	}

	/** Result line under the buttons ("Shortest Path isn't installed", "Copied 3 safespots"); null clears it. */
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
		for (WhereToGoModel.Row r : m.getRows())
		{
			addRow(row(r));
		}

		Ui.Column foot = new Ui.Column(6);
		foot.setBorder(BorderFactory.createEmptyBorder(Ui.PAD, Ui.PAD, 0, Ui.PAD));
		foot.addRow(Ui.primary("Take me there", onTakeMeThere));
		foot.addRow(Ui.ghost("Send my safespots in", onSendSafespots));
		if (actionMessage != null)
		{
			JLabel msg = Ui.wrapped(actionMessage, Ui.body(), Ui.WARN_TEXT, Ui.INNER_WIDTH);
			foot.addRow(msg);
		}
		addRow(foot);
		addRow(Ui.note(m.getNote()));
	}

	private JPanel row(WhereToGoModel.Row r)
	{
		Ui.Column p = new Ui.Column(4);
		p.setOpaque(true);
		if (r.isSelected())
		{
			p.setBackground(Ui.SELECTED_BG);
			p.setBorder(Ui.leftAccent());
		}
		else
		{
			p.setBackground(Ui.BG);
			p.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.LINE),
				BorderFactory.createEmptyBorder(Ui.PAD, Ui.PAD, Ui.PAD, Ui.PAD)));
		}
		boolean locked = r.isLocked();
		Color nameColor = locked ? Ui.FAINT : Ui.TEXT;

		if (r.isRecommended())
		{
			p.addRow(Ui.label("RECOMMENDED", Ui.small(), Ui.ORANGE));
		}
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		JLabel travel = Ui.label(r.getTravel(), Ui.small(), locked ? Ui.FAINT : Ui.MUTED);
		int nameWidth = Ui.INNER_WIDTH - 3 - travel.getPreferredSize().width - 8;
		top.add(Ui.wrapped(r.getName(), Ui.bold(), nameColor, nameWidth), BorderLayout.CENTER);
		top.add(travel, BorderLayout.EAST);
		p.addRow(top);

		JPanel tags = Ui.flow();
		for (WhereToGoModel.Tag t : r.getTags())
		{
			tags.add(Ui.chip(Ui.esc(t.getText()), tagColor(t.getKind()), Ui.ROW2));
		}
		p.addRow(tags);

		if (!locked)
		{
			p.addRow(bars(r));
		}
		if (r.getWhy() != null)
		{
			p.addRow(Ui.wrapped(r.getWhy(), Ui.small(), Ui.MUTED, Ui.INNER_WIDTH));
		}
		Ui.onClick(p, () -> onSelect.accept(r.getLocationId()));
		return p;
	}

	private static Color tagColor(WhereToGoModel.TagKind kind)
	{
		switch (kind)
		{
			case SAFE:
				return Ui.GREEN;
			case BLUE:
				return Ui.BLUE;
			case RISK:
				return Ui.RISK;
			case LOCK:
			case NO_SAFE:
				return Ui.FAINT;
			default:
				return Ui.MUTED;
		}
	}

	private static JPanel bars(WhereToGoModel.Row r)
	{
		JPanel g = new JPanel(new GridBagLayout());
		g.setOpaque(false);
		addBar(g, 0, "XP", r.getXpFraction(), Ui.BLUE, r.getXpText());
		addBar(g, 1, "GP", r.getGpFraction(), Ui.GOLD, r.getGpText());
		return g;
	}

	private static void addBar(JPanel g, int y, String label, double fraction, Color color, String value)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = y;
		c.insets = new Insets(y == 0 ? 0 : 3, 0, 0, 6);
		c.anchor = GridBagConstraints.WEST;
		c.gridx = 0;
		JLabel l = Ui.label(label, Ui.small(), Ui.FAINT);
		l.setPreferredSize(new java.awt.Dimension(18, l.getPreferredSize().height));
		g.add(l, c);
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		g.add(new Ui.Bar(fraction, color, 6), c);
		c.gridx = 2;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(y == 0 ? 0 : 3, 0, 0, 0);
		JLabel v = Ui.label(value, Ui.small(), Ui.MUTED);
		v.setHorizontalAlignment(JLabel.RIGHT);
		v.setPreferredSize(new java.awt.Dimension(52, v.getPreferredSize().height));
		g.add(v, c);
	}
}
