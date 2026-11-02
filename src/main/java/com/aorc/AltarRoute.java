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

	AltarRoute(String name, boolean fairyRing, int altar, int[]... steps)
	{
		this.name = name;
		this.fairyRing = fairyRing;
		this.altar = altar;
		List<int[]> list = new ArrayList<>();
		Collections.addAll(list, steps);
		this.steps = Collections.unmodifiableList(list);
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
			new int[]{25380, 3560, 9780, 0}));  // 5. Mysterious ruins
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

	/** Same object at the same spot (within 2 tiles, same floor). */
	static boolean same(int[] a, int[] b)
	{
		return a[0] == b[0] && a[3] == b[3] && Math.abs(a[1] - b[1]) <= 2 && Math.abs(a[2] - b[2]) <= 2;
	}
}
