package com.slayercompanion.ui;

import com.slayercompanion.data.GameData;
import com.slayercompanion.data.Monster;
import com.slayercompanion.gear.CombatStats;
import com.slayercompanion.gear.GearOptimizer;
import com.slayercompanion.gear.Loadout;
import com.slayercompanion.gear.OwnedItems;
import com.slayercompanion.gear.Style;
import com.slayercompanion.session.TaskSession;
import com.slayercompanion.task.CurrentTask;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/**
 * Renders each tab with sample data (Fire giants, Waterfall Dungeon) to PNGs for layout checks.
 * Not a test: run with {@code ./gradlew panelShots} (headless). Output: build/panel-shots/{prep,where,ontask}.png
 * plus the no-task variants.
 */
public final class PanelShots
{
	private PanelShots()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File dir = new File(args.length > 0 ? args[0] : "build/panel-shots");
		if (!dir.isDirectory() && !dir.mkdirs())
		{
			throw new IllegalStateException("Can't create " + dir);
		}
		timeOptimizer();
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				render(dir);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		System.out.println("Wrote panel shots to " + dir.getAbsolutePath());
	}

	private static void render(File dir) throws Exception
	{
		SlayerCompanionPanel panel = new SlayerCompanionPanel(new SlayerCompanionPanel.Actions()
		{
			@Override
			public void selectStyle(Style style)
			{
			}

			@Override
			public void selectLocation(String locationId)
			{
			}

			@Override
			public void toggleBankHighlight()
			{
			}

			@Override
			public void takeMeThere()
			{
			}

			@Override
			public void sendSafespots()
			{
			}
		});

		Instant now = Instant.parse("2026-01-01T12:00:00Z");
		OwnedItems owned = new OwnedItems();
		owned.updateBank(java.util.Collections.singletonMap(Samples.SHARK, 214), now.minus(Duration.ofMinutes(12)));
		CurrentTask task = Samples.task(Samples.fireGiants(), 87, 142);
		GearOptimizer.Upgrade upgrade = new GearOptimizer.Upgrade(Samples.item(11832, "Bandos chestplate", "body"), 4.2);
		PrepModel prep = PrepModel.build(task, Samples.fireGiant(), owned, Style.MELEE,
			Optional.of(Samples.meleeLoadout()), Optional.of(upgrade), false, now);
		WhereToGoModel where = WhereToGoModel.build(Samples.fireGiantRanking(), null);

		Samples.MutableClock clock = new Samples.MutableClock();
		TaskSession session = new TaskSession(clock);
		session.setMonster(Samples.fireGiant());
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 142, 142));
		session.onFoodCount(20);
		session.onTaskUpdate(Samples.task(Samples.fireGiants(), 141, 142));
		for (int remaining = 140; remaining >= 87; remaining--)
		{
			clock.advance(32);
			session.onTaskUpdate(Samples.task(Samples.fireGiants(), remaining, 142));
		}
		session.onFoodCount(6);
		session.onLoot(1333, 1, 15_000);
		for (int i = 0; i < 40; i++)
		{
			session.onLoot(995, 2200, 1);
		}
		session.onSuperior();
		OnTaskModel onTask = OnTaskModel.build("Fire giants", "Waterfall Dungeon", session, Samples.fireGiant(), 6,
			48, 70, 1_000_000, 49);

		panel.update(prep, where, onTask);
		panel.showWhereMessage(WhereToGoModel.takeMeThereMessage(false));
		shoot(panel, SlayerCompanionPanel.Tab.PREP, new File(dir, "prep.png"));
		shoot(panel, SlayerCompanionPanel.Tab.WHERE, new File(dir, "where.png"));
		shoot(panel, SlayerCompanionPanel.Tab.ON_TASK, new File(dir, "ontask.png"));

		PrepModel noBank = PrepModel.build(Samples.task(Samples.needs("Aberrant spectres", "nose peg or Slayer helmet",
			4168), 120, 120), null, new OwnedItems(), Style.RANGED, Optional.empty(), Optional.empty(), false, now);
		panel.update(noBank, WhereToGoModel.noTask(), OnTaskModel.noTask());
		shoot(panel, SlayerCompanionPanel.Tab.PREP, new File(dir, "prep-nobank.png"));
		shoot(panel, SlayerCompanionPanel.Tab.ON_TASK, new File(dir, "notask.png"));
	}

	private static void shoot(SlayerCompanionPanel panel, SlayerCompanionPanel.Tab tab, File out) throws Exception
	{
		panel.select(tab);
		int width = PluginPanel.PANEL_WIDTH;
		JComponent content = panel.tabComponent(tab);
		panel.setSize(width, 3000);
		layout(panel);
		int height = panel.tabBarComponent().getPreferredSize().height + content.getPreferredSize().height;
		panel.setSize(width, height);
		layout(panel);
		layout(panel);
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		panel.paint(g);
		g.dispose();
		ImageIO.write(img, "png", out);
	}

	private static void layout(Component c)
	{
		c.doLayout();
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				layout(child);
			}
		}
	}

	/** Prints how long a full gear search takes on the bundled data, since the plugin runs it on the client thread. */
	private static void timeOptimizer()
	{
		GameData data = GameData.load("/com/slayercompanion");
		GearOptimizer optimizer = new GearOptimizer(data.allEquipment());
		Monster m = data.monster(2075).orElseThrow(IllegalStateException::new);
		Map<Integer, Integer> bank = new HashMap<>();
		int[] ids = {4151, 12954, 11864, 10551, 21304, 6585, 1127, 1079, 3105, 7462, 2503, 2497, 861, 892, 1381, 4675,
			6889, 4089, 4091, 4093, 11283, 2550, 1704, 9185, 9144, 11802, 4587, 1333, 1215, 5698};
		for (int id : ids)
		{
			bank.put(id, 1);
		}
		OwnedItems owned = new OwnedItems();
		owned.updateBank(bank, Instant.now());
		CombatStats stats = new CombatStats(80, 85, 75, 80, 80, 85, 70, 80);
		for (Style style : Style.values())
		{
			long t0 = System.nanoTime();
			Optional<Loadout> best = optimizer.best(stats, style, m, true, owned);
			long t1 = System.nanoTime();
			best.flatMap(b -> optimizer.biggestUpgrade(stats, java.util.Collections.emptySet(), b, m, true, owned));
			long t2 = System.nanoTime();
			System.out.printf("%s: best %.1f ms, biggestUpgrade %.1f ms (%d catalogue items)%n", style,
				(t1 - t0) / 1e6, (t2 - t1) / 1e6, data.allEquipment().size());
		}
	}
}
