package com.slayercompanion.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class SlayerTask
{
	private String id;
	private String name;
	private List<Integer> monsterIds = new ArrayList<>();
	private List<String> masters = new ArrayList<>();
	private List<ItemNeed> requiredItems = new ArrayList<>();
	private List<ItemNeed> recommendedSupplies = new ArrayList<>();
	private List<String> locationIds = new ArrayList<>();
}
