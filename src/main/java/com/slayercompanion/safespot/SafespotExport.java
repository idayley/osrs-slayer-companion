package com.slayercompanion.safespot;

import com.google.gson.Gson;

/** Renders player-contributed safespots in the exact safespots.json format, ready to paste into the repo. */
public final class SafespotExport
{
	private SafespotExport()
	{
	}

	public static String export(Gson gson, SafespotStore store)
	{
		return gson.newBuilder().setPrettyPrinting().create().toJson(store.exportable());
	}
}
