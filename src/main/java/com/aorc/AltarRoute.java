package com.aorc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The way to one altar, set by hand (no learning): whether you use a fairy ring, every object
 * you go through on the way ({object ID, x, y, plane}, in order) and the altar itself.
 * The spot makes sure only that exact object is highlighted (the cave on the other side has
 * the same ID). Add a route to ROUTES below; the key is the altar's map area (region).
 */
public class AltarRoute
{
	final String name;
	final boolean fairyRing;
	final int altar;
	final List<int[]> steps;
	/** Objects never highlighted on this route (the way back through a cave, from the other side). */
	final java.util.Set<Integer> hidden = new java.util.HashSet<>();
	/** Object ID -> step (index): no "Enter" on that object while that step is the highlighted one. */
	final Map<Integer, Integer> hiddenAt = new HashMap<>();

	AltarRoute(String name, boolean fairyRing, int altar, int[]... steps)
	{
		this.name = name;
		this.fairyRing = fairyRing;
		this.altar = altar;
		List<int[]> list = new ArrayList<>();
		Collections.addAll(list, steps);
		this.steps = Collections.unmodifiableList(list);
	}

	/** The way back at step 'step' (1 = first): never highlighted, and no options while that step is next. */
	AltarRoute hide(int step, int... ids)
	{
		for (int id : ids)
		{
			hidden.add(id);
			hiddenAt.put(id, step - 1);
		}
		return this;
	}

	/** No route known for this altar. */
	static final AltarRoute NONE = new AltarRoute("none", false, -1);

	/** Routes by the altar's map area (region ID, shown as "altar region" in Route debug). */
	static final Map<Integer, AltarRoute> ROUTES = new HashMap<>();

	static
	{
		// Blood - Mory: fairy ring DLS, then the caves to the blood altar
		ROUTES.put(12875, new AltarRoute("Blood - Mory", true, 43479,
			new int[]{16308, 3447, 9821, 0},    // 1. Cave entrance
			new int[]{5046, 3467, 9820, 0},     // 2. Cave entrance
			new int[]{43759, 3500, 9803, 0},    // 3. Cave
			new int[]{43762, 3540, 9772, 0},    // 4. Cave
			new int[]{25380, 3560, 9780, 0})    // 5. Mysterious ruins
			.hide(4, 43763));                   // step 4 from the other side (the way back)
	}

	static AltarRoute of(int altarRegion)
	{
		return ROUTES.getOrDefault(altarRegion, NONE);
	}

	/** Index of the step at this spot (within 2 tiles, same floor; any object ID there), or -1. */
	int findSpot(int x, int y, int plane)
	{
		for (int i = 0; i < steps.size(); i++)
		{
			int[] r = steps.get(i);
			if (r[3] == plane && Math.abs(r[1] - x) <= 2 && Math.abs(r[2] - y) <= 2)
			{
				return i;
			}
		}
		return -1;
	}

	/** Next to a step: this close (tiles) counts as standing at it. */
	static final int NEAR = 3;

	/**
	 * Which step you just went through this tick, or -1 (no clicks needed):
	 * - you jumped (more than 2 tiles in one tick) from right next to it (ruins, ladders), or
	 * - you were right next to it and now stand on its other side (caves you crawl through).
	 * Walking up to a cave and back the way you came does not count.
	 * 'approach' remembers per step where you stood when you were next to it.
	 */
	int passedNow(java.util.Set<Integer> passed, Map<Integer, int[]> approach,
		int fromX, int fromY, int fromPlane, int x, int y, int plane, int moved)
	{
		for (int i = 0; i < steps.size(); i++)
		{
			if (passed.contains(i))
			{
				continue;
			}
			int[] st = steps.get(i);
			boolean wasNear = fromPlane == st[3] && Math.abs(fromX - st[1]) <= NEAR && Math.abs(fromY - st[2]) <= NEAR;
			if (moved > 2 && wasNear)
			{
				return i;
			}
			if (plane != st[3])
			{
				continue;
			}
			int dx = x - st[1];
			int dy = y - st[2];
			int d = Math.max(Math.abs(dx), Math.abs(dy));
			if (d <= NEAR)
			{
				if (dx != 0 || dy != 0)
				{
					approach.putIfAbsent(i, new int[]{dx, dy});
				}
			}
			else if (d <= 20 && approach.containsKey(i))
			{
				int[] a = approach.get(i);
				double cos = (a[0] * dx + a[1] * dy) / (Math.hypot(a[0], a[1]) * Math.hypot(dx, dy));
				if (cos < -0.3)
				{
					return i;   // came from one side, now on the other side
				}
			}
		}
		return -1;
	}

	/** Same object at the same spot (within 2 tiles, same floor). */
	static boolean same(int[] a, int[] b)
	{
		return a[0] == b[0] && a[3] == b[3] && Math.abs(a[1] - b[1]) <= 2 && Math.abs(a[2] - b[2]) <= 2;
	}
}
