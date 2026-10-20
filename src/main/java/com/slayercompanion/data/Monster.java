package com.slayercompanion.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class Monster
{
	private int id;
	private String name;
	private int hitpoints;
	private int slayerXp;
	private int defenceLevel;
	private int magicLevel;
	/** Keys: stab, slash, crush, magic, light, standard, heavy. */
	private Map<String, Integer> defensiveBonuses = new HashMap<>();
	/** "water", "air", "earth", "fire" or null. */
	private String elementalWeakness;
	private int elementalWeaknessPercent;
	private int maxHit;
	private List<String> attackStyles = new ArrayList<>();
	private boolean poisonous;
	private boolean venomous;
	private boolean undead;
	private boolean demon;
	private boolean dragon;
	private List<Drop> drops = new ArrayList<>();
}
