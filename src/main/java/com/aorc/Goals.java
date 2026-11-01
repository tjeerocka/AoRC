package com.aorc;

/** Time-to-goal math (level goal and rune goal). */
public final class Goals
{
	private Goals()
	{
	}

	/** Total XP needed for a level (the normal OSRS table). Level 99 = 13,034,431. */
	public static int xpForLevel(int level)
	{
		level = Math.max(1, Math.min(126, level));
		double points = 0;
		for (int l = 1; l < level; l++)
		{
			points += Math.floor(l + 300.0 * Math.pow(2, l / 7.0));
		}
		return (int) Math.floor(points / 4);
	}

	/** How far from start to goal (0..1). */
	public static double progress(long now, long start, long goal)
	{
		if (now >= goal)
		{
			return 1;
		}
		if (goal <= start)
		{
			return 0;
		}
		return Math.max(0, Math.min(1, (now - start) / (double) (goal - start)));
	}

	/** 0 = goal reached, -1 = no rate yet. */
	public static long millisToGoal(long remaining, long perHour)
	{
		if (remaining <= 0)
		{
			return 0;
		}
		if (perHour <= 0)
		{
			return -1;
		}
		return remaining * 3_600_000L / perHour;
	}
}
