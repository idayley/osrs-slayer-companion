package com.slayercompanion.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class Location
{
	private String id;
	private String name;
	private String taskId;
	private int monsterId;
	private WorldPointDto entrance;
	/** Where "Take me there" sends Shortest Path when it can't reach the entrance; see tools/route_targets.py. */
	private WorldPointDto route;
	private List<String> tags = new ArrayList<>();
	private Map<String, Integer> skillReqs = new HashMap<>();
	/** RuneLite Quest enum names. */
	private List<String> questReqs = new ArrayList<>();
	private List<ItemNeed> itemReqs = new ArrayList<>();
	private double overheadSeconds;
	private double killSpeedFactor;
	private double bonusGpPerKill;

	public WorldPointDto routeTarget()
	{
		return route != null ? route : entrance;
	}
}
