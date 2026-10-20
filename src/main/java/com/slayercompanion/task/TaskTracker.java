package com.slayercompanion.task;

import com.slayercompanion.data.GameData;
import com.slayercompanion.data.SlayerTask;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Reads the active slayer task from client state, the same way RuneLite's SlayerPlugin does. */
public class TaskTracker
{
	/** Varp 395 value meaning "boss task"; the actual boss is in the SLAYER_TARGET_BOSSID varbit. */
	private static final int BOSS_TASK_TARGET = 98;

	private static final Pattern MASTER = Pattern.compile(
		"\\b(Turael|Spria|Mazchna|Vannaka|Chaeldar|Konar|Nieve|Steve|Duradel|Krystilia|Aya)\\b",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern ASSIGNMENT = Pattern.compile("assign|task", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final GameData data;

	private String master;
	private boolean masterFresh;
	private String lastTaskName;
	private String initialFor;
	private int maxInitial;

	public TaskTracker(Client client, GameData data)
	{
		this.client = client;
		this.data = data;
	}

	/** Remembers the slayer master named in an assignment chat message. */
	public void onChatMessage(String message)
	{
		if (message == null || !ASSIGNMENT.matcher(message).find())
		{
			return;
		}
		Matcher m = MASTER.matcher(message);
		if (m.find())
		{
			master = m.group(1).toLowerCase(Locale.ROOT);
			masterFresh = true;
		}
	}

	/** Restores the initial amount persisted by the plugin; the max of this and values seen is kept. */
	public void rememberInitialAmount(String taskName, int amount)
	{
		if (taskName == null)
		{
			return;
		}
		String key = taskName.toLowerCase(Locale.ROOT);
		if (key.equals(initialFor))
		{
			maxInitial = Math.max(maxInitial, amount);
		}
		else
		{
			initialFor = key;
			maxInitial = amount;
		}
	}

	public Optional<CurrentTask> read()
	{
		int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		if (remaining <= 0)
		{
			return Optional.empty();
		}
		String name = taskName(client.getVarpValue(VarPlayerID.SLAYER_TARGET));
		if (name == null)
		{
			return Optional.empty();
		}

		String key = name.toLowerCase(Locale.ROOT);
		if (!key.equals(lastTaskName))
		{
			if (!masterFresh)
			{
				master = null;
			}
			masterFresh = false;
			lastTaskName = key;
		}
		if (!key.equals(initialFor))
		{
			initialFor = key;
			maxInitial = remaining;
		}
		maxInitial = Math.max(maxInitial, remaining);

		return Optional.of(new CurrentTask(
			resolve(name),
			remaining,
			maxInitial,
			konarLocation(),
			Optional.ofNullable(master),
			client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED)));
	}

	/** True when the task name changed or the remaining count went up (a fresh assignment). */
	public boolean isNewTask(Optional<CurrentTask> previous, Optional<CurrentTask> now)
	{
		if (!now.isPresent())
		{
			return false;
		}
		if (!previous.isPresent())
		{
			return true;
		}
		String a = previous.get().getTask().getName();
		String b = now.get().getTask().getName();
		return !a.equalsIgnoreCase(b) || now.get().getRemaining() > previous.get().getRemaining();
	}

	private String taskName(int target)
	{
		int taskRow;
		if (target == BOSS_TASK_TARGET)
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
				DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
			if (rows == null || rows.isEmpty())
			{
				return null;
			}
			Object[] f = client.getDBTableField(rows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0);
			if (f == null || f.length == 0)
			{
				return null;
			}
			taskRow = (Integer) f[0];
		}
		else
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, target);
			if (rows == null || rows.isEmpty())
			{
				return null;
			}
			taskRow = rows.get(0);
		}
		Object[] f = client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
		return f == null || f.length == 0 ? null : (String) f[0];
	}

	private Optional<String> konarLocation()
	{
		int area = client.getVarpValue(VarPlayerID.SLAYER_AREA);
		if (area <= 0)
		{
			return Optional.empty();
		}
		List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, area);
		if (rows == null || rows.isEmpty())
		{
			return Optional.empty();
		}
		Object[] f = client.getDBTableField(rows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0);
		return f == null || f.length == 0 ? Optional.empty() : Optional.ofNullable((String) f[0]);
	}

	/** Finds bundled data for an in-game name, tolerating case and singular/plural differences. */
	private SlayerTask resolve(String name)
	{
		Set<String> candidates = new LinkedHashSet<>();
		String lower = name.trim().toLowerCase(Locale.ROOT);
		candidates.add(lower);
		candidates.add(lower + "s");
		candidates.add(lower + "es");
		if (lower.endsWith("s"))
		{
			candidates.add(lower.substring(0, lower.length() - 1));
		}
		if (lower.endsWith("es"))
		{
			candidates.add(lower.substring(0, lower.length() - 2));
		}
		if (lower.endsWith("ies"))
		{
			candidates.add(lower.substring(0, lower.length() - 3) + "y");
		}
		if (lower.endsWith("y"))
		{
			candidates.add(lower.substring(0, lower.length() - 1) + "ies");
		}
		for (String c : candidates)
		{
			Optional<SlayerTask> t = data.taskByName(c);
			if (t.isPresent())
			{
				return t.get();
			}
		}
		SlayerTask stub = new SlayerTask();
		stub.setName(name);
		return stub;
	}
}
