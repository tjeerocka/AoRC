package com.aorc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trip stats per place, like "Blood - Zeah", "Blood - Mory", "Lava - Fire altar".
 * The place of a trip is where its last craft was. Finished trips are added here
 * by the plugin (same trip rules as {@link TripTracker}).
 * These are long-term records: only "Reset all" clears them.
 */
public class TripLocations
{
	public static class Stats
	{
		int trips;
		long runes;
		int timedTrips;
		long millis;
		long fastestMillis = -1;
		long lastMillis = -1;
		int lastRunes;

		public int getTrips()
		{
			return trips;
		}

		public int getAvgRunes()
		{
			return trips == 0 ? 0 : (int) (runes / trips);
		}

		/** -1 when there is no timed trip yet. */
		public long getAvgMillis()
		{
			return timedTrips == 0 ? -1 : millis / timedTrips;
		}

		public long getFastestMillis()
		{
			return fastestMillis;
		}

		public long getLastMillis()
		{
			return lastMillis;
		}
	}

	/** Data written to disk between RuneLite sessions. */
	public static class Saved
	{
		Map<String, Stats> places = new LinkedHashMap<>();
		String current;
		String last;
	}

	private Map<String, Stats> places = new LinkedHashMap<>();
	private String current;   // place of the trip in progress
	private String last;      // place of the last craft (for "This location")

	/** A craft at this place (the trip in progress belongs to the place of its last craft). */
	public void onCraft(String place)
	{
		if (place != null)
		{
			current = place;
			last = place;
		}
	}

	/** A trip finished: runes in it, and its time (-1 = not timed). */
	public void onTripDone(int runes, long millis)
	{
		if (current == null)
		{
			return;
		}
		Stats s = places.computeIfAbsent(current, k -> new Stats());
		s.trips++;
		s.runes += Math.max(0, runes);
		s.lastRunes = runes;
		s.lastMillis = millis;
		if (millis > 0)
		{
			s.timedTrips++;
			s.millis += millis;
			if (s.fastestMillis < 0 || millis < s.fastestMillis)
			{
				s.fastestMillis = millis;
			}
		}
		current = null;
	}

	/** Place of the trip in progress, else the place of your last craft, or null. */
	public String getCurrentOrLast()
	{
		return current != null ? current : last;
	}

	/** Place of your last craft, or null. */
	public String getLast()
	{
		return last;
	}

	/** Stats of one place (empty when none). */
	public Stats get(String place)
	{
		Stats s = place == null ? null : places.get(place);
		return s == null ? new Stats() : s;
	}

	/** Places with at least one finished trip, in name order. */
	public List<String> names()
	{
		List<String> n = new ArrayList<>(places.keySet());
		n.sort(String::compareTo);
		return n;
	}

	public void reset()
	{
		places = new LinkedHashMap<>();
		current = null;
		last = null;
	}

	public Saved save()
	{
		Saved s = new Saved();
		s.places = new LinkedHashMap<>(places);
		s.current = current;
		s.last = last;
		return s;
	}

	public void load(Saved s)
	{
		reset();
		if (s != null)
		{
			if (s.places != null)
			{
				places.putAll(s.places);
			}
			current = s.current;
			last = s.last;
		}
	}

	/**
	 * Name of the place of one craft.
	 * @param runeId the rune crafted most in this craft
	 * @param runeTypes how many rune types this craft gave (2+ = ZMI)
	 * @param arceuus dark essence fragments were used (Zeah blood / soul altar)
	 * @param guardian guardian essence was used (Guardians of the Rift)
	 * @param regionId map region of the player (for combination runes)
	 */
	public static String placeOf(int runeId, int runeTypes, boolean arceuus, boolean guardian, int regionId)
	{
		if (runeTypes >= 2)
		{
			return "ZMI (Ourania)";
		}
		String rune = RuneIds.RUNES.get(runeId);
		if (rune == null)
		{
			return null;
		}
		if (guardian)
		{
			return rune + " - GOTR";
		}
		switch (runeId)
		{
			case RuneIds.BLOOD_RUNE:
				return arceuus ? "Blood - Zeah" : "Blood - Mory";
			case RuneIds.SOUL_RUNE:
				return "Soul - Zeah";
			case 4695: // mist
			case 4696: // dust
			case 4698: // mud
			case 4697: // smoke
			case 4694: // steam
			case 4699: // lava
				return rune + " - " + elementalAltar(regionId);
			default:
				return rune;
		}
	}

	/** Combination runes are made at the air, water, earth or fire altar. */
	static String elementalAltar(int regionId)
	{
		switch (regionId)
		{
			case 11339:
				return "Air altar";
			case 10827:
				return "Water altar";
			case 10571:
				return "Earth altar";
			case 10315:
				return "Fire altar";
			default:
				return "area " + regionId;
		}
	}
}
