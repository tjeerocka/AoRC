package com.aorc;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What Runey says, with a personality per account type:
 * respect for ironmen, nerves for hardcores, and trash talk (with spitting) for mains.
 * Big moments (trip done, new fastest trip, new best day) show first for a while;
 * otherwise the message changes every 10 seconds.
 * Messages with "*spits*" make Runey spit.
 */
public class HypeBuddy
{
	static final long ROTATE_MILLIS = 10_000L;
	static final long EVENT_MILLIS = 15_000L;

	private final NumberFormat nf = NumberFormat.getIntegerInstance(Locale.ENGLISH);

	private boolean started;
	private AccountType lastType;
	private int lastTrips;
	private boolean lastBestToday;
	private String event;
	private long eventUntil;
	private int rotate = -1;
	private long nextRotate;
	private String current = "";

	public String message(PanelData d, long now)
	{
		AccountType type = d.accountType == null ? AccountType.MAIN : d.accountType;
		boolean bestToday = d.days > 1 && d.todayRunes > 0 && d.todayRunes >= d.bestRunes;

		if (!started || type != lastType)
		{
			// First message, or the account type is known now: say hello
			current = hello(type);
			nextRotate = now + ROTATE_MILLIS;
		}
		else
		{
			if (d.trips > lastTrips)
			{
				boolean fastest = d.trips > 1 && d.lastTripMillis > 0 && d.lastTripMillis == d.fastestTripMillis;
				String time = d.lastTripMillis > 0 ? PanelData.formatTime(d.lastTripMillis) : null;
				setEvent(fastest ? fastestTrip(type, time) : tripDone(type, time), now);
			}
			if (bestToday && !lastBestToday)
			{
				setEvent(bestDay(type, d.todayRunes), now);
			}
		}
		started = true;
		lastType = type;
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

	// ---- big moments ----

	static String hello(AccountType t)
	{
		switch (t)
		{
			case MAIN:
				return "Ugh, a main. Fine, I'll cheer. *spits*";
			case HARDCORE:
				return "Hardcore! Let's craft runes and NOT die today.";
			case HARDCORE_GROUP:
				return "Hardcore group! Craft safe, the group is watching.";
			case ULTIMATE:
				return "An ultimate ironman! No bank, all heart.";
			case GROUP:
				return "Group iron! Let's feed the group storage!";
			case UNRANKED_GROUP:
				return "Unranked group iron! All vibes, no ranks.";
			default:
				return "An ironman! Respect. Let's craft!";
		}
	}

	static String tripDone(AccountType t, String time)
	{
		String in = time == null ? "" : " in " + time;
		switch (t)
		{
			case MAIN:
				return "Trip done" + in + ". *spits* My grandma crafts faster.";
			case HARDCORE:
			case HARDCORE_GROUP:
				return "Trip done" + in + " and still hardcore!";
			case ULTIMATE:
				return "Trip done" + in + ". No bank, no problem!";
			case GROUP:
			case UNRANKED_GROUP:
				return "Trip done" + in + ". The group thanks you. Maybe.";
			default:
				return "Trip done" + in + ". Iron efficiency!";
		}
	}

	static String fastestTrip(AccountType t, String time)
	{
		String base = "NEW FASTEST TRIP! " + time + "!";
		return t == AccountType.MAIN ? base + " Still slow tho. *spits*" : base + " You're on fire!";
	}

	static String bestDay(AccountType t, int runes)
	{
		String base = "NEW BEST DAY! " + NumberFormat.getIntegerInstance(Locale.ENGLISH).format(runes) + " runes!";
		return t == AccountType.MAIN ? base + " Ok fine, that's actually good." : base + " Legend!";
	}

	// ---- rotating tips ----

	List<String> tips(PanelData d)
	{
		AccountType type = d.accountType == null ? AccountType.MAIN : d.accountType;
		boolean main = type == AccountType.MAIN;
		List<String> t = new ArrayList<>();

		if (d.essenceCharges >= 0 && d.essenceCharges <= 100)
		{
			t.add(main ? "Your blood essence is almost empty. Just buy another, rich boy."
				: "Psst! Your blood essence is almost empty!");
		}
		if (d.currentLevel > 0 && d.currentLevel < d.goalLevel && d.goalLevelMillis > 0)
		{
			String eta = PanelData.formatTime(d.goalLevelMillis);
			t.add(main ? "Level " + d.goalLevel + " in " + eta + ". Or buy a bond and cry."
				: "Level " + d.goalLevel + " in " + eta + ". The grind never stops!");
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
			t.add(main ? nf.format(d.todayRunes) + " runes today. The GE has millions. *spits*"
				: nf.format(d.todayRunes) + " runes today. All yours, no GE!");
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
			t.add(main ? GpFormat.shortGp(d.gpPerHour) + " gp/hr. Still poorer than an iron's pride."
				: GpFormat.shortGp(d.gpPerHour) + " gp/hr of runes you'll never sell. Beautiful.");
		}
		if (d.trips > 0)
		{
			t.add(nf.format(d.trips) + (d.trips == 1 ? " trip" : " trips") + " done. One more?");
		}
		t.addAll(personality(type));
		return t;
	}

	static List<String> personality(AccountType t)
	{
		List<String> p = new ArrayList<>();
		switch (t)
		{
			case MAIN:
				p.add("You could just buy these on the GE. *spits*");
				p.add("Main account detected. Disappointing, but carry on.");
				p.add("Imagine not being an ironman. Couldn't be me.");
				p.add("Craft faster. Your GE offers won't flip themselves.");
				break;
			case HARDCORE:
				p.add("Careful! Don't die to something silly. Not today.");
				p.add("Hardcore status: still alive. For now.");
				p.add("One wrong click and you're a regular iron. No pressure.");
				break;
			case HARDCORE_GROUP:
				p.add("Hardcore group lives: hopefully all of them still.");
				p.add("Don't be the one who loses a life for the group.");
				p.add("Craft safe. The group chat never forgets.");
				break;
			case ULTIMATE:
				p.add("No bank? Where do you even keep all these runes?");
				p.add("Inventory full of runes and pure determination.");
				p.add("UIM life: one inventory, endless grind.");
				break;
			case GROUP:
				p.add("Your group is counting on you. Probably slacking though.");
				p.add("Craft enough for the whole group. They won't thank you.");
				p.add("Group storage is about to eat good.");
				break;
			case UNRANKED_GROUP:
				p.add("Unranked group: no pressure, all vibes.");
				p.add("Craft enough for the whole group. They won't thank you.");
				p.add("Group storage is about to eat good.");
				break;
			default:
				p.add("No GE, no problem. Respect, ironman.");
				p.add("Btw, you're an ironman. Everyone knows. Good.");
				p.add("Self-sufficient king. Every rune is YOURS.");
				break;
		}
		return p;
	}
}
