package com.slayercompanion.ui;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

/** Inner stone box (#2e271e, 1px dark edge) stacking rows like {@link Ui.Column}; "best" gets the orange frame. */
class Card extends Ui.Column
{
	private final int pad;

	Card(int gap, int pad)
	{
		super(gap);
		this.pad = pad;
		setOpaque(true);
		setBackground(Ui.CARD);
		setBest(false);
	}

	/** Gap 4, padding 8. */
	Card()
	{
		this(4, 8);
	}

	/** Orange frame with a brown inner line, for the recommended pick. */
	Card setBest(boolean best)
	{
		Border edge = best
			? BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.ORANGE),
				BorderFactory.createLineBorder(Ui.BEST_INNER))
			: BorderFactory.createLineBorder(Ui.EDGE_DARK);
		int p = best ? pad - 1 : pad;
		setBorder(BorderFactory.createCompoundBorder(edge, BorderFactory.createEmptyBorder(p, p, p, p)));
		return this;
	}

	/** Light-edged variant (the upgrade box: 1px #6b5a42). */
	Card setRaised()
	{
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.BUTTON_TOP),
			BorderFactory.createEmptyBorder(pad, pad, pad, pad)));
		return this;
	}
}
