package com.aorc;

/**
 * Trip stats.
 * A trip ends when the inventory has no essence left (no dense/dark blocks, fragments,
 * rune, pure, daeyalt or guardian essence) - right after the last craft at the altar.
 * Essence pouches: emptying a pouch and crafting again at the altar within 60 seconds is
 * still the same trip, so the trip only really ends after the LAST craft. That is why a
 * finished trip is counted 60 seconds after its last craft (see tick), and the new trip's
 * timer starts at that last craft.
 * Trip time = from the end of one trip to the end of the next.
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
		boolean pending;         // a trip ended but waited for pouch crafts when saved
		long pendingTripMillis = -1;   // its time (-1 = not timed)
	}

	private Saved s = new Saved();
	private long pausedAt = -1;   // while paused the trip clock stands still
	// end of the trip that just ended (last craft), waiting 60 s for pouch crafts; -1 = none
	private long pendingEnd = -1;
	// loaded from disk with a trip that had ended: count it on the next tick
	private boolean finishOnTick;

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
			if (pendingEnd >= 0)
			{
				pendingEnd += Math.max(0, now - pausedAt);
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

	/** The inventory has no essence left (after a craft at the altar, or banked). */
	public void onEssenceEmpty(long now)
	{
		if (pendingEnd >= 0)
		{
			if (now - pendingEnd < SAME_TRIP_MILLIS)
			{
				// Crafted again from a pouch: still the same trip, it now ends here
				pendingEnd = now;
				return;
			}
			finish(pendingEnd);
		}
		if (s.currentRunes <= 0)
		{
			// Nothing crafted (for example essence deposited in the bank): no trip
			s.tripStart = now;
			return;
		}
		// The trip ends here, unless you empty a pouch and craft again within 60 seconds
		pendingEnd = now;
	}

	/** Call every game tick: a trip that ended over 60 seconds ago (no pouch crafts) is counted. */
	public void tick(long now)
	{
		if (pendingEnd >= 0 && pausedAt < 0 && (finishOnTick || now - pendingEnd >= SAME_TRIP_MILLIS))
		{
			finish(pendingEnd);
		}
	}

	/** Count the trip that ended at 'end'; the next trip starts there. */
	private void finish(long end)
	{
		pendingEnd = -1;
		finishOnTick = false;
		s.trips++;
		s.tripRunes += s.currentRunes;
		s.lastTripRunes = s.currentRunes;
		// half trip (its start was not seen): counts with its runes, but without a time
		long dur = s.tripStart < 0 ? -1 : end - s.tripStart;
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
		s.tripStart = end;
		s.currentRunes = 0;
	}

	/** Time of the trip in progress, -1 when no trip has started. */
	public long getCurrentMillis(long now)
	{
		long end = pausedAt >= 0 ? pausedAt : now;
		// a trip just ended: the new trip's timer runs from its last craft
		long start = pendingEnd >= 0 ? pendingEnd : s.tripStart;
		return start < 0 ? -1 : Math.max(0, end - start);
	}

	/** Runes crafted in the trip in progress. */
	public int getCurrentRunes()
	{
		return pendingEnd >= 0 ? 0 : s.currentRunes;
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
		pendingEnd = -1;
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
		if (pendingEnd >= 0)
		{
			c.pending = true;
			c.pendingTripMillis = s.tripStart < 0 ? -1 : pendingEnd - s.tripStart;
		}
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
		pendingEnd = -1;
		finishOnTick = false;
		if (s.pending)
		{
			// a trip had ended (waiting for pouch crafts): it is counted on the next tick,
			// and the new trip continues from it (the closed time does not count)
			pendingEnd = now - Math.max(0, s.currentMillis);
			finishOnTick = true;
			s.tripStart = s.pendingTripMillis < 0 ? -1 : pendingEnd - s.pendingTripMillis;
			s.pending = false;
			return;
		}
		s.tripStart = s.tripStart < 0 && s.currentRunes == 0 && s.currentMillis == 0
			? -1
			: now - Math.max(0, s.currentMillis);
	}
}
