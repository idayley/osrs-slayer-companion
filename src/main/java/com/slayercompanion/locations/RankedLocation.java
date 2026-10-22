package com.slayercompanion.locations;

import com.slayercompanion.data.Location;
import lombok.Data;

@Data
public class RankedLocation
{
	private Location location;
	private boolean usable;
	/** Why the location is unusable; null when usable. */
	private String lockedReason;
	private boolean hasSafespot;
	private double travelSeconds;
	/** travelSeconds is Shortest Path's route time rather than a straight-line estimate. */
	private boolean travelRouted;
	private double xpPerHour;
	private double gpPerHour;
	private boolean recommended;
	private String why;
}
