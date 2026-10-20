package com.slayercompanion.gear;

import com.slayercompanion.data.Equipment;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Loadout
{
	private Map<String, Equipment> bySlot = new HashMap<>();
	private Style style = Style.MELEE;
	/** stab, slash or crush. */
	private String meleeType = "slash";
	/** e.g. "Water Wave"; null for powered staves or non-magic. */
	private String spell;
}
