package com.aorc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the numbers for one runecrafting session.
 * Time is passed in (milliseconds), so this class is easy to test.
 *
 * Active time is counted in parts ("segments"). A segment ends when you are idle
 * longer than the idle timeout, or when the session is saved and loaded again.
 * So time spent idle or logged out does not count for "per hour".
 */
public class RuneSession
{
	/** Data written to disk between RuneLite sessions. */
	public static class Saved
	{
		Map<Integer, Integer> runes = new LinkedHashMap<>();
		int xp;
		int blocks;
		int crafts;
		long activeMillis;
	}

	private final Map<Integer, Integer> runesCrafted = new LinkedHashMap<>();
	private long bankedMillis;          // active time from earlier segments
	private long segmentStart = -1;     // start of the current segment
	private long lastActionMillis = -1;
	private long idleTimeoutMillis = 5 * 60_000L;
	private int xpGained;
	private int blocksImbued;
	private int crafts;

	public void setIdleTimeoutMillis(long idleTimeoutMillis)
	{
		this.idleTimeoutMillis = idleTimeoutMillis;
	}

	public boolean isStarted()
	{
		return segmentStart >= 0 || bankedMillis > 0 || crafts > 0 || xpGained > 0 || !runesCrafted.isEmpty();
	}

	private void touch(long now)
	{
		if (segmentStart < 0)
		{
			segmentStart = now;
		}
		else if (lastActionMillis >= 0 && now - lastActionMillis > idleTimeoutMillis)
		{
			// Back after a break: keep the old active time, do not count the break.
			bankedMillis += lastActionMillis - segmentStart;
			segmentStart = now;
		}
		lastActionMillis = Math.max(lastActionMillis, now);
	}

	/** Called when runes of one type were crafted at an altar. */
	public void addRunes(int itemId, int amount, long now)
	{
		if (amount <= 0)
		{
			return;
		}
		touch(now);
		runesCrafted.merge(itemId, amount, Integer::sum);
	}

	/** Called once per altar click that made runes (one "craft"). */
	public void addCraft(long now)
	{
		touch(now);
		crafts++;
	}

	public void addXp(int xp, long now)
	{
		if (xp <= 0)
		{
			return;
		}
		touch(now);
		xpGained += xp;
	}

	public void addBlocksImbued(int amount, long now)
	{
		if (amount <= 0)
		{
			return;
		}
		touch(now);
		blocksImbued += amount;
	}

	/** True when nothing happened for longer than the timeout. */
	public boolean isIdle(long now, long timeoutMillis)
	{
		return lastActionMillis >= 0 && now - lastActionMillis > timeoutMillis;
	}

	/**
	 * Active time used for "per hour". While active the current segment runs until now.
	 * When idle it stops at the last action, so the rate does not drop while you are away.
	 */
	public long elapsedMillis(long now, long timeoutMillis)
	{
		long total = bankedMillis;
		if (segmentStart >= 0)
		{
			long end = isIdle(now, timeoutMillis) ? lastActionMillis : now;
			total += Math.max(0, end - segmentStart);
		}
		return total;
	}

	public static int perHour(long amount, long elapsedMillis)
	{
		if (elapsedMillis <= 0)
		{
			return 0;
		}
		return (int) (amount * 3_600_000L / elapsedMillis);
	}

	public int getRunes(int itemId)
	{
		return runesCrafted.getOrDefault(itemId, 0);
	}

	public int getTotalRunes()
	{
		int sum = 0;
		for (int v : runesCrafted.values())
		{
			sum += v;
		}
		return sum;
	}

	public Map<Integer, Integer> getRunesCrafted()
	{
		return Collections.unmodifiableMap(runesCrafted);
	}

	public int getXpGained()
	{
		return xpGained;
	}

	public int getBlocksImbued()
	{
		return blocksImbued;
	}

	public int getCrafts()
	{
		return crafts;
	}

	public void reset()
	{
		runesCrafted.clear();
		bankedMillis = 0;
		segmentStart = -1;
		lastActionMillis = -1;
		xpGained = 0;
		blocksImbued = 0;
		crafts = 0;
	}

	/** Snapshot for saving. */
	public Saved save(long now)
	{
		Saved s = new Saved();
		s.runes = new LinkedHashMap<>(runesCrafted);
		s.xp = xpGained;
		s.blocks = blocksImbued;
		s.crafts = crafts;
		s.activeMillis = elapsedMillis(now, idleTimeoutMillis);
		return s;
	}

	/** Restore a saved session. The timer continues at the next action. */
	public void load(Saved s)
	{
		reset();
		if (s == null)
		{
			return;
		}
		if (s.runes != null)
		{
			runesCrafted.putAll(s.runes);
		}
		xpGained = s.xp;
		blocksImbued = s.blocks;
		crafts = s.crafts;
		bankedMillis = Math.max(0, s.activeMillis);
	}
}
