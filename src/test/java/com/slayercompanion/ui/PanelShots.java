package com.slayercompanion.ui;

import com.google.gson.Gson;
import com.slayercompanion.data.GameData;
import com.slayercompanion.data.Monster;
import com.slayercompanion.gear.CombatStats;
import com.slayercompanion.gear.GearOptimizer;
import com.slayercompanion.gear.Loadout;
import com.slayercompanion.gear.OwnedItems;
import com.slayercompanion.gear.Style;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.session.SupplyTracker;
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
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.ItemID;
import net.runelite.client.ui.PluginPanel;

/**
 * Renders each tab with sample data (Fire giants, Waterfall Dungeon) to PNGs for layout checks.
 * Not a test: run with {@code ./gradlew panelShots} (headless). Output: build/panel-shots/{prep,where,ontask}.png,
 * ontask-supplies.png (Mutated zygomites: food, prayer, fungicide, cannonballs) plus the no-task variants,
 * and components.png (the shared stone parts).
 */
public final class PanelShots
{
	private static final Gson GSON = new Gson();

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
		}, new TestIcons());

		Instant now = Instant.parse("2026-01-01T12:00:00Z");
		OwnedItems owned = new OwnedItems();
		owned.updateBank(java.util.Collections.singletonMap(Samples.SHARK, 214), now.minus(Duration.ofMinutes(12)));
		owned.updateWorn(java.util.Collections.singletonMap(Samples.WHIP, 1));
		CurrentTask task = Samples.task(Samples.fireGiants(), 87, 142);
		com.slayercompanion.data.Equipment bandos = Samples.item(11832, "Bandos chestplate", "body");
		bandos.getSkillReqs().put("defence", 65);
		bandos.getQuestReqs().add("DRAGON_SLAYER_I");
		GearOptimizer.Upgrade upgrade = new GearOptimizer.Upgrade(bandos, 4.2);
		PrepModel.UpgradeFacts facts = new PrepModel.UpgradeFacts(14_632_000, skill -> 70, q -> false,
			q -> "Dragon Slayer I");
		PrepModel prep = PrepModel.build(task, Samples.fireGiant(), true, owned, Style.MELEE,
			Optional.of(Samples.meleeLoadout()), Optional.of(upgrade), facts, false, now, "Assumes Piety and a super combat");
		WhereToGoModel where = WhereToGoModel.build(Samples.fireGiantRanking(), null, "Fire giants", Style.MELEE);

		OnTaskModel onTask = onTaskSample(Samples.fireGiants(), "Waterfall Dungeon", 142, 87, 14, 0);
		SlayerTask zygomites = new SlayerTask();
		zygomites.setId("mutated-zygomites");
		zygomites.setName("Mutated zygomites");
		OnTaskModel onTaskZygomites = onTaskSample(zygomites, "Zanaris", 120, 40, 30, 9);

		panel.update(prep, where, onTask);
		panel.showWhereMessage(WhereToGoModel.takeMeThereMessage(false));
		shoot(panel, SlayerCompanionPanel.Tab.PREP, new File(dir, "prep.png"));
		shoot(panel, SlayerCompanionPanel.Tab.WHERE, new File(dir, "where.png"));
		shoot(panel, SlayerCompanionPanel.Tab.ON_TASK, new File(dir, "ontask.png"));
		panel.update(prep, where, onTaskZygomites);
		shoot(panel, SlayerCompanionPanel.Tab.ON_TASK, new File(dir, "ontask-supplies.png"));
		components(new File(dir, "components.png"));

		PrepModel shown = PrepModel.build(task, Samples.fireGiant(), true, owned, Style.MELEE,
			Optional.of(Samples.meleeLoadout()), Optional.of(upgrade), true, now);
		panel.update(shown, where, onTask);
		shoot(panel, SlayerCompanionPanel.Tab.PREP, new File(dir, "prep-setup-shown.png"));

		com.slayercompanion.data.Equipment fireCape = Samples.item(6570, "Fire cape", "cape");
		fireCape.setSource("TzHaar Fight Cave reward");
		PrepModel untradeable = PrepModel.build(task, Samples.fireGiant(), true, owned, Style.MELEE,
			Optional.of(Samples.meleeLoadout()), Optional.of(new GearOptimizer.Upgrade(fireCape, 2.1)),
			new PrepModel.UpgradeFacts(0, skill -> 70, q -> false, q -> q), false, now);
		panel.update(untradeable, where, onTask);
		shoot(panel, SlayerCompanionPanel.Tab.PREP, new File(dir, "prep-untradeable.png"));

		PrepModel noBank = PrepModel.build(Samples.task(Samples.needs("Aberrant spectres", "nose peg or Slayer helmet",
			4168), 120, 120), null, false, new OwnedItems(), Style.RANGED, Optional.empty(), Optional.empty(), false, now);
		panel.update(noBank, WhereToGoModel.noTask(), OnTaskModel.noTask());
		shoot(panel, SlayerCompanionPanel.Tab.PREP, new File(dir, "prep-nobank.png"));
		shoot(panel, SlayerCompanionPanel.Tab.ON_TASK, new File(dir, "notask.png"));
	}

	/**
	 * Plays out a task from {@code total} down to {@code remaining} kills, 32 s each, with a cannon firing 3 balls
	 * a kill, a shark every 4 kills, 2 prayer points drained a kill (a dose sipped below 20) and, when
	 * {@code refills} > 0, a fungicide charge a kill from a spray(10) plus that many refills.
	 */
	private static OnTaskModel onTaskSample(SlayerTask task, String location, int total, int remaining, int sharks,
		int refills)
	{
		Map<Integer, Long> prices = Map.of(ItemID.SHARK, 900L, ItemID.PRAYER_POTION4, 9000L,
			ItemID.STEEL_CANNONBALL, 200L, ItemID.FUNGICIDE, 300L);
		Map<Integer, String> names = Map.of(ItemID.PRAYER_POTION4, "Prayer potion(4)",
			ItemID.PRAYER_POTION3, "Prayer potion(3)", ItemID.PRAYER_POTION2, "Prayer potion(2)",
			ItemID.PRAYER_POTION1, "Prayer potion(1)");
		int[] potions = {0, ItemID.PRAYER_POTION1, ItemID.PRAYER_POTION2, ItemID.PRAYER_POTION3, ItemID.PRAYER_POTION4};
		int[] sprays = {ItemID.FUNGICIDE_SPRAY_0, ItemID.FUNGICIDE_SPRAY_1, ItemID.FUNGICIDE_SPRAY_2,
			ItemID.FUNGICIDE_SPRAY_3, ItemID.FUNGICIDE_SPRAY_4, ItemID.FUNGICIDE_SPRAY_5, ItemID.FUNGICIDE_SPRAY_6,
			ItemID.FUNGICIDE_SPRAY_7, ItemID.FUNGICIDE_SPRAY_8, ItemID.FUNGICIDE_SPRAY_9, ItemID.FUNGICIDE_SPRAY_10};

		Samples.MutableClock clock = new Samples.MutableClock();
		TaskSession session = new TaskSession(clock,
			new SupplyTracker(id -> prices.getOrDefault(id, 0L), names::get));
		session.setMonster(Samples.fireGiant());
		int points = 70;
		int doses = 12;
		int steel = 500;
		int ammo = 0;
		int charges = 10;
		int food = sharks + (total - remaining) / 4;
		session.onPrayerPoints(points);
		session.onTaskUpdate(Samples.task(task, total, total));
		for (int left = total; left >= remaining; left--)
		{
			if (left < total)
			{
				clock.advance(32);
				session.onTaskUpdate(Samples.task(task, left, total));
				session.onLoot(995, "Coins", 2200, 1);
				points -= 2;
				ammo -= 3;
				charges--;
				if ((total - left) % 4 == 0)
				{
					food--;
				}
			}
			if (points < 20 && doses > 0)
			{
				doses--;
				points += 7 + 70 / 4;
			}
			if (ammo < 3)
			{
				steel -= 30 - ammo;
				ammo = 30;
			}
			if (charges == 0 && refills > 0)
			{
				refills--;
				charges = 10;
			}
			session.onPrayerPoints(points);
			Map<Integer, Integer> carried = new HashMap<>();
			carried.put(ItemID.SHARK, food);
			carried.put(ItemID.STEEL_CANNONBALL, steel);
			carried.put(ItemID.PRAYER_POTION4, doses / 4);
			if (doses % 4 > 0)
			{
				carried.put(potions[doses % 4], 1);
			}
			if (task.getName().startsWith("Mutated"))
			{
				carried.put(sprays[charges], 1);
				carried.put(ItemID.FUNGICIDE, refills);
			}
			Map<Integer, Integer> looted = new HashMap<>(carried);
			looted.put(995, 2200 * (total - left)); // every kill's coins picked up
			session.onSupplies(carried, looted);
			session.onCannon(true, ammo);
		}
		session.onLoot(1333, "Rune scimitar", 1, 15_000);
		Map<Integer, Integer> carried = new HashMap<>();
		carried.put(ItemID.SHARK, food);
		Map<Integer, Integer> looted = new HashMap<>(carried);
		looted.put(995, 2200 * (total - remaining));
		looted.put(1333, 1);
		session.onSupplies(carried, looted);
		session.onSuperior();
		return OnTaskModel.build(task.getName(), location, session, Samples.fireGiant(), food, points, 70, 1_000_000,
			49, 412, "chaeldar", 49);
	}

	private static void shoot(SlayerCompanionPanel panel, SlayerCompanionPanel.Tab tab, File out) throws Exception
	{
		panel.select(tab);
		int width = PluginPanel.PANEL_WIDTH;
		JComponent content = panel.tabComponent(tab);
		panel.setSize(width, 3000);
		layout(panel);
		int height = panel.tabBarComponent().getPreferredSize().height + content.getPreferredSize().height
			+ panel.reportComponent().getPreferredSize().height;
		panel.setSize(width, height);
		layout(panel);
		layout(panel);
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		panel.paint(g);
		g.dispose();
		ImageIO.write(img, "png", out);
	}

	/** One sheet showing each shared stone component, so tab layouts can be checked against it. */
	private static void components(File out) throws Exception
	{
		IconSource icons = new TestIcons();
		Ui.Column root = new Ui.Column(0);
		root.setOpaque(true);
		root.setBackground(Ui.PANEL);

		Ui.Column hero = Ui.hero();
		JPanel heroRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
		heroRow.setOpaque(false);
		heroRow.add(new ProgressRing(0.39, "87", "left"));
		heroRow.add(Ui.title("Fire giants", Ui.ORANGE, 120));
		hero.addRow(heroRow);
		root.addRow(hero);

		Ui.Column sec = Ui.section("Best owned gear");
		JPanel slots = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		slots.setOpaque(false);
		slots.add(new IconSlot(ItemIcon.item(icons, 4151, ItemIcon.SLOT)).setWorn(true));
		slots.add(new IconSlot(ItemIcon.item(icons, 11864, ItemIcon.SLOT)));
		slots.add(new IconSlot(ItemIcon.item(icons, 2446, ItemIcon.SLOT).setDimmed(true)));
		slots.add(ItemIcon.skill("slayer", ItemIcon.ROW));
		slots.add(ItemIcon.skill("prayer", ItemIcon.INLINE));
		slots.add(ItemIcon.item(icons, 995, ItemIcon.INLINE));
		sec.addRow(slots);
		JPanel tags = Ui.flow();
		tags.add(new TagPill("Safespot", TagPill.Kind.OK));
		tags.add(new TagPill("Multi", TagPill.Kind.BLUE));
		tags.add(new TagPill("Bonus drops", TagPill.Kind.GOLD));
		tags.add(new TagPill("Cannon", TagPill.Kind.NEUTRAL));
		sec.addRow(tags);
		JPanel stats = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
		stats.setOpaque(false);
		stats.add(new StatTile("Per kill", "32", "s", Ui.TEXT));
		stats.add(new StatTile("Loot so far", "191k", "", Ui.YELLOW));
		sec.addRow(stats);
		sec.addRow(new Gauge(0.79, Ui.BLUE, 8));
		sec.addRow(new Gauge(1.0, Ui.OK, 9).marker(87 / 120.0));
		sec.addRow(new WarnBox("Prayer runs out ~23 kills early", Ui.INNER_WIDTH));
		Card best = new Card().setBest(true);
		best.addRow(Ui.label("Waterfall Dungeon", Ui.bold(), Ui.TEXT));
		best.addRow(Ui.label("Safespot \u00b7 Closest", Ui.small(), Ui.ORANGE));
		best.addRow(Ui.primary("\u279c Take me there", () -> { }).small());
		sec.addRow(best);
		Card plain = new Card();
		plain.addRow(Ui.kv("Cost", "14.6m", Ui.YELLOW));
		plain.addRow(Ui.kv("Needs", "65 Defence", Ui.OK));
		sec.addRow(plain);
		sec.addRow(Ui.primary("Show setup in bank", () -> { }));
		sec.addRow(Ui.ghost("Take me there", () -> { }));
		root.addRow(sec);
		root.addRow(Ui.note("Bank last read 12 min ago"));

		int width = PluginPanel.PANEL_WIDTH;
		root.setSize(width, 2000);
		layout(root);
		int height = root.getPreferredSize().height;
		root.setSize(width, height);
		layout(root);
		layout(root);
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		root.paint(g);
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
		GameData data = GameData.load(GSON, "/com/slayercompanion");
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
