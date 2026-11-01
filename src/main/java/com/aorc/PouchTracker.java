package com.aorc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Essence pouch wear. The game hides how close a pouch is to decaying, so we count the
 * essence you put in each pouch since its last repair (OSRS Wiki, "Essence pouch"):
 * medium about 270 essence, large 261, giant 120, colossal 320, then it decays.
 * At 75% used (25% left) the pouch needs a repair (Dark Mage via Astral Contact).
 * The small pouch never decays.
 */
public class PouchTracker
{
	/** Warn at this part used (25% left). */
	static final double WARN_AT = 0.75;

	public enum Pouch
	{
		MEDIUM("Medium pouch", 5510, 5511, 270),
		LARGE("Large pouch", 5512, 5513, 261),
		GIANT("Giant pouch", 5514, 5515, 120),
		COLOSSAL("Colossal pouch", 26784, 26786, 320);

		final String name;
		final int itemId;
		final int degradedId;
		final int limit;

		Pouch(String name, int itemId, int degradedId, int limit)
		{
			this.name = name;
			this.itemId = itemId;
			this.degradedId = degradedId;
			this.limit = limit;
		}

		public String getName()
		{
			return name;
		}
	}

	/** Data written to disk between RuneLite sessions. */
	public static class Saved
	{
		Map<String, Integer> stored = new LinkedHashMap<>();
	}

	private final Map<String, Integer> stored = new LinkedHashMap<>();

	/** The pouch for an item ID (normal or decayed), or null. */
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

	/**
	 * Essence put into a pouch.
	 * @return true when this fill crossed 75% used (time to warn)
	 */
	public boolean addFilled(Pouch p, int essence)
	{
		if (p == null || essence <= 0)
		{
			return false;
		}
		boolean before = needsRepair(p);
		stored.merge(p.name(), essence, Integer::sum);
		return !before && needsRepair(p);
	}

	/** The pouch has decayed (game message or decayed item seen): fully used. */
	public void markDecayed(Pouch p)
	{
		if (p != null)
		{
			stored.put(p.name(), Math.max(p.limit, stored.getOrDefault(p.name(), 0)));
		}
	}

	/** Part used since the last repair, 0..1. */
	public double used(Pouch p)
	{
		return p == null ? 0 : Math.min(1.0, stored.getOrDefault(p.name(), 0) / (double) p.limit);
	}

	public boolean needsRepair(Pouch p)
	{
		return used(p) >= WARN_AT;
	}

	/** Any of these pouches needs a repair. */
	public boolean anyNeedsRepair(Iterable<Pouch> pouches)
	{
		for (Pouch p : pouches)
		{
			if (needsRepair(p))
			{
				return true;
			}
		}
		return false;
	}

	/** Any essence counted since the last repair. */
	public boolean hasAny()
	{
		return !stored.isEmpty();
	}

	/** All pouches repaired (the Dark Mage repairs them all at once). */
	public void repaired()
	{
		stored.clear();
	}

	public Saved save()
	{
		Saved s = new Saved();
		s.stored = new LinkedHashMap<>(stored);
		return s;
	}

	public void load(Saved s)
	{
		stored.clear();
		if (s != null && s.stored != null)
		{
			stored.putAll(s.stored);
		}
	}
}
