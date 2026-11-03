package com.runecraftcompanion;

/** Rune type for the rune goal (setting). ANY = all rune types together. */
public enum GoalRune
{
	ANY("Any rune", 0),
	BLOOD("Blood", RuneIds.BLOOD_RUNE),
	SOUL("Soul", RuneIds.SOUL_RUNE),
	DEATH("Death", 560),
	LAW("Law", 563),
	NATURE("Nature", 561),
	ASTRAL("Astral", 9075),
	CHAOS("Chaos", 562),
	COSMIC("Cosmic", 564),
	WRATH("Wrath", 21880),
	BODY("Body", 559),
	MIND("Mind", 558),
	AIR("Air", 556),
	WATER("Water", 555),
	EARTH("Earth", 557),
	FIRE("Fire", 554),
	MIST("Mist", 4695),
	DUST("Dust", 4696),
	MUD("Mud", 4698),
	SMOKE("Smoke", 4697),
	STEAM("Steam", 4694),
	LAVA("Lava", 4699),
	SUNFIRE("Sunfire", 28929),
	AETHER("Aether", 30843);

	private final String name;
	private final int itemId;

	GoalRune(String name, int itemId)
	{
		this.name = name;
		this.itemId = itemId;
	}

	/** Item ID of the rune, 0 for any rune. */
	public int getItemId()
	{
		return itemId;
	}

	/** Short text for the panel: "blood", "water", or "runes" for any rune. */
	public String shortName()
	{
		return this == ANY ? "runes" : name.toLowerCase();
	}

	@Override
	public String toString()
	{
		return name;
	}
}
