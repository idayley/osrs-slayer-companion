package com.slayercompanion;

import com.google.inject.Provides;
import com.slayercompanion.data.Equipment;
import com.slayercompanion.data.GameData;
import com.slayercompanion.data.Location;
import com.slayercompanion.data.Monster;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.gear.CombatStats;
import com.slayercompanion.gear.DpsCalculator;
import com.slayercompanion.gear.GearOptimizer;
import com.slayercompanion.gear.Loadout;
import com.slayercompanion.gear.OwnedItems;
import com.slayercompanion.gear.Style;
import com.slayercompanion.integration.ShortestPath;
import com.slayercompanion.locations.Estimates;
import com.slayercompanion.locations.LocationRanker;
import com.slayercompanion.locations.RankedLocation;
import com.slayercompanion.locations.Requirements;
import com.slayercompanion.safespot.SafespotExport;
import com.slayercompanion.safespot.SafespotMenu;
import com.slayercompanion.safespot.SafespotOverlay;
import com.slayercompanion.safespot.SafespotStore;
import com.slayercompanion.session.FoodItems;
import com.slayercompanion.session.TaskSession;
import com.slayercompanion.task.CurrentTask;
import com.slayercompanion.task.TaskTracker;
import com.slayercompanion.ui.BankHighlightOverlay;
import com.slayercompanion.ui.OnTaskModel;
import com.slayercompanion.ui.PrepModel;
import com.slayercompanion.ui.SlayerCompanionPanel;
import com.slayercompanion.ui.WhereToGoModel;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * Wires game events to the task, gear, location, safespot and session logic and feeds the side panel.
 * All plugin state is owned by the client thread; the panel only receives immutable view models.
 */
@Slf4j
@PluginDescriptor(
	name = "Slayer Companion",
	description = "Prep, where to go and live stats for your slayer task",
	tags = {"slayer", "task", "gear", "safespot", "loot"}
)
public class SlayerCompanionPlugin extends Plugin
{
	private static final String SUPERIOR_MESSAGE = "A superior foe has appeared";
	/** Rebuild Prep / Where to go about once a minute even without changes, so "{n} min ago" stays current. */
	private static final int PLAN_REFRESH_TICKS = 100;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ConfigManager configManager;
	@Inject
	private ItemManager itemManager;
	@Inject
	private EventBus eventBus;
	@Inject
	private PluginManager pluginManager;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ChatboxPanelManager chatboxPanelManager;
	@Inject
	private BankHighlightOverlay bankHighlightOverlay;

	private GameData data;
	private GearOptimizer optimizer;
	private Set<String> equipmentQuests;
	private TaskTracker tracker;
	private TaskSession session;
	private OwnedItems owned;
	private SafespotStore safespots;
	private SafespotMenu safespotMenu;
	private SafespotOverlay safespotOverlay;
	private ShortestPath shortestPath;
	private SlayerCompanionPanel panel;
	private NavigationButton navButton;

	// Client-thread state
	private Optional<CurrentTask> current = Optional.empty();
	private Style style = Style.MELEE;
	private String preferredLocationId;
	private Location selectedLocation;
	private Monster selectedMonster;
	private boolean highlighting;
	private boolean taskDirty;
	private boolean planDirty;
	private int ticksSincePlan;
	/** Bumped when the set of owned catalogue (equipment) item ids changes; gear results are keyed on it. */
	private int ownedVersion;
	private final Map<Integer, Set<Integer>> containerGearIds = new HashMap<>();
	private int inventoryFood = -1;
	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
	private String savedInitialTask;
	private int savedInitialAmount;
	private PrepModel prepModel = PrepModel.noTask();
	private WhereToGoModel whereModel = WhereToGoModel.noTask();
	/*
	 * Gear searches are slow (an upgrade scan is ~100-200 ms), so they run on gearExecutor against an immutable
	 * OwnedItems snapshot. Results are stored per monster with the key they were computed for; the panel keeps
	 * showing the previous result (or nothing) until a result for the current key arrives.
	 */
	private ExecutorService gearExecutor;
	/** Bumped on shutdown so results from a previous run are dropped. */
	private int gearGeneration;
	private final Map<Integer, Optional<Loadout>> bestByMonster = new HashMap<>();
	private final Map<Integer, String> bestKeyByMonster = new HashMap<>();
	private final Map<Integer, Optional<GearOptimizer.Upgrade>> upgradeByMonster = new HashMap<>();
	private final Map<Integer, String> upgradeKeyByMonster = new HashMap<>();
	private final Set<String> pendingGearJobs = new HashSet<>();
	private OwnedItems gearSnapshot;
	private String gearSnapshotKey;
	private Set<Integer> catalogueIds;
	/** Quest states run client scripts, so they are read on login and on the periodic refresh only. */
	private final Map<String, Boolean> questCache = new HashMap<>();

