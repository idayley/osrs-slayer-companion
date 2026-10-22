package com.slayercompanion.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Boxed stat (.st): small faint caption over a large bold value, with an optional small unit ("32s"). */
class StatTile extends JPanel
{
	StatTile(String label, String value)
	{
		this(label, value, "", Ui.TEXT);
	}

	StatTile(String label, String value, String unit, java.awt.Color valueColor)
	{
		super(new BorderLayout(0, 1));
		setBackground(Ui.CARD);
		setOpaque(true);
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.EDGE_DARK),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		add(Ui.label(label, Ui.small(), Ui.FAINT), BorderLayout.NORTH);
		String html = unit == null || unit.isEmpty() ? Ui.esc(value)
			: "<html>" + Ui.esc(value) + "<span style='font-size:11pt'>" + Ui.esc(unit) + "</span></html>";
		JLabel v = Ui.label(html, Ui.sans(Font.BOLD, 16f), valueColor);
		add(v, BorderLayout.CENTER);
	}
}
