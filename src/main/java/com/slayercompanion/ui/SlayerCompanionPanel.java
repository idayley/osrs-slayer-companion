package com.slayercompanion.ui;

import com.slayercompanion.gear.Style;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/** The side panel: a Prep / Where to go / On task tab bar over scrollable tab content. Swing thread only. */
public class SlayerCompanionPanel extends PluginPanel
{
	static final String ISSUES_URL = "https://github.com/idayley/osrs-slayer-companion/issues/new/choose";

	public interface Actions
	{
		void selectStyle(Style style);

		void selectLocation(String locationId);

		void toggleBankHighlight();

		void takeMeThere();

		void sendSafespots();
	}

	public enum Tab
	{
		PREP("Prep"), WHERE("Where to go"), ON_TASK("On task");

		private final String title;

		Tab(String title)
		{
			this.title = title;
		}
	}

	private final PrepTab prep;
	private final WhereToGoTab where;
	private final OnTaskTab onTask;
	private final JComponent[] tabPages = new JComponent[Tab.values().length];
	private final JLabel[] tabLabels = new JLabel[Tab.values().length];
	private final ScrollContent content = new ScrollContent();
	private final JScrollPane scroll;
	private final JLabel report = reportLink();

	private PrepModel lastPrep;
	private WhereToGoModel lastWhere;
	private OnTaskModel lastOnTask;

	/** @param icons item pictures for the tabs (ItemManager-backed in the client). */
	public SlayerCompanionPanel(Actions actions, IconSource icons)
	{
		super(false);
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder());
		setBackground(Ui.BG);

		prep = new PrepTab(icons, actions::selectStyle, actions::toggleBankHighlight);
		where = new WhereToGoTab(icons, actions::selectLocation, actions::takeMeThere, actions::sendSafespots);
		onTask = new OnTaskTab(icons);

		add(tabBar(), BorderLayout.NORTH);

		tabPages[Tab.PREP.ordinal()] = prep;
		tabPages[Tab.WHERE.ordinal()] = where;
		tabPages[Tab.ON_TASK.ordinal()] = onTask;

		scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(Ui.BG);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		PrepModel p = PrepModel.noTask();
		WhereToGoModel w = WhereToGoModel.noTask();
		OnTaskModel o = OnTaskModel.noTask();
		update(p, w, o);
		select(Tab.PREP);
	}

	private JPanel tabBar()
	{
		JPanel bar = new JPanel(new GridLayout(1, Tab.values().length));
		bar.setBackground(Ui.TAB_BG);
		bar.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Ui.EDGE_DARK));
		for (Tab t : Tab.values())
		{
			ShadowLabel l = new ShadowLabel(t.title);
			l.setHorizontalAlignment(SwingConstants.CENTER);
			l.setFont(Ui.pixelFont());
			Ui.aa(l);
			l.setOpaque(true);
			l.setBackground(Ui.TAB_BG);
			Ui.onClick(l, () -> select(t));
			tabLabels[t.ordinal()] = l;
			bar.add(l);
		}
		return bar;
	}

	/** Faint "wrong info?" line under every tab that opens the GitHub issue forms. */
	private static JLabel reportLink()
	{
		JLabel l = Ui.note("");
		l.setText("<html><center>Something wrong, or a safespot to share?<br><u>Report it on GitHub</u></center></html>");
		Ui.onClick(l, () -> LinkBrowser.browse(ISSUES_URL));
		return l;
	}

	public void select(Tab tab)
	{
		// Only the shown tab is in the scroll content, so the scroll range fits it.
		content.removeAll();
		content.add(tabPages[tab.ordinal()], BorderLayout.NORTH);
		content.add(report, BorderLayout.SOUTH);
		for (Tab t : Tab.values())
		{
			boolean on = t == tab;
			JLabel l = tabLabels[t.ordinal()];
			l.setForeground(on ? Ui.YELLOW : Ui.FAINT);
			l.setBackground(on ? Ui.TAB_ACTIVE : Ui.TAB_BG);
			boolean last = t.ordinal() == Tab.values().length - 1;
			l.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 0, last ? 0 : 1, Ui.EDGE_DARK),
				BorderFactory.createEmptyBorder(7, 0, 6, 0)));
		}
		content.revalidate();
		content.repaint();
		scroll.getVerticalScrollBar().setValue(0);
	}

	/** Shows new models; tabs whose model did not change are left alone. */
	public void update(PrepModel p, WhereToGoModel w, OnTaskModel o)
	{
		if (!Objects.equals(p, lastPrep))
		{
			lastPrep = p;
			prep.show(p);
		}
		if (!Objects.equals(w, lastWhere))
		{
			lastWhere = w;
			where.show(w);
		}
		if (!Objects.equals(o, lastOnTask))
		{
			lastOnTask = o;
			onTask.show(o);
		}
	}

	public void showWhereMessage(String message)
	{
		where.showActionMessage(message);
	}

	/** The tab content without the scroll pane, for headless rendering. */
	JComponent tabComponent(Tab tab)
	{
		switch (tab)
		{
			case PREP:
				return prep;
			case WHERE:
				return where;
			default:
				return onTask;
		}
	}

	JComponent reportComponent()
	{
		return report;
	}

	JComponent tabBarComponent()
	{
		return (JComponent) getComponent(0);
	}

	/** Scroll content that always matches the viewport width, so only vertical scrolling happens. */
	private static class ScrollContent extends JPanel implements Scrollable
	{
		ScrollContent()
		{
			super(new BorderLayout());
			setBackground(Ui.BG);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
