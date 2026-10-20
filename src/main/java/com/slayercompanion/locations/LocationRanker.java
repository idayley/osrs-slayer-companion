package com.slayercompanion.locations;

import com.slayercompanion.data.Location;
import com.slayercompanion.data.WorldPointDto;
import com.slayercompanion.gear.Style;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.runelite.api.coords.WorldPoint;

public final class LocationRanker
{
	static final double SECONDS_PER_TILE = 0.3;
	static final double FAR_TRAVEL_SECONDS = 120;
	static final double PLANE_CHANGE_SECONDS = 30;
	/** Underground areas mostly sit 6400 tiles north of the overworld spot they lie under. */
	static final int UNDERGROUND_Y_OFFSET = 6400;
	static final String KONAR_REASON = "Konar sent you elsewhere";

	private LocationRanker()
	{
	}

	/**
	 * @param lockReason empty when the location is usable, otherwise the first unmet requirement
	 *                   (e.g. "Needs boots of stone").
	 */
	public static List<RankedLocation> rank(List<Location> locations, Style style, WorldPoint player,
		Function<Location, Optional<String>> lockReason, Optional<String> konarLocation,
		Function<Location, Boolean> hasSafespot, ToDoubleFunction<Location> xpHr, ToDoubleFunction<Location> gpHr)
	{
		boolean konarActive = konarLocation.isPresent() && locations.stream().anyMatch(l -> matchesKonar(l, konarLocation.get()));
		boolean safespotMatters = style != Style.MELEE;

		List<RankedLocation> out = new ArrayList<>();
		for (Location l : locations)
		{
			RankedLocation r = new RankedLocation();
			r.setLocation(l);
			Optional<String> reason = konarActive && !matchesKonar(l, konarLocation.get())
				? Optional.of(KONAR_REASON)
				: lockReason.apply(l);
			r.setUsable(!reason.isPresent());
			r.setLockedReason(reason.orElse(null));
			r.setHasSafespot(Boolean.TRUE.equals(hasSafespot.apply(l)));
			r.setTravelSeconds(travelSeconds(player, l.getEntrance()));
			r.setXpPerHour(xpHr.applyAsDouble(l));
			r.setGpPerHour(gpHr.applyAsDouble(l));
			out.add(r);
		}

		Comparator<RankedLocation> order = Comparator.comparing((RankedLocation r) -> !r.isUsable());
		if (safespotMatters)
		{
			order = order.thenComparing(r -> !r.isHasSafespot());
		}
		order = order.thenComparingDouble(RankedLocation::getTravelSeconds);
		out.sort(order);

		if (!out.isEmpty() && out.get(0).isUsable())
		{
			RankedLocation top = out.get(0);
			top.setRecommended(true);
			boolean strictlyClosest = out.stream().skip(1).filter(RankedLocation::isUsable)
				.allMatch(r -> top.getTravelSeconds() < r.getTravelSeconds());
			List<String> reasons = new ArrayList<>();
			if (safespotMatters && top.isHasSafespot())
			{
				reasons.add("has a safespot");
			}
			if (strictlyClosest)
			{
				reasons.add("is closest");
			}
			top.setWhy(reasons.isEmpty() ? "Picked because it's the best available."
				: "Picked because it " + String.join(" and ", reasons) + ".");
		}
		return out;
	}

	/**
	 * Coarse estimate: Chebyshev distance between overworld-equivalent points x 0.3 s, plus a fixed
	 * penalty when the level differs (another plane, or going above/below ground). The player point
	 * is expected in template space already (WorldPoint.fromLocalInstance), so instances are handled.
	 */
	static double travelSeconds(WorldPoint player, WorldPointDto entrance)
	{
		if (entrance == null)
		{
			return FAR_TRAVEL_SECONDS;
		}
		boolean playerUnder = isUnderground(player.getY());
		boolean entranceUnder = isUnderground(entrance.getY());
		int dx = Math.abs(player.getX() - entrance.getX());
		int dy = Math.abs(overworldY(player.getY()) - overworldY(entrance.getY()));
		double seconds = Math.max(dx, dy) * SECONDS_PER_TILE;
		if (player.getPlane() != entrance.getPlane() || playerUnder != entranceUnder)
		{
			seconds += PLANE_CHANGE_SECONDS;
		}
		return seconds;
	}

	private static boolean isUnderground(int y)
	{
		return y >= UNDERGROUND_Y_OFFSET;
	}

	private static int overworldY(int y)
	{
		return isUnderground(y) ? y - UNDERGROUND_Y_OFFSET : y;
	}

	private static boolean matchesKonar(Location l, String konar)
	{
		if (l.getName() == null)
		{
			return false;
		}
		String a = l.getName().toLowerCase(Locale.ROOT);
		String b = konar.toLowerCase(Locale.ROOT);
		return a.contains(b) || b.contains(a);
	}
}
