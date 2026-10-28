package com.aorc;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the hype buddy says. Big moments (trip done, new fastest trip, new best day)
 * show first for a while; otherwise the message changes every 10 seconds.
 * No RuneLite code, so it can be tested on its own.
 */
public class HypeBuddy
{
	static final long ROTATE_MILLIS = 10_000L;
	static final long EVENT_MILLIS = 15_000L;

	private final NumberFormat nf = NumberFormat.getIntegerInstance(Locale.ENGLISH);

	private boolean started;
	private int lastTrips;
	private boolean lastBestToday;
	private String event;
	private long eventUntil;
	private int rotate = -1;
	private long nextRotate;
	private String current = "Hi! I'm Runey. Let's craft some runes!";

	public String message(PanelData d, long now)
	{
		boolean bestToday = d.days > 1 && d.todayRunes > 0 && d.todayRunes >= d.bestRunes;

		if (started)
		{
			if (d.trips > lastTrips)
			{
				boolean fastest = d.trips > 1 && d.lastTripMillis > 0 && d.lastTripMillis == d.fastestTripMillis;
				if (fastest)
				{
					setEvent("New fastest trip! " + PanelData.formatTime(d.lastTripMillis) + "! You're on fire!", now);
				}
				else if (d.lastTripMillis > 0)
				{
					setEvent("Trip done! " + PanelData.formatTime(d.lastTripMillis) + ". Nice one!", now);
				}
				else
				{
					setEvent("Trip done! Nice one!", now);
				}
			}
			if (bestToday && !lastBestToday)
			{
				setEvent("NEW BEST DAY! " + nf.format(d.todayRunes) + " runes! Legend!", now);
			}
		}
		if (!started)
		{
			nextRotate = now + ROTATE_MILLIS; // show the hello first
		}
		started = true;
		lastTrips = d.trips;
		lastBestToday = bestToday;

		if (event != null && now < eventUntil)
		{
			return event;
		}
		event = null;

		if (now >= nextRotate)
		{
			List<String> tips = tips(d);
			rotate++;
			current = tips.get(rotate % tips.size());
			nextRotate = now + ROTATE_MILLIS;
		}
		return current;
	}

	private void setEvent(String text, long now)
	{
		event = text;
		eventUntil = now + EVENT_MILLIS;
	}

	List<String> tips(PanelData d)
	{
		List<String> t = new ArrayList<>();

		if (d.essenceCharges >= 0 && d.essenceCharges <= 100)
		{
			t.add("Psst! Your blood essence is almost empty!");
		}
		if (d.currentLevel > 0 && d.currentLevel < d.goalLevel && d.goalLevelMillis > 0)
		{
			t.add("Level " + d.goalLevel + " in " + PanelData.formatTime(d.goalLevelMillis) + ". Keep going!");
		}
		if (d.currentLevel > 0 && d.currentLevel >= d.goalLevel)
		{
			t.add("Level " + d.goalLevel + " reached! You did it!");
		}
		if (d.goalBlood > 0 && d.bloodLeft > 0)
		{
			String eta = d.goalBloodMillis > 0 ? " About " + PanelData.formatTime(d.goalBloodMillis) + "." : "";
			t.add(nf.format(d.bloodLeft) + " blood runes to go!" + eta);
		}
		if (d.todayRunes > 0)
		{
			t.add(nf.format(d.todayRunes) + " runes today. You're doing great!");
		}
		if (d.days > 1 && d.bestRunes > d.todayRunes && d.todayRunes > 0)
		{
			t.add("Only " + nf.format(d.bestRunes - d.todayRunes) + " runes to beat your best day!");
		}
		if (d.fastestTripMillis > 0 && d.currentTripMillis > 0 && d.currentTripMillis < d.fastestTripMillis
			&& d.currentTripMillis > d.fastestTripMillis / 2)
		{
			t.add("On pace for a new fastest trip! Go go go!");
		}
		if (d.gpPerHour > 0)
		{
			t.add(GpFormat.shortGp(d.gpPerHour) + " gp per hour. Cha-ching!");
		}
		if (d.trips > 0)
		{
			t.add(nf.format(d.trips) + (d.trips == 1 ? " trip" : " trips") + " done. One more?");
		}
		t.add("You're great! Keep crafting!");
		t.add("Every rune counts. Let's go!");
		return t;
	}
}
