package com.aorc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the numbers for one runecrafting session.
 * Time is passed in (milliseconds), so this class is easy to test.
 */
public class RuneSession
{
	private final Map<Integer, Integer> runesCrafted = new LinkedHashMap<>();
	private long startMillis = -1;
	private long lastActionMillis = -1;
	private int xpGained;
	private int blocksImbued;
	private int crafts;

	public boolean isStarted()
	{
		return startMillis >= 0;
	}

	private void touch(long now)
	{
		if (startMillis < 0)
		{
			startMillis = now;
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
	 * Time used for "per hour". While active this is start..now.
	 * When idle it stops at the last action, so the rate does not drop while you are away.
	 */
	public long elapsedMillis(long now, long timeoutMillis)
	{
		if (startMillis < 0)
		{
			return 0;
		}
		long end = isIdle(now, timeoutMillis) ? lastActionMillis : now;
		return Math.max(0, end - startMillis);
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
		startMillis = -1;
		lastActionMillis = -1;
		xpGained = 0;
		blocksImbued = 0;
		crafts = 0;
	}
}
