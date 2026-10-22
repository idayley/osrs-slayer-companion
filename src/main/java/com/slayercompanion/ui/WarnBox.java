package com.slayercompanion.ui;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** Dark red warning strip (.warn) with peach text, wrapped to the given width. */
class WarnBox extends JPanel
{
	WarnBox(String text, int width)
	{
		super(new BorderLayout());
		setBackground(Ui.WARN_BG);
		setOpaque(true);
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.EDGE_DARK),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		add(Ui.wrapped("⚠ " + text, Ui.small().deriveFont(11f), Ui.WARN_TEXT, width - 14), BorderLayout.CENTER);
	}
}
