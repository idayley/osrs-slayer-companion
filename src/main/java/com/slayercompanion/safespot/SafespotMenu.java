package com.slayercompanion.safespot;

import com.slayercompanion.data.Safespot;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
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

	private final Client client;
	private final SafespotStore store;
	private final ChatboxPanelManager chatboxPanelManager;

	private volatile String locationId;
	private volatile int monsterId;
	private Runnable onChange = () -> { };

	@Inject
	public SafespotMenu(Client client, SafespotStore store, ChatboxPanelManager chatboxPanelManager)
	{
		this.client = client;
		this.store = store;
		this.chatboxPanelManager = chatboxPanelManager;
	}

	/** Sets the current location and monster; a null locationId disables the menu. */
	public void setContext(String locationId, int monsterId)
	{
		this.locationId = locationId;
		this.monsterId = monsterId;
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
			addEntry(MARK, () -> promptNote(loc, monsterId, wp));
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

	private void promptNote(String loc, int monster, WorldPoint wp)
	{
		chatboxPanelManager.openTextInput("Safespot note (optional)")
			.onDone(note ->
			{
				store.mark(loc, monster, wp, note);
				onChange.run();
			})
			.build();
	}
}
