package com.slayercompanion.safespot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Renders player-contributed safespots in the exact safespots.json format, ready to paste into the repo. */
public final class SafespotExport
{
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private SafespotExport()
	{
	}

	public static String export(SafespotStore store)
	{
		return GSON.toJson(store.exportable());
	}
}
