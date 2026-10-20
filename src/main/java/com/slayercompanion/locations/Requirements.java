package com.slayercompanion.locations;

import com.slayercompanion.data.ItemNeed;
import com.slayercompanion.data.Location;
import com.slayercompanion.gear.OwnedItems;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Works out why a location can't be used yet, for {@link LocationRanker#rank}. */
public final class Requirements
{
	private Requirements()
	{
	}

	/**
	 * @param skillLevel lowercase skill name (as in locations.json) to the player's level; negative when unknown
	 * @param questDone  RuneLite Quest enum name to whether it is finished
	 * @param questName  RuneLite Quest enum name to its display name
	 * @return the first unmet requirement, or empty when the location is usable
	 */
	public static Optional<String> lockReason(Location l, ToIntFunction<String> skillLevel, Predicate<String> questDone,
		Function<String, String> questName, OwnedItems owned)
	{
		for (Map.Entry<String, Integer> req : l.getSkillReqs().entrySet())
		{
			int level = skillLevel.applyAsInt(req.getKey());
			if (level >= 0 && level < req.getValue())
			{
				String skill = req.getKey().isEmpty() ? req.getKey()
					: Character.toUpperCase(req.getKey().charAt(0)) + req.getKey().substring(1);
				return Optional.of("Needs " + req.getValue() + " " + skill);
			}
		}
		for (String quest : l.getQuestReqs())
		{
			if (!questDone.test(quest))
			{
				return Optional.of("Needs " + questName.apply(quest));
			}
		}
		for (ItemNeed need : l.getItemReqs())
		{
			if (need.getItemIds().stream().noneMatch(id -> owned.count(id) > 0))
			{
				return Optional.of("Needs " + need.getName());
			}
		}
		return Optional.empty();
	}
}
