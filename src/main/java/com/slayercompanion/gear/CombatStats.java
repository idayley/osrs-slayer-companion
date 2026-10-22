package com.slayercompanion.gear;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Real (unboosted) levels, plus what {@link Boosts} may assume on top of them. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CombatStats
{
	private int attack;
	private int strength;
	private int defence;
	private int ranged;
	private int magic;
	private int hitpoints;
	private int prayer;
	private int slayer;
	/** Assume the best offensive prayer and the standard potion for the style; off for plain level maths. */
	private boolean assumeBoosts;
	/** Knight Waves training ground done: unlocks Chivalry and Piety. */
	private boolean pietyUnlocked;
	private boolean rigourUnlocked;
	private boolean auguryUnlocked;
	private boolean deadeyeUnlocked;
	private boolean mysticVigourUnlocked;

	/** Levels only: no assumed boosts, no prayer unlocks. */
	public CombatStats(int attack, int strength, int defence, int ranged, int magic, int hitpoints, int prayer, int slayer)
	{
		this(attack, strength, defence, ranged, magic, hitpoints, prayer, slayer, false, false, false, false, false, false);
	}
}
