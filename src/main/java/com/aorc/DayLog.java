package com.aorc;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * History per day: runes crafted and time spent (mining dense essence, chiseling, crafting).
 * Dates are "yyyy-MM-dd" strings in the player's own time zone.
 * Time only counts between actions that are closer than the idle timeout, so breaks do not count.
 */
public class DayLog
{
	/** Keep about one year. */
	static final int MAX_DAYS = 400;

	public static class Day
	{
		int runes;
		int bloodRunes;
		int xp;
		long activeMillis;

		public int getRunes()
		{
			return runes;
		}

		public int getBloodRunes()
		{
			return bloodRunes;
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
		String best = null;
		int bestRunes = 0;
		for (Map.Entry<String, Day> e : days.entrySet())
		{
			if (e.getValue().runes > bestRunes)
			{
				bestRunes = e.getValue().runes;
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
					days.put(e.getKey(), e.getValue());
				}
			}
		}
	}
}
