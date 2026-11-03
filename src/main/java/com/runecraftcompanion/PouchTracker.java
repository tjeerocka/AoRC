package com.runecraftcompanion;

/**
 * Essence pouch durability, read from the game itself: the game keeps a hidden wear counter
 * per pouch in a varp / varbit (the same numbers RuneLite's own Runecraft plugin shows on
 * the pouch). Durability = fills left before the pouch decays, like "3/12".
 * The breakpoints are the game's own (client scripts 3670, 3671, 3695, 4592).
 * The small pouch never decays.
 */
public final class PouchTracker
{
	/** Warn at this durability (3/12). */
	static final int WARN_FILLS_LEFT = 3;

	public enum Pouch
	{
		// wear counter: capacity from that value on (highest first); then the normal capacity
		MEDIUM("Medium pouch", 5510, 5511, 6, new int[]{800, 0, 400, 3}),
		LARGE("Large pouch", 5512, 5513, 9, new int[]{1000, 0, 800, 3, 600, 5, 400, 7}),
		GIANT("Giant pouch", 5514, 5515, 12, new int[]{1200, 0, 1000, 3, 800, 5, 600, 6, 400, 7, 300, 8, 200, 9}),
		COLOSSAL("Colossal pouch", 26784, 26786, 40,
			new int[]{1020, 0, 1015, 5, 995, 10, 950, 15, 870, 20, 745, 25, 565, 30, 320, 35});

		final String name;
		final int itemId;
		final int degradedId;
		final int maxFill;
		final int[] levels;

		Pouch(String name, int itemId, int degradedId, int maxFill, int[] levels)
		{
			this.name = name;
			this.itemId = itemId;
			this.degradedId = degradedId;
			this.maxFill = maxFill;
			this.levels = levels;
		}

		public String getName()
		{
			return name;
		}
	}

	private PouchTracker()
	{
	}

	/** The pouch for an item ID (normal or decayed), or null (the small pouch never decays). */
	public static Pouch of(int itemId)
	{
		for (Pouch p : Pouch.values())
		{
			if (p.itemId == itemId || p.degradedId == itemId)
			{
				return p;
			}
		}
		return null;
	}

	public static boolean isDegraded(int itemId)
	{
		Pouch p = of(itemId);
		return p != null && p.degradedId == itemId;
	}

	/** Essence the pouch holds at this wear (colossal: less at lower Runecraft levels). */
	static int capacity(Pouch p, int wear, int runecraftLevel)
	{
		int limit = p.maxFill;
		for (int i = 0; i < p.levels.length; i += 2)
		{
			if (wear >= p.levels[i])
			{
				limit = p.levels[i + 1];
				break;
			}
		}
		if (limit > 0 && p == Pouch.COLOSSAL)
		{
			int scaledMax = runecraftLevel >= 85 ? 40 : runecraftLevel >= 75 ? 27 : runecraftLevel >= 50 ? 16 : 8;
			limit = Math.max(1, limit * scaledMax / 40);
		}
		return limit;
	}

	/** Durability: full fills left before the pouch decays (holds less) again. */
	public static int fillsLeft(Pouch p, int wear, int runecraftLevel)
	{
		int breakpoint = wear;
		for (int i = p.levels.length - 2; i >= 0; i -= 2)
		{
			if (wear < p.levels[i])
			{
				breakpoint = p.levels[i];
				break;
			}
		}
		int remaining = breakpoint - wear;
		// colossal: 1 wear per essence; the others: 0-3 wear per essence (this fit is RuneLite's,
		// it matches the 99th percentile, so it is on the safe side)
		int essence = p == Pouch.COLOSSAL ? remaining : (int) Math.ceil(.4 * Math.pow(remaining, 1.07));
		int limit = capacity(p, wear, runecraftLevel);
		return limit <= 0 ? 0 : (essence + limit - 1) / limit;
	}

	/** Durability of a freshly repaired pouch (12 for a colossal pouch at level 75-84). */
	public static int fullFills(Pouch p, int runecraftLevel)
	{
		return fillsLeft(p, 0, runecraftLevel);
	}

	/** Time to repair: 3 fills left or less, or already decayed. */
	public static boolean needsRepair(int fillsLeft, boolean degraded)
	{
		return degraded || fillsLeft <= WARN_FILLS_LEFT;
	}
}
