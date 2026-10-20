package com.slayercompanion.gear;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
