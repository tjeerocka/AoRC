package com.aorc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Running totals that are reset on their own (not with the per-hour numbers):
 * - runes crafted per type ("Blood runes")
 * - bonus runes (from Raiments, lantern, blood essence, diary)
 *
 * Bonus runes are worked out per craft with the bonus % you had at that moment,
 * so swapping gear later does not change old numbers.
 */
public class RuneTotals
{
	/** Data written to disk between RuneLite sessions. */
	public static class Saved
	{
		Map<Integer, Integer> runes = new LinkedHashMap<>();
		double bonusRunes;
	}

	private final Map<Integer, Integer> runes = new LinkedHashMap<>();
	private double bonusRunes;

	/** One craft at an altar: amount = runes you really got, bonusPercent = bonus at that moment. */
	public void addCraft(int itemId, int amount, int bonusPercent)
	{
		if (amount <= 0)
		{
			return;
		}
		runes.merge(itemId, amount, Integer::sum);
		if (bonusPercent > 0)
		{
			double base = amount / (1.0 + bonusPercent / 100.0);
			bonusRunes += amount - base;
		}
	}

	public int getRunes(int itemId)
	{
		return runes.getOrDefault(itemId, 0);
	}

	public Map<Integer, Integer> getRunes()
	{
		return Collections.unmodifiableMap(runes);
	}

	public int getBonusRunes()
	{
		return (int) Math.round(bonusRunes);
	}

	public void resetRunes()
	{
		runes.clear();
	}

	public void resetBonus()
	{
		bonusRunes = 0;
	}

	public Saved save()
	{
		Saved s = new Saved();
		s.runes = new LinkedHashMap<>(runes);
		s.bonusRunes = bonusRunes;
		return s;
	}

	public void load(Saved s)
	{
		runes.clear();
		bonusRunes = 0;
		if (s == null)
		{
			return;
		}
		if (s.runes != null)
		{
			runes.putAll(s.runes);
		}
		bonusRunes = Math.max(0, s.bonusRunes);
	}
}
