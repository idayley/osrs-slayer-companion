package com.slayercompanion.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ItemNeed
{
	private String name;
	/** Any one of these items satisfies the need. */
	private List<Integer> itemIds = new ArrayList<>();
}
