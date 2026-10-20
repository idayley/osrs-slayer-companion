package com.slayercompanion.task;

import com.slayercompanion.data.SlayerTask;
import java.util.Optional;
import lombok.Value;

/** Snapshot of the player's active slayer assignment. */
@Value
public class CurrentTask
{
	SlayerTask task;
	int remaining;
	int initialAmount;
	Optional<String> konarLocation;
	/** Lowercase master id (matches tasks.json), when seen in an assignment chat message. */
	Optional<String> master;
	int streak;
}