	@Provides
	SlayerCompanionConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerCompanionConfig.class);
	}

	@Override
	protected void startUp()
	{
		data = GameData.load("/com/slayercompanion");
		optimizer = new GearOptimizer(data.allEquipment());
		equipmentQuests = new HashSet<>();
		catalogueIds = new HashSet<>();
		for (Equipment e : data.allEquipment())
		{
			equipmentQuests.addAll(e.getQuestReqs());
			catalogueIds.add(e.getItemId());
		}
		gearExecutor = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "slayer-companion-gear");
			t.setDaemon(true);
			return t;
		});
		tracker = new TaskTracker(client, data);
		session = new TaskSession(Clock.systemUTC());
		owned = new OwnedItems(itemManager::canonicalize);
		safespots = new SafespotStore(data.builtinSafespots(), null);
		safespotMenu = new SafespotMenu(client, safespots, chatboxPanelManager);
		safespotMenu.setOnChange(this::saveSafespots);
		safespotOverlay = new SafespotOverlay(client, safespots);
		shortestPath = new ShortestPath(eventBus, pluginManager);

		eventBus.register(safespotMenu);
		overlayManager.add(safespotOverlay);
		overlayManager.add(bankHighlightOverlay);

		panel = new SlayerCompanionPanel(new PanelActions());
		BufferedImage icon = ImageUtil.loadImageResource(SlayerCompanionPlugin.class, "/skill_icons_small/slayer.png");
		navButton = NavigationButton.builder()
			.tooltip("Slayer Companion")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadProfile();
				taskDirty = true;
				planDirty = true;
			}
		});
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(safespotMenu);
		overlayManager.remove(safespotOverlay);
		overlayManager.remove(bankHighlightOverlay);
		bankHighlightOverlay.setItems(Collections.emptySet());
		clientToolbar.removeNavigation(navButton);
		current = Optional.empty();
		preferredLocationId = null;
		selectedLocation = null;
		selectedMonster = null;
		highlighting = false;
		inventoryFood = -1;
		levels.clear();
		gearExecutor.shutdownNow();
		gearExecutor = null;
		gearGeneration++;
		clearGearResults();
		containerGearIds.clear();
		questCache.clear();
		prepModel = PrepModel.noTask();
		whereModel = WhereToGoModel.noTask();
		panel = null;
		navButton = null;
	}

	// ---- persistence ----

	private void loadProfile()
	{
		owned = OwnedItems.fromJson(configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP,
			SlayerCompanionConfig.OWNED_ITEMS_KEY), itemManager::canonicalize);
		// The inventory and worn containers may have arrived before the profile did.
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			owned.updateInventory(toMap(inv));
		}
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			owned.updateWorn(toMap(worn));
		}
		ownedVersion++;

		safespots.load(configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP,
			SlayerCompanionConfig.SAFESPOTS_KEY));

		savedInitialTask = null;
		savedInitialAmount = 0;
		String initial = configManager.getRSProfileConfiguration(SlayerCompanionConfig.GROUP,
			SlayerCompanionConfig.INITIAL_AMOUNT_KEY);
		if (initial != null)
		{
			int tab = initial.lastIndexOf('\t');
			if (tab > 0)
			{
				try
				{
					savedInitialAmount = Integer.parseInt(initial.substring(tab + 1));
					savedInitialTask = initial.substring(0, tab);
				}
				catch (NumberFormatException e)
				{
					log.debug("Ignoring bad saved initial amount {}", initial);
				}
			}
		}
	}

	private void saveOwned()
	{
		if (configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(SlayerCompanionConfig.GROUP, SlayerCompanionConfig.OWNED_ITEMS_KEY,
				owned.toJson());
		}
	}

	private void saveSafespots()
	{
		if (configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(SlayerCompanionConfig.GROUP, SlayerCompanionConfig.SAFESPOTS_KEY,
				safespots.toJson());
		}
		planDirty = true;
	}

	// ---- events ----

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		// The store and owned items are client-thread only, whichever thread posts this event.
		clientThread.invoke(() ->
		{
			loadProfile();
			clearGearResults();
			containerGearIds.clear();
			taskDirty = true;
			planDirty = true;
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			session.onLoggedIn();
			questCache.clear();
			taskDirty = true;
			planDirty = true;
		}
		else if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			session.onLoggedOut();
			push();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		int varp = e.getVarpId();
		int varbit = e.getVarbitId();
		if (varp == VarPlayerID.SLAYER_COUNT || varp == VarPlayerID.SLAYER_TARGET || varp == VarPlayerID.SLAYER_AREA
			|| varp == VarPlayerID.SLAYER_COUNT_ORIGINAL
			|| varbit == VarbitID.SLAYER_TASKS_COMPLETED || varbit == VarbitID.SLAYER_TARGET_BOSSID)
		{
			taskDirty = true;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged e)
	{
		Integer before = levels.put(e.getSkill(), e.getLevel());
		if (before == null || before != e.getLevel())
		{
			planDirty = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		int id = e.getContainerId();
		ItemContainer c = e.getItemContainer();
		if (c == null)
		{
			return;
		}
		Map<Integer, Integer> items = toMap(c);
		if (id == InventoryID.BANK)
		{
			owned.updateBank(items, Instant.now());
		}
		else if (id == InventoryID.INV)
		{
			owned.updateInventory(items);
			int food = 0;
			for (Item item : c.getItems())
			{
				if (item != null && FoodItems.isFood(item.getId()))
				{
					food += Math.max(1, item.getQuantity());
				}
			}
			inventoryFood = food;
			session.onFoodCount(food);
		}
		else if (id == InventoryID.WORN)
		{
			owned.updateWorn(items);
		}
		else
		{
			return;
		}
		// Potions, loot and food change constantly; only a different set of owned equipment can change the gear.
		Set<Integer> gearIds = new HashSet<>();
		for (int itemId : items.keySet())
		{
			int canon = itemManager.canonicalize(itemId);
			if (catalogueIds.contains(canon))
			{
				gearIds.add(canon);
			}
		}
		if (!gearIds.equals(containerGearIds.put(id, gearIds)))
		{
			ownedVersion++;
		}
		planDirty = true;
		saveOwned();
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived e)
	{
		if (!current.isPresent() || !isTaskNpc(e.getNpc()))
		{
			return;
		}
		for (ItemStack stack : e.getItems())
		{
			session.onLoot(stack.getId(), stack.getQuantity(), itemManager.getItemPrice(stack.getId()));
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		String message = Text.removeTags(e.getMessage());
		tracker.onChatMessage(message);
		if (e.getType() == ChatMessageType.GAMEMESSAGE && message.contains(SUPERIOR_MESSAGE) && current.isPresent())
		{
			session.onSuperior();
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		if (taskDirty)
		{
			taskDirty = false;
			readTask();
		}
		if (++ticksSincePlan >= PLAN_REFRESH_TICKS)
		{
			questCache.clear();
			planDirty = true;
		}
		if (planDirty)
		{
			planDirty = false;
			ticksSincePlan = 0;
			rebuildPlan();
		}
		push();
	}

	// ---- task ----

	private void readTask()
	{
		Optional<CurrentTask> now = tracker.read().map(this::withInitialAmount);
		String before = current.map(c -> c.getTask().getName()).orElse(null);
		String after = now.map(c -> c.getTask().getName()).orElse(null);
		if (tracker.isNewTask(current, now) || !Objects.equals(before, after))
		{
			preferredLocationId = null;
		}
		if (!now.equals(current))
		{
			planDirty = true;
		}
		current = now;
		session.onTaskUpdate(now.orElse(null));
	}

	/** Uses the game's original-amount varp and the saved amount so "of N" is right after a restart mid-task. */
	private CurrentTask withInitialAmount(CurrentTask c)
	{
		String name = c.getTask().getName();
		int original = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
		int total;
		if (original >= c.getRemaining())
		{
			// Set by the game at assignment, so it is right even for a repeat of the same task.
			total = original;
		}
		else
		{
			total = c.getInitialAmount();
			if (savedInitialTask != null && savedInitialTask.equalsIgnoreCase(name) && savedInitialAmount >= c.getRemaining())
			{
				total = Math.max(total, savedInitialAmount);
			}
		}
		if (!name.equalsIgnoreCase(savedInitialTask) || total != savedInitialAmount)
		{
			savedInitialTask = name;
			savedInitialAmount = total;
			if (configManager.getRSProfileKey() != null)
			{
				configManager.setRSProfileConfiguration(SlayerCompanionConfig.GROUP,
					SlayerCompanionConfig.INITIAL_AMOUNT_KEY, name + "\t" + total);
			}
		}
		if (total == c.getInitialAmount())
		{
			return c;
		}
		return new CurrentTask(c.getTask(), c.getRemaining(), total, c.getKonarLocation(), c.getMaster(), c.getStreak());
	}

	private boolean isTaskNpc(NPC npc)
	{
		if (npc == null || npc.getName() == null)
		{
			return false;
		}
		List<Integer> ids = current.get().getTask().getMonsterIds();
		if (ids.isEmpty())
		{
			return true;
		}
		for (int id : ids)
		{
			Optional<Monster> m = data.monster(id);
			if (m.isPresent() && baseName(m.get().getName()).equalsIgnoreCase(npc.getName()))
			{
				return true;
			}
		}
		return false;
	}

	private static String baseName(String monsterName)
	{
		int paren = monsterName.indexOf(" (");
		return paren > 0 ? monsterName.substring(0, paren) : monsterName;
	}

	// ---- plan (Prep + Where to go) ----

	private void rebuildPlan()
	{
		if (!current.isPresent())
		{
			selectedLocation = null;
			selectedMonster = null;
			safespotMenu.setContext(null, 0);
			safespotOverlay.setLocationId(null);
			bankHighlightOverlay.setItems(Collections.emptySet());
			prepModel = PrepModel.noTask();
			whereModel = WhereToGoModel.noTask();
			return;
		}
		CurrentTask c = current.get();
		SlayerTask task = c.getTask();
		List<Location> locations = task.getId() == null ? Collections.emptyList() : data.locations(task.getId());
		CombatStats stats = stats();
		Set<String> questsDone = questsDone(locations);

		String key = style + ":" + ownedVersion + ":" + owned.bankSeen() + ":" + stats + ":" + questsDone;

		List<RankedLocation> ranked = Collections.emptyList();
		WorldPoint player = playerPoint();
		if (player != null && !locations.isEmpty())
		{
			ranked = LocationRanker.rank(locations, style, player,
				l -> Requirements.lockReason(l, this::skillLevel, questsDone::contains, SlayerCompanionPlugin::questName, owned),
				c.getKonarLocation().filter(s -> !s.trim().isEmpty()),
				l -> safespots.hasAny(l.getId()),
				l -> xpPerHour(stats, l, key),
				l -> gpPerHour(stats, l, key));
		}

		String selectedId = WhereToGoModel.effectiveSelection(ranked, preferredLocationId);
		selectedLocation = locations.stream().filter(l -> l.getId().equals(selectedId)).findFirst()
			.orElse(locations.isEmpty() ? null : locations.get(0));
		selectedMonster = selectedLocation != null
			? data.monster(selectedLocation.getMonsterId()).orElse(null)
			: task.getMonsterIds().stream().map(data::monster).filter(Optional::isPresent).map(Optional::get)
				.findFirst().orElse(null);

		session.setMonster(selectedMonster);
		String locationId = selectedLocation == null ? null : selectedLocation.getId();
		safespotMenu.setContext(locationId, selectedMonster == null ? 0 : selectedMonster.getId());
		safespotOverlay.setLocationId(locationId);

		Optional<Loadout> best = selectedMonster == null ? Optional.empty() : bestGear(stats, selectedMonster, key);
		Optional<GearOptimizer.Upgrade> upgrade = selectedMonster == null ? Optional.empty()
			: upgrade(stats, questsDone, selectedMonster, key);
		boolean safespot = selectedLocation != null && safespots.hasAny(selectedLocation.getId());
		prepModel = PrepModel.build(c, selectedMonster, safespot, owned, style, best, upgrade, highlighting,
			Instant.now());
		bankHighlightOverlay.setItems(highlighting ? prepModel.getHighlightIds() : Collections.emptySet());
		whereModel = WhereToGoModel.build(ranked, selectedId);
	}

	private void clearGearResults()
	{
		bestByMonster.clear();
		bestKeyByMonster.clear();
		upgradeByMonster.clear();
		upgradeKeyByMonster.clear();
		pendingGearJobs.clear();
		gearSnapshot = null;
		gearSnapshotKey = null;
	}

	/** Latest best gear for the monster; requests a fresh search when it is missing or stale. */
	private Optional<Loadout> bestGear(CombatStats stats, Monster m, String key)
	{
		if (!owned.bankSeen())
		{
			return Optional.empty();
		}
		int id = m.getId();
		if (!key.equals(bestKeyByMonster.get(id)))
		{
			Style s = style;
			runGearJob("best:" + key + ":" + id, snapshot -> optimizer.best(stats, s, m, true, snapshot),
				r ->
				{
					bestByMonster.put(id, r);
					bestKeyByMonster.put(id, key);
				});
		}
		return bestByMonster.getOrDefault(id, Optional.empty());
	}

	/** Latest biggest upgrade for the monster; computed once best gear for the current key is known. */
	private Optional<GearOptimizer.Upgrade> upgrade(CombatStats stats, Set<String> questsDone, Monster m, String key)
	{
		int id = m.getId();
		Optional<Loadout> best = bestByMonster.getOrDefault(id, Optional.empty());
		if (owned.bankSeen() && best.isPresent() && key.equals(bestKeyByMonster.get(id))
			&& !key.equals(upgradeKeyByMonster.get(id)))
		{
			Loadout base = best.get();
			runGearJob("upgrade:" + key + ":" + id,
				snapshot -> optimizer.biggestUpgrade(stats, questsDone, base, m, true, snapshot),
				r ->
				{
					upgradeByMonster.put(id, r);
					upgradeKeyByMonster.put(id, key);
				});
		}
		return upgradeByMonster.getOrDefault(id, Optional.empty());
	}

	/** Runs a gear search off the client thread on a snapshot of owned items; the result is applied on the client thread. */
	private <T> void runGearJob(String tag, Function<OwnedItems, T> search, Consumer<T> apply)
	{
		ExecutorService executor = gearExecutor;
		if (executor == null || !pendingGearJobs.add(tag))
		{
			return;
		}
		String snapshotKey = ownedVersion + ":" + owned.bankSeenAt().orElse(null);
		if (!snapshotKey.equals(gearSnapshotKey))
		{
			// Canonical ids are already applied, so the copy needs no ItemManager (safe off-thread).
			gearSnapshot = OwnedItems.fromJson(owned.toJson());
			gearSnapshotKey = snapshotKey;
		}
		OwnedItems snapshot = gearSnapshot;
		int generation = gearGeneration;
		executor.execute(() ->
		{
			T result;
			try
			{
				result = search.apply(snapshot);
			}
			catch (RuntimeException ex)
			{
				log.warn("Gear search failed", ex);
				result = null;
			}
			T r = result;
			clientThread.invoke(() ->
			{
				if (generation != gearGeneration)
				{
					return;
				}
				pendingGearJobs.remove(tag);
				if (r != null)
				{
					apply.accept(r);
					planDirty = true;
				}
			});
		});
	}

	private double dps(CombatStats stats, Monster m, String key)
	{
		Loadout l = bestGear(stats, m, key).orElseGet(() ->
		{
			Loadout empty = new Loadout();
			empty.setStyle(style);
			return empty;
		});
		return DpsCalculator.dps(stats, l, m, true);
	}

	private double xpPerHour(CombatStats stats, Location l, String key)
	{
		return data.monster(l.getMonsterId())
			.map(m -> Estimates.xpPerHour(Estimates.killsPerHour(dps(stats, m, key), m, l), m))
			.orElse(0.0);
	}

	private double gpPerHour(CombatStats stats, Location l, String key)
	{
		return data.monster(l.getMonsterId())
			.map(m -> Estimates.gpPerHour(Estimates.killsPerHour(dps(stats, m, key), m, l),
				Estimates.gpPerKill(m, itemManager::getItemPrice), l))
			.orElse(0.0);
	}

	private CombatStats stats()
	{
		return new CombatStats(
			client.getRealSkillLevel(Skill.ATTACK), client.getRealSkillLevel(Skill.STRENGTH),
			client.getRealSkillLevel(Skill.DEFENCE), client.getRealSkillLevel(Skill.RANGED),
			client.getRealSkillLevel(Skill.MAGIC), client.getRealSkillLevel(Skill.HITPOINTS),
			client.getRealSkillLevel(Skill.PRAYER), client.getRealSkillLevel(Skill.SLAYER));
	}

	private int skillLevel(String name)
	{
		try
		{
			return client.getRealSkillLevel(Skill.valueOf(name.toUpperCase(Locale.ROOT)));
		}
		catch (IllegalArgumentException e)
		{
			return -1;
		}
	}

	/** Finished quests among those the gear catalogue and these locations ask for. Client thread only. */
	private Set<String> questsDone(List<Location> locations)
	{
		Set<String> wanted = new HashSet<>(equipmentQuests);
		locations.forEach(l -> wanted.addAll(l.getQuestReqs()));
		Set<String> done = new TreeSet<>();
		for (String q : wanted)
		{
			if (questCache.computeIfAbsent(q, this::questFinished))
			{
				done.add(q);
			}
		}
		return done;
	}

	private boolean questFinished(String quest)
	{
		try
		{
			return Quest.valueOf(quest).getState(client) == QuestState.FINISHED;
		}
		catch (IllegalArgumentException e)
		{
			log.debug("Unknown quest {}", quest);
			return false;
		}
	}

	private static String questName(String quest)
	{
		try
		{
			return Quest.valueOf(quest).getName();
		}
		catch (IllegalArgumentException e)
		{
			return quest;
		}
	}

	private WorldPoint playerPoint()
	{
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return null;
		}
		return WorldPoint.fromLocalInstance(client, p.getLocalLocation());
	}

	// ---- panel ----

	private void push()
	{
		SlayerCompanionPanel target = panel;
		if (target == null)
		{
			return;
		}
		OnTaskModel onTask = onTaskModel();
		PrepModel prep = prepModel;
		WhereToGoModel where = whereModel;
		SwingUtilities.invokeLater(() -> target.update(prep, where, onTask));
	}

	private OnTaskModel onTaskModel()
	{
		if (!current.isPresent())
		{
			return OnTaskModel.noTask();
		}
		CurrentTask c = current.get();
		return OnTaskModel.build(c.getTask().getName(), selectedLocation == null ? null : selectedLocation.getName(),
			session, selectedMonster, inventoryFood,
			client.getBoostedSkillLevel(Skill.PRAYER), client.getRealSkillLevel(Skill.PRAYER),
			client.getSkillExperience(Skill.SLAYER), c.getStreak());
	}

	private static Map<Integer, Integer> toMap(ItemContainer c)
	{
		Map<Integer, Integer> out = new HashMap<>();
		for (Item item : c.getItems())
		{
			if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
			{
				out.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return out;
	}

	/** Panel callbacks arrive on the Swing thread and hop to the client thread before touching state. */
	private class PanelActions implements SlayerCompanionPanel.Actions
	{
		@Override
		public void selectStyle(Style s)
		{
			clientThread.invoke(() ->
			{
				style = s;
				planDirty = true;
			});
		}

		@Override
		public void selectLocation(String locationId)
		{
			clientThread.invoke(() ->
			{
				preferredLocationId = locationId;
				planDirty = true;
			});
		}

		@Override
		public void toggleBankHighlight()
		{
			clientThread.invoke(() ->
			{
				highlighting = !highlighting;
				planDirty = true;
			});
		}

		@Override
		public void takeMeThere()
		{
			clientThread.invoke(() ->
			{
				Location l = selectedLocation;
				String message = l == null || l.getEntrance() == null ? null
					: WhereToGoModel.takeMeThereMessage(shortestPath.pathTo(l.getEntrance().toWorldPoint()));
				SlayerCompanionPanel target = panel;
				if (target != null)
				{
					SwingUtilities.invokeLater(() -> target.showWhereMessage(message));
				}
			});
		}

		@Override
		public void sendSafespots()
		{
			clientThread.invoke(() ->
			{
				String json = SafespotExport.export(safespots);
				int count = safespots.exportableCount();
				SlayerCompanionPanel target = panel;
				SwingUtilities.invokeLater(() ->
				{
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(json), null);
					if (target != null)
					{
						target.showWhereMessage(WhereToGoModel.copiedMessage(count));
					}
				});
			});
		}
	}
}
