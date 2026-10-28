package com.aorc;

/**
 * Trip stats.
 * A trip ends when the inventory has no essence left (no dense/dark blocks, fragments,
 * rune, pure or daeyalt essence) - that is right after the last craft at the altar.
 * Trip time = from one "essence empty" moment to the next.
 * Runes crafted before the first "empty" moment are a half trip and are not counted.
 */
public class TripTracker
{
	/** Empty again within this time (refilling from pouches) = same trip. */
	static final long SAME_TRIP_MILLIS = 60_000L;
	/** Longer than this = a break, so the trip time is not used. */
	static final long MAX_TRIP_MILLIS = 30 * 60_000L;

	/** Data written to disk between RuneLite sessions. */
	public static class Saved
	{
		int trips;
		long tripRunes;
		int timedTrips;
		long tripMillis;
		long fastestMillis = -1;
		int lastTripRunes;
		long lastTripMillis = -1;
		long tripStart = -1;     // last "essence empty" moment, -1 = not started yet
		int currentRunes;        // runes in the trip in progress
	}

	private Saved s = new Saved();

	/** Runes from one craft (all rune types together). */
	public void onCraft(int runes)
	{
		if (runes > 0)
		{
			s.currentRunes += runes;
		}
	}

	/** The inventory had no essence when we first saw it (login): a full trip can start now. */
	public void onStartEmpty(long now)
	{
		if (s.tripStart < 0)
		{
			s.tripStart = now;
			s.currentRunes = 0;
		}
	}

	/** The inventory has no essence left (after the last craft of a trip). */
	public void onEssenceEmpty(long now)
	{
		if (s.tripStart < 0)
		{
			// First empty moment: the trip before this was only partly seen
			s.tripStart = now;
			s.currentRunes = 0;
			return;
		}
		if (now - s.tripStart < SAME_TRIP_MILLIS)
		{
			// Emptied again quickly (pouches): still the same trip
			return;
		}
		if (s.currentRunes <= 0)
		{
			// Nothing crafted (for example essence deposited in the bank): no trip
			s.tripStart = now;
			return;
		}

		s.trips++;
		s.tripRunes += s.currentRunes;
		s.lastTripRunes = s.currentRunes;
		long dur = now - s.tripStart;
		if (dur > 0 && dur <= MAX_TRIP_MILLIS)
		{
			s.timedTrips++;
			s.tripMillis += dur;
			s.lastTripMillis = dur;
			if (s.fastestMillis < 0 || dur < s.fastestMillis)
			{
				s.fastestMillis = dur;
			}
		}
		else
		{
			s.lastTripMillis = -1;
		}
		s.tripStart = now;
		s.currentRunes = 0;
	}

	/** Time of the trip in progress, -1 when no trip has started. */
	public long getCurrentMillis(long now)
	{
		return s.tripStart < 0 ? -1 : Math.max(0, now - s.tripStart);
	}

	/** Runes crafted in the trip in progress. */
	public int getCurrentRunes()
	{
		return s.currentRunes;
	}

	/** Finished trips. */
	public int getTrips()
	{
		return s.trips;
	}

	public int getAvgRunes()
	{
		return s.trips == 0 ? 0 : (int) (s.tripRunes / s.trips);
	}

	/** -1 when there is no timed trip yet. */
	public long getAvgTripMillis()
	{
		return s.timedTrips == 0 ? -1 : s.tripMillis / s.timedTrips;
	}

	public long getFastestMillis()
	{
		return s.fastestMillis;
	}

	public long getLastTripMillis()
	{
		return s.lastTripMillis;
	}

	public int getLastTripRunes()
	{
		return s.lastTripRunes;
	}

	public void reset()
	{
		s = new Saved();
	}

	public Saved save()
	{
		Saved c = new Saved();
		c.trips = s.trips;
		c.tripRunes = s.tripRunes;
		c.timedTrips = s.timedTrips;
		c.tripMillis = s.tripMillis;
		c.fastestMillis = s.fastestMillis;
		c.lastTripRunes = s.lastTripRunes;
		c.lastTripMillis = s.lastTripMillis;
		c.tripStart = s.tripStart;
		c.currentRunes = s.currentRunes;
		return c;
	}

	public void load(Saved saved)
	{
		s = saved == null ? new Saved() : saved;
		// Time while the client was closed must not count: start fresh at the next empty moment
		s.tripStart = -1;
		s.currentRunes = 0;
	}
}
