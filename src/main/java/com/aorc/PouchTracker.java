package com.aorc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Essence pouch wear, in uses (like "3/12 uses left"). The game hides how close a pouch is
 * to decaying, so we count each use (every time essence comes out of the pouch) since its
 * last repair. A pouch lasts about this much essence (OSRS Wiki, "Essence pouch"):
 * medium 270, large 261, giant 120, colossal 320. Uses in total = that / what the pouch holds
 * (colossal at 27 essence: 320 / 27 = 12 uses). Warning at 3 uses left (25% left).
 * The small pouch never decays.
 */
public class PouchTracker
{
	/** Warn at this many uses left (3/12 = 25% left). */
	static final int WARN_USES_LEFT = 3;

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
		Map<String, Integer> uses = new LinkedHashMap<>();       // uses since the last repair
		Map<String, Integer> capacity = new LinkedHashMap<>();   // most essence seen in one use
		Map<String, Boolean> decayed = new LinkedHashMap<>();
	}

	private final Map<String, Integer> uses = new LinkedHashMap<>();
	private final Map<String, Integer> capacity = new LinkedHashMap<>();
	private final Map<String, Boolean> decayed = new LinkedHashMap<>();

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
	 * One use: this much essence came out of the pouch.
	 * @return true when this use reached 3 uses left (time to warn, once)
	 */
	public boolean addUse(Pouch p, int essence)
	{
		if (p == null || essence <= 0)
		{
			return false;
		}
		boolean before = needsRepair(p);
		capacity.merge(p.name(), essence, Math::max);
		uses.merge(p.name(), 1, Integer::sum);
		return !before && needsRepair(p);
	}

	/** Uses in total between repairs (12 for a colossal pouch holding 27). */
	public int totalUses(Pouch p)
	{
		int cap = capacity.getOrDefault(p.name(), 0);
		if (cap <= 0)
		{
			cap = p == Pouch.COLOSSAL ? 40 : p == Pouch.GIANT ? 12 : p == Pouch.LARGE ? 9 : 6;
		}
		return Math.max(1, (int) Math.ceil(p.limit / (double) cap));
	}

	public int usesLeft(Pouch p)
	{
		if (Boolean.TRUE.equals(decayed.get(p.name())))
		{
			return 0;
		}
		return Math.max(0, totalUses(p) - uses.getOrDefault(p.name(), 0));
	}

	/** Part used since the last repair, 0..1. */
	public double used(Pouch p)
	{
		return p == null ? 0 : 1.0 - usesLeft(p) / (double) totalUses(p);
	}

	public boolean needsRepair(Pouch p)
	{
		return p != null && usesLeft(p) <= WARN_USES_LEFT;
	}

	/** The pouch has decayed (game message or decayed item seen). */
	public void markDecayed(Pouch p)
	{
		if (p != null)
		{
			decayed.put(p.name(), true);
		}
	}

	/** For the test button: this pouch is at 3 uses left (until the next repair). */
	public void markNeedsRepair(Pouch p)
	{
		if (p != null)
		{
			uses.put(p.name(), Math.max(uses.getOrDefault(p.name(), 0), totalUses(p) - WARN_USES_LEFT));
		}
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

	/** Any use counted since the last repair. */
	public boolean hasAny()
	{
		return !uses.isEmpty() || !decayed.isEmpty();
	}

	/** All pouches repaired (the Dark Mage repairs them all at once). What they hold stays. */
	public void repaired()
	{
		uses.clear();
		decayed.clear();
	}

	public Saved save()
	{
		Saved s = new Saved();
		s.uses = new LinkedHashMap<>(uses);
		s.capacity = new LinkedHashMap<>(capacity);
		s.decayed = new LinkedHashMap<>(decayed);
		return s;
	}

	public void load(Saved s)
	{
		uses.clear();
		capacity.clear();
		decayed.clear();
		if (s != null)
		{
			if (s.uses != null)
			{
				uses.putAll(s.uses);
			}
			if (s.capacity != null)
			{
				capacity.putAll(s.capacity);
			}
			if (s.decayed != null)
			{
				decayed.putAll(s.decayed);
			}
		}
	}
}
