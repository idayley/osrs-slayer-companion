package com.slayercompanion.safespot;

import com.slayercompanion.data.Location;
import com.slayercompanion.data.Safespot;
import com.slayercompanion.data.WorldPointDto;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.chatbox.ChatboxPanelManager;

/**
 * Adds safespot options on the "Walk here" entry, modelled on RuneLite's Ground Markers plugin:
 * shift + right-click offers "Mark safespot"; a tile with a shown spot offers "Looks right" (unverified
 * builtin only) and "Wrong spot". Existing menu entries are never changed or removed.
 */
public class SafespotMenu
{
	private static final String WALK_HERE = "Walk here";
	private static final String MARK = "Mark safespot";
	private static final String LOOKS_RIGHT = "Looks right";
	private static final String WRONG_SPOT = "Wrong spot";
	private static final String TARGET = "Safespot";
	/** A mark goes to the task location whose entrance is within this many tiles of the player. */
	static final int NEAR_ENTRANCE_TILES = 150;

	private final Client client;
	private final SafespotStore store;
	private final ChatboxPanelManager chatboxPanelManager;

	private volatile String locationId;
	private volatile int monsterId;
	private volatile Location selected;
	private volatile List<Location> taskLocations = Collections.emptyList();
	private Runnable onChange = () -> { };

	@Inject
	public SafespotMenu(Client client, SafespotStore store, ChatboxPanelManager chatboxPanelManager)
	{
		this.client = client;
		this.store = store;
		this.chatboxPanelManager = chatboxPanelManager;
	}

	/**
	 * Sets the selected location and monster plus every location of the current task;
	 * a null selected location disables the menu.
	 */
	public void setContext(Location selected, int monsterId, List<Location> taskLocations)
	{
		this.selected = selected;
		this.locationId = selected == null ? null : selected.getId();
		this.monsterId = monsterId;
		this.taskLocations = taskLocations == null ? Collections.emptyList() : taskLocations;
	}

	/**
	 * The task location whose entrance is nearest the player (Chebyshev, same plane, within
	 * {@link #NEAR_ENTRANCE_TILES}); the selected location when none is that close.
	 */
	static Location locationForMark(List<Location> taskLocations, Location selected, WorldPoint player)
	{
		if (player == null)
		{
			return selected;
		}
		Location best = null;
		int bestDist = Integer.MAX_VALUE;
		for (Location l : taskLocations)
		{
			WorldPointDto e = l.getEntrance();
			if (e == null || e.getPlane() != player.getPlane())
			{
				continue;
			}
			int dist = Math.max(Math.abs(e.getX() - player.getX()), Math.abs(e.getY() - player.getY()));
			if (dist <= NEAR_ENTRANCE_TILES && dist < bestDist)
			{
				best = l;
				bestDist = dist;
			}
		}
		return best != null ? best : selected;
	}

	/** Called after any store change so the owner can persist {@link SafespotStore#toJson()}. */
	public void setOnChange(Runnable onChange)
	{
		this.onChange = onChange == null ? () -> { } : onChange;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		String loc = locationId;
		if (loc == null || event.getType() != MenuAction.WALK.getId() || !WALK_HERE.equals(event.getOption()))
		{
			return;
		}
		Tile tile = client.getSelectedSceneTile();
		if (tile == null)
		{
			return;
		}
		WorldPoint wp = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());

		// wp is in template space, the same space spots are stored in.
		Safespot shown = store.spotAt(loc, wp);

		if (shown != null)
		{
			if (!shown.isVerified() && "builtin".equals(shown.getSource()))
			{
				addEntry(LOOKS_RIGHT, () ->
				{
					store.confirm(wp);
					onChange.run();
				});
			}
			addEntry(WRONG_SPOT, () ->
			{
				store.reject(wp);
				onChange.run();
			});
		}
		else if (client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			Location target = locationForMark(taskLocations, selected, playerPoint());
			if (target == null)
			{
				return;
			}
			int monster = target == selected ? monsterId : target.getMonsterId();
			addEntry(MARK, () -> promptNote(target, monster, wp));
		}
	}

	private void addEntry(String option, Runnable action)
	{
		client.getMenu().createMenuEntry(-1)
			.setOption(option)
			.setTarget(TARGET)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> action.run());
	}

	private WorldPoint playerPoint()
	{
		Player p = client.getLocalPlayer();
		return p == null ? null : WorldPoint.fromLocalInstance(client, p.getLocalLocation());
	}

	private void promptNote(Location loc, int monster, WorldPoint wp)
	{
		chatboxPanelManager.openTextInput("Safespot note for " + loc.getName() + " (optional)")
			.onDone(note ->
			{
				store.mark(loc.getId(), monster, wp, note);
				onChange.run();
			})
			.build();
	}
}
