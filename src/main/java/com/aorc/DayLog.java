package com.aorc;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * History per day: runes crafted and time spent (mining dense essence, chiseling, crafting).
 * Dates are "yyyy-MM-dd" strings in the player's own time zone.
 * Time only counts between actions that are closer than the idle timeout, so breaks do not count.
 */
public class DayLog
{
	/** Key for runes saved before the rune types were kept (only blood was known). */
	public static final int OTHER = -1;

	/** Keep about one year. */
	static final int MAX_DAYS = 400;

	public static class Day
	{
		int runes;
		int bloodRunes;
		int xp;
		long activeMillis;
		// runes per rune type (item ID); OTHER = runes from before types were saved
		Map<Integer, Integer> types = new LinkedHashMap<>();

		/** Runes of one type (item ID), or all runes for 0. */
		public int runesOf(int itemId)
		{
			if (itemId == 0)
			{
				return runes;
			}
			Integer n = types == null ? null : types.get(itemId);
			return n == null ? 0 : n;
		}

		/** Runes per rune type (item ID). */
		public Map<Integer, Integer> getTypes()
		{
			return types == null ? Collections.emptyMap() : Collections.unmodifiableMap(types);
		}

		public int getRunes()
		{
			return runes;
		}

		public int getXp()
		{
			return xp;
		}

		public long getActiveMillis()
		{
			return activeMillis;
		}
	}

	/** Data written to disk between RuneLite sessions. */
	public static class Saved
	{
		Map<String, Day> days = new TreeMap<>();
	}

	private final TreeMap<String, Day> days = new TreeMap<>();
	private long lastActivity = -1;

	private Day day(String date)
	{
		return days.computeIfAbsent(date, d -> new Day());
	}

	/** Any runecrafting action (mining dense, chiseling, imbuing, crafting). */
	public void activity(String date, long now, long idleTimeoutMillis)
	{
		if (lastActivity >= 0 && now > lastActivity && now - lastActivity <= idleTimeoutMillis)
		{
			day(date).activeMillis += now - lastActivity;
		}
		lastActivity = Math.max(lastActivity, now);
		trim();
	}

	public void addRunes(String date, int itemId, int amount)
	{
		if (amount <= 0)
		{
			return;
		}
		Day d = day(date);
		d.runes += amount;
		if (d.types == null)
		{
			d.types = new LinkedHashMap<>();
		}
		d.types.merge(itemId, amount, Integer::sum);
		if (itemId == RuneIds.BLOOD_RUNE)
		{
			d.bloodRunes += amount;
		}
	}

	public void addXp(String date, int xp)
	{
		if (xp > 0)
		{
			day(date).xp += xp;
		}
	}

	/**
	 * Time since the last action that is not saved yet (for a live timer).
	 * 0 when idle longer than the timeout, or paused.
	 */
	public long liveMillis(long now, long idleTimeoutMillis)
	{
		if (lastActivity < 0 || now <= lastActivity || now - lastActivity > idleTimeoutMillis)
		{
			return 0;
		}
		return now - lastActivity;
	}

	/**
	 * Runes saved before the rune types were kept (also part of a day, when the update came
	 * in the middle of it): blood runes were always known, the rest becomes "other".
	 */
	static void fillMissingTypes(Day d)
	{
		if (d.types == null)
		{
			d.types = new LinkedHashMap<>();
		}
		int known = 0;
		for (int n : d.types.values())
		{
			known += n;
		}
		int missing = d.runes - known;
		if (missing <= 0)
		{
			return;
		}
		int bloodMissing = Math.min(missing, Math.max(0, d.bloodRunes - d.types.getOrDefault(RuneIds.BLOOD_RUNE, 0)));
		if (bloodMissing > 0)
		{
			d.types.merge(RuneIds.BLOOD_RUNE, bloodMissing, Integer::sum);
		}
		if (missing - bloodMissing > 0)
		{
			d.types.merge(OTHER, missing - bloodMissing, Integer::sum);
		}
	}

	/** Days on which runes of this type (0 = any) were crafted. */
	public int dayCount(int itemId)
	{
		int n = 0;
		for (Day d : days.values())
		{
			if (d.runesOf(itemId) > 0)
			{
				n++;
			}
		}
		return n;
	}

	/** Stop the running time (for example after logging out). */
	public void pause()
	{
		lastActivity = -1;
	}

	public Day get(String date)
	{
		Day d = days.get(date);
		return d != null ? d : new Day();
	}

	/** Date with the most runes crafted, or null if there is no history. */
	public String bestDate()
	{
		return bestDate(0);
	}

	/** Date with the most runes of this type (0 = all runes), or null. */
	public String bestDate(int itemId)
	{
		String best = null;
		int bestRunes = 0;
		for (Map.Entry<String, Day> e : days.entrySet())
		{
			int n = e.getValue().runesOf(itemId);
			if (n > bestRunes)
			{
				bestRunes = n;
				best = e.getKey();
			}
		}
		return best;
	}

	public Day total()
	{
		Day t = new Day();
		for (Day d : days.values())
		{
			t.runes += d.runes;
			t.bloodRunes += d.bloodRunes;
			t.xp += d.xp;
			t.activeMillis += d.activeMillis;
			if (d.types != null)
			{
				for (Map.Entry<Integer, Integer> e : d.types.entrySet())
				{
					t.types.merge(e.getKey(), e.getValue(), Integer::sum);
				}
			}
		}
		return t;
	}

	public int dayCount()
	{
		return days.size();
	}

	public void clear()
	{
		days.clear();
		lastActivity = -1;
	}

	private void trim()
	{
		Iterator<String> it = days.keySet().iterator();
		while (days.size() > MAX_DAYS && it.hasNext())
		{
			it.next();
			it.remove();
		}
	}

	public Saved save()
	{
		Saved s = new Saved();
		s.days = new TreeMap<>(days);
		return s;
	}

	public void load(Saved s)
	{
		clear();
		if (s != null && s.days != null)
		{
			for (Map.Entry<String, Day> e : s.days.entrySet())
			{
				if (e.getKey() != null && e.getValue() != null)
				{
					Day d = e.getValue();
					fillMissingTypes(d);
					days.put(e.getKey(), d);
				}
			}
		}
	}
}
