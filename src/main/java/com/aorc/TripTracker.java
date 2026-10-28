package com.aorc;

/**
 * Trip stats. A trip = one visit to the altar. Crafts less than 60 seconds apart
 * (for example fragments first, then the chiseled blocks) belong to the same trip.
 * Trip time = time from one altar visit to the next.
 */
public class TripTracker
{
	/** Crafts closer than this are the same trip. */
	static final long SAME_TRIP_MILLIS = 60_000L;
	/** Longer than this between altar visits = a break, so the time is not used. */
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
		long groupStart = -1;
		long lastCraft = -1;
		int groupRunes;
	}

	private Saved s = new Saved();

	/** One craft at an altar (all rune types of that click together). */
	public void onCraft(long now, int runes)
	{
		if (runes <= 0)
		{
			return;
		}
		if (s.lastCraft >= 0 && now - s.lastCraft <= SAME_TRIP_MILLIS && now >= s.lastCraft)
		{
			s.groupRunes += runes;
			s.lastCraft = now;
			return;
		}

		// A new altar visit: close the previous trip
		if (s.groupStart >= 0)
		{
			s.trips++;
			s.tripRunes += s.groupRunes;
			s.lastTripRunes = s.groupRunes;
			long dur = now - s.groupStart;
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
		}
		s.groupStart = now;
		s.groupRunes = runes;
		s.lastCraft = now;
	}

	/** Trips including the one in progress. */
	public int getTrips()
	{
		return s.trips + (s.groupStart >= 0 ? 1 : 0);
	}

	public int getAvgRunes()
	{
		int n = getTrips();
		return n == 0 ? 0 : (int) ((s.tripRunes + s.groupRunes) / n);
	}

	/** -1 when there is no finished trip yet. */
	public long getAvgTripMillis()
	{
		return s.timedTrips == 0 ? -1 : s.tripMillis / s.timedTrips;
	}

	public long getFastestMillis()
	{
		return s.fastestMillis;
	}

	/** Runes of the altar visit in progress (or last one). */
	public int getCurrentRunes()
	{
		return s.groupRunes;
	}

	public long getLastTripMillis()
	{
		return s.lastTripMillis;
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
		c.groupStart = s.groupStart;
		c.lastCraft = s.lastCraft;
		c.groupRunes = s.groupRunes;
		return c;
	}

	public void load(Saved saved)
	{
		s = saved == null ? new Saved() : saved;
	}
}
