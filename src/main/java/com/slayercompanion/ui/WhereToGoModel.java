package com.slayercompanion.ui;

import com.slayercompanion.locations.RankedLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Value;

/** What the Where to go tab shows: ranked locations with estimated XP/GP bars. */
@Value
public class WhereToGoModel
{
	static final String NO_SAFESPOT = "No known safespot";
	private static final String DATA_SAFESPOT_TAG = "Safespot";

	public enum TagKind
	{
		NORMAL, SAFE, NO_SAFE, BLUE, RISK, LOCK
	}

	@Value
	public static class Tag
	{
		String text;
		TagKind kind;
	}

	@Value
	public static class Row
	{
		String locationId;
		String name;
		String travel;
		List<Tag> tags;
		String xpText;
		String gpText;
		double xpFraction;
		double gpFraction;
		boolean recommended;
		String why;
		boolean locked;
		boolean selected;
	}

	boolean noTask;
	String message;
	List<Row> rows;
	String selectedId;
	String note;

	public static WhereToGoModel noTask()
	{
		return new WhereToGoModel(true, PrepModel.NO_TASK, Collections.emptyList(), null, null);
	}

	public static String takeMeThereMessage(boolean routed)
	{
		return routed ? null : "Shortest Path isn't installed";
	}

	public static String copiedMessage(int count)
	{
		return "Copied " + count + " safespots";
	}

	/** The preferred id if it is one of the rows, otherwise the recommended (top) row; null when there are none. */
	public static String effectiveSelection(List<RankedLocation> ranked, String preferredId)
	{
		for (RankedLocation r : ranked)
		{
			if (Objects.equals(r.getLocation().getId(), preferredId))
			{
				return preferredId;
			}
		}
		return ranked.isEmpty() ? null : ranked.get(0).getLocation().getId();
	}

	public static WhereToGoModel build(List<RankedLocation> ranked, String preferredId)
	{
		String selected = effectiveSelection(ranked, preferredId);
		double maxXp = 0;
		double maxGp = 0;
		for (RankedLocation r : ranked)
		{
			if (r.isUsable())
			{
				maxXp = Math.max(maxXp, r.getXpPerHour());
				maxGp = Math.max(maxGp, r.getGpPerHour());
			}
		}

		List<Row> rows = new ArrayList<>();
		for (RankedLocation r : ranked)
		{
			String id = r.getLocation().getId();
			boolean isSelected = Objects.equals(id, selected);
			if (!r.isUsable())
			{
				rows.add(new Row(id, r.getLocation().getName(), "—",
					Collections.singletonList(new Tag(r.getLockedReason(), TagKind.LOCK)),
					null, null, 0, 0, false, null, true, isSelected));
				continue;
			}
			rows.add(new Row(id, r.getLocation().getName(),
				Format.travel(r.getTravelSeconds()) + (r.isRecommended() ? " away" : ""),
				tags(r), Format.compact(r.getXpPerHour()) + "/hr", Format.compact(r.getGpPerHour()) + "/hr",
				maxXp > 0 ? r.getXpPerHour() / maxXp : 0, maxGp > 0 ? r.getGpPerHour() / maxGp : 0,
				r.isRecommended(), r.isRecommended() ? r.getWhy() : null, false, isSelected));
		}
		return new WhereToGoModel(false, null, rows, selected,
			"XP and GP per hour are estimated from your gear and levels");
	}

	private static List<Tag> tags(RankedLocation r)
	{
		List<Tag> out = new ArrayList<>();
		out.add(r.isHasSafespot() ? new Tag("Safespot", TagKind.SAFE) : new Tag(NO_SAFESPOT, TagKind.NO_SAFE));
		for (String t : r.getLocation().getTags())
		{
			if (DATA_SAFESPOT_TAG.equalsIgnoreCase(t))
			{
				// Whether we can show a spot comes from the safespot store, not the wiki tag.
				continue;
			}
			TagKind kind = t.startsWith("Catacombs") ? TagKind.BLUE
				: "Wilderness".equalsIgnoreCase(t) ? TagKind.RISK : TagKind.NORMAL;
			out.add(new Tag(t, kind));
		}
		return out;
	}
}
