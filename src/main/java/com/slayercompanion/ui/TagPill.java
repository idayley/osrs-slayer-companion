package com.slayercompanion.ui;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JLabel;

/** Small tag with a 1px coloured border and matching text (.tag). */
class TagPill extends JLabel
{
	enum Kind
	{
		/** Green: safespot, met requirement. */
		OK(Ui.OK, new Color(0x2f6b3f)),
		/** Blue: multi-combat, magic. */
		BLUE(Ui.BLUE, new Color(0x2f5a80)),
		/** Yellow: bonus drops, money. */
		GOLD(Ui.YELLOW, new Color(0x6b5a1c)),
		/** Red: risk. */
		BAD(Ui.BAD, new Color(0x7a2a20)),
		NEUTRAL(Ui.MUTED, Ui.EDGE_LIGHT);

		final Color text;
		final Color border;

		Kind(Color text, Color border)
		{
			this.text = text;
			this.border = border;
		}
	}

	TagPill(String text, Kind kind)
	{
		super(text);
		setFont(Ui.small());
		setForeground(kind.text);
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(kind.border),
			BorderFactory.createEmptyBorder(1, 5, 1, 5)));
		Ui.aa(this);
	}
}
