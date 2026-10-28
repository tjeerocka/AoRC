package com.aorc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item IDs used by the plugin. Numbers come from the OSRS Wiki.
 * Plain numbers are used on purpose, so the plugin does not break when
 * RuneLite renames its ItemID constants.
 */
public final class RuneIds
{
	private RuneIds()
	{
	}

	public static final int BLOOD_RUNE = 565;
	public static final int SOUL_RUNE = 566;

	// Essence used at Arceuus
	public static final int DENSE_ESSENCE_BLOCK = 13445;
	public static final int DARK_ESSENCE_BLOCK = 13446;
	public static final int DARK_ESSENCE_FRAGMENTS = 7938;
	public static final int RUNE_ESSENCE = 1436;
	public static final int PURE_ESSENCE = 7936;
	public static final int DAEYALT_ESSENCE = 24704;

	/** Essence that is used up at an altar. None left = end of a trip. */
	public static boolean isTripEssence(int itemId)
	{
		return itemId == DENSE_ESSENCE_BLOCK || itemId == DARK_ESSENCE_BLOCK
			|| itemId == DARK_ESSENCE_FRAGMENTS || itemId == RUNE_ESSENCE
			|| itemId == PURE_ESSENCE || itemId == DAEYALT_ESSENCE;
	}

	// Blood essence (wiki: inactive 26390, active 26392)
	public static final int BLOOD_ESSENCE = 26390;
	public static final int BLOOD_ESSENCE_ACTIVE = 26392;

	// Abyssal lantern variants (wiki item IDs)
	public static final int LANTERN_WILLOW = 26838;
	public static final int LANTERN_BLISTERWOOD = 26844;
	public static final int LANTERN_MAGIC = 26846;
	public static final int LANTERN_REDWOOD = 26848;

	// Varbit: elite Kourend & Kebos diary done (RuneLite Varbits.DIARY_KOUREND_ELITE)
	public static final int VARBIT_DIARY_KOUREND_ELITE = 7928;

	/** All rune types the plugin counts, in display order. */
	public static final Map<Integer, String> RUNES;

	static
	{
		Map<Integer, String> m = new LinkedHashMap<>();
		m.put(BLOOD_RUNE, "Blood");
		m.put(SOUL_RUNE, "Soul");
		m.put(560, "Death");
		m.put(563, "Law");
		m.put(561, "Nature");
		m.put(9075, "Astral");
		m.put(562, "Chaos");
		m.put(564, "Cosmic");
		m.put(21880, "Wrath");
		m.put(559, "Body");
		m.put(558, "Mind");
		m.put(556, "Air");
		m.put(555, "Water");
		m.put(557, "Earth");
		m.put(554, "Fire");
		m.put(4695, "Mist");
		m.put(4696, "Dust");
		m.put(4698, "Mud");
		m.put(4697, "Smoke");
		m.put(4694, "Steam");
		m.put(4699, "Lava");
		RUNES = Collections.unmodifiableMap(m);
	}

	public static boolean isRune(int itemId)
	{
		return RUNES.containsKey(itemId);
	}
}
