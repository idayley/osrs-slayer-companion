package com.slayercompanion.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;

/**
 * "Show setup in bank": a temporary Bank Tags tab holding only the recommended setup, laid out by
 * {@link com.slayercompanion.gear.SetupLayout}. Same approach as Inventory Setups: a registered (not
 * item-tagged) tag plus a saved layout, opened with the tag name hidden. Client thread only.
 * Requires {@code @PluginDependency(BankTagsPlugin.class)} on the plugin so the Bank Tags services are injectable.
 */
@Slf4j
public class BankSetupTab
{
	public static final String TAG = "slayer companion";
	private static final int OPTIONS = BankTagsService.OPTION_ALLOW_MODIFICATIONS | BankTagsService.OPTION_HIDE_TAG_NAME;

	private final Client client;
	private final ItemManager itemManager;
	private final PluginManager pluginManager;
	private final BankTagsService bankTagsService;
	private final TagManager tagManager;
	private final LayoutManager layoutManager;

	private volatile Set<Integer> itemIds = Collections.emptySet();
	private int[] layout;
	private boolean shown;

	@Inject
	BankSetupTab(Client client, ItemManager itemManager, PluginManager pluginManager, BankTagsService bankTagsService,
		TagManager tagManager, LayoutManager layoutManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.pluginManager = pluginManager;
		this.bankTagsService = bankTagsService;
		this.tagManager = tagManager;
		this.layoutManager = layoutManager;
	}

	/** Whether the Bank Tags plugin is enabled, so the tab can be used instead of the highlight overlay. */
	public boolean isAvailable()
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (p instanceof BankTagsPlugin)
			{
				return pluginManager.isPluginEnabled(p);
			}
		}
		return false;
	}

	public boolean isShown()
	{
		return shown;
	}

	/**
	 * Shows (or refreshes) the tab with this layout of raw item ids; they are canonicalized to bank ids here.
	 * Opens it now if the bank is open, otherwise the next time it opens.
	 * @return false if Bank Tags rejected it, in which case the caller should fall back to the overlay
	 */
	public boolean show(int[] rawLayout)
	{
		int[] canon = new int[rawLayout.length];
		Set<Integer> ids = new HashSet<>();
		for (int i = 0; i < rawLayout.length; i++)
		{
			canon[i] = rawLayout[i] < 0 ? rawLayout[i] : itemManager.canonicalize(rawLayout[i]);
			if (canon[i] >= 0)
			{
				ids.add(canon[i]);
			}
		}
		if (shown && Arrays.equals(canon, layout))
		{
			return true;
		}
		try
		{
			itemIds = Collections.unmodifiableSet(ids);
			boolean refresh = shown;
			if (!refresh)
			{
				tagManager.registerTag(TAG, this::contains);
			}
			layoutManager.saveLayout(new Layout(TAG, canon));
			layout = canon;
			shown = true;
			// A refresh only redraws the tab if it is the one on screen; it never switches tabs on the player.
			if (!refresh || isOpen())
			{
				openIfBankOpen();
			}
			return true;
		}
		catch (RuntimeException | LinkageError e)
		{
			log.warn("Bank Tags tab unavailable", e);
			hide();
			return false;
		}
	}

	/** Removes a layout left behind if the client closed while the tab was shown. */
	public void clearLeftovers()
	{
		try
		{
			layoutManager.removeLayout(TAG);
		}
		catch (RuntimeException | LinkageError e)
		{
			log.warn("Failed to clear Bank Tags layout", e);
		}
	}

	/** Closes the tab if it is open and removes the tag and its layout. */
	public void hide()
	{
		if (!shown)
		{
			return;
		}
		shown = false;
		layout = null;
		itemIds = Collections.emptySet();
		try
		{
			if (isOpen())
			{
				bankTagsService.closeBankTag();
			}
			tagManager.unregisterTag(TAG);
			layoutManager.removeLayout(TAG);
		}
		catch (RuntimeException | LinkageError e)
		{
			log.warn("Failed to remove Bank Tags tab", e);
		}
	}

	private boolean contains(int itemId)
	{
		return itemIds.contains(itemId);
	}

	private boolean isOpen()
	{
		return TAG.equals(bankTagsService.getActiveTag());
	}

	private void openIfBankOpen()
	{
		if (shown && client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null)
		{
			bankTagsService.openBankTag(TAG, OPTIONS);
		}
	}

	/** Runs after Bank Tags' own handler (as Inventory Setups does) so the tab opens each time the bank does. */
	@Subscribe(priority = -1)
	public void onScriptPreFired(ScriptPreFired e)
	{
		if (e.getScriptId() == ScriptID.BANKMAIN_INIT && shown)
		{
			try
			{
				openIfBankOpen();
			}
			catch (RuntimeException e2)
			{
				log.warn("Failed to open Bank Tags tab", e2);
			}
		}
	}
}
