package com.aorc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A snapshot of the numbers for the side panel.
 * Made on the game thread, then shown on the Swing thread (no shared state).
 */
public final class PanelData
{
	public static final class Row
	{
		public final int itemId;
		public final String name;
		public final int count;
		public final int perHour;

		public Row(int itemId, String name, int count, int perHour)
		{
			this.itemId = itemId;
			this.name = name;
			this.count = count;
			this.perHour = perHour;
		}
	}

	public long activeMillis;
	public int xpPerHour;
	public long gpPerHour;
	public int allRunesPerHour;
	public int crafts;
	public int blocksImbued;
	public int bonusPercent;
	public int bonusRunes;
	public List<Row> rows = new ArrayList<>();

	// Profit and blood essence
	public double essenceUsed;
	public long profitPerHour;
	public int essenceCharges = -1;   // -1 = no active essence / unknown
	public boolean essenceEstimate;

	// Trips
	public int trips;
	public int avgTripRunes;
	public long avgTripMillis = -1;
	public long fastestTripMillis = -1;
	public long lastTripMillis = -1;
	public long currentTripMillis = -1;
	public int currentTripRunes;

	// Goals
	public int goalLevel;
	public int currentLevel;
	public int xpLeft;
	public long goalLevelMillis = -1;
	public int goalBlood;
	public int bloodLeft;
	public long goalBloodMillis = -1;

	// History
	public int todayRunes;
	public long todayMillis;
	public String bestDate;     // "yyyy-MM-dd" or null
	public int bestRunes;
	public long bestMillis;
	public int allRunes;
	public long allMillis;
	public int days;

	/** "2026-10-28" -> "28 Oct 2026". */
	public static String formatDate(String isoDate)
	{
		if (isoDate == null)
		{
			return "-";
		}
		try
		{
			return java.time.LocalDate.parse(isoDate)
				.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH));
		}
		catch (RuntimeException e)
		{
			return isoDate;
		}
	}

	/** Blood runes first, then the most crafted rune types. */
	public static void sortRows(List<Row> rows)
	{
		Collections.sort(rows, (a, b) ->
		{
			if (a.itemId == RuneIds.BLOOD_RUNE)
			{
				return b.itemId == RuneIds.BLOOD_RUNE ? 0 : -1;
			}
			if (b.itemId == RuneIds.BLOOD_RUNE)
			{
				return 1;
			}
			return Integer.compare(b.count, a.count);
		});
	}

	/** "1h 05m" or "4m 12s". */
	public static String formatTime(long millis)
	{
		long sec = Math.max(0, millis / 1000);
		long h = sec / 3600;
		long m = (sec % 3600) / 60;
		long s = sec % 60;
		if (h > 0)
		{
			return String.format("%dh %02dm", h, m);
		}
		return String.format("%dm %02ds", m, s);
	}
}
