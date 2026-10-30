package com.aorc;

/**
 * Trip stats.
 * A trip ends when the inventory has no essence left (no dense/dark blocks, fragments,
 * rune, pure or daeyalt essence) - that is right after the last craft at the altar.
 * Trip time = from one "essence empty" moment to the next.
 * A half trip (its start was not seen) still counts as a trip with its runes,
 * but without a time (it is left out of the average and fastest time).
 * After a restart in the middle of a trip, the same trip continues (closed time not counted).
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
		long currentMillis;      // time of the trip in progress when saved (to continue after a restart)
	}

	private Saved s = new Saved();
	private long pausedAt = -1;   // while paused the trip clock stands still

	/** Stop the trip clock (the trip in progress waits). */
	public void pause(long now)
	{
		if (pausedAt < 0)
		{
			pausedAt = now;
		}
	}

	/** Start the trip clock again; the paused time is not part of the trip. */
	public void resume(long now)
	{
		if (pausedAt >= 0)
		{
			if (s.tripStart >= 0)
			{
				s.tripStart += Math.max(0, now - pausedAt);
			}
			pausedAt = -1;
		}
	}

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
		if (s.currentRunes > 0)
		{
			// a trip with runes was still open: it ended (essence is gone)
			onEssenceEmpty(now);
			return;
		}
		s.tripStart = now;
		s.currentRunes = 0;
	}

	/** The inventory has no essence left (after the last craft of a trip). */
	public void onEssenceEmpty(long now)
	{
		if (s.tripStart < 0)
		{
			// Half trip (start not seen): counts with its runes, but without a time
			if (s.currentRunes > 0)
			{
				s.trips++;
				s.tripRunes += s.currentRunes;
				s.lastTripRunes = s.currentRunes;
				s.lastTripMillis = -1;
			}
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
		long end = pausedAt >= 0 ? pausedAt : now;
		return s.tripStart < 0 ? -1 : Math.max(0, end - s.tripStart);
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
		pausedAt = -1;
	}

	public Saved save()
	{
		return save(System.currentTimeMillis());
	}

	public Saved save(long now)
	{
		Saved c = new Saved();
		c.currentMillis = Math.max(0, getCurrentMillis(now));
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
		load(saved, System.currentTimeMillis());
	}

	/** Continue the trip in progress; the time the client was closed does not count. */
	public void load(Saved saved, long now)
	{
		s = saved == null ? new Saved() : saved;
		pausedAt = -1;
		s.tripStart = s.tripStart < 0 && s.currentRunes == 0 && s.currentMillis == 0
			? -1
			: now - Math.max(0, s.currentMillis);
	}
}
