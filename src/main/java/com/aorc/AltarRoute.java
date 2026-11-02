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
	static final int NEAR = 4;

	/**
	 * The last step you are past right now, or -1. It only looks at where you stand, so it
	 * also works right after a login or a restart (nothing to remember):
	 * - you jumped (more than 2 tiles in one tick) from right next to a step (ruins, ladders);
	 * - you stand next to a later step: every step before it is done;
	 * - you are between step i and step i+1, clearly closer to step i+1 than step i is
	 *   (you went through step i; caves you crawl through).
	 * Walking up to a cave and back the way you came does not count.
	 */
	int passedNow(java.util.Set<Integer> passed, int fromX, int fromY, int fromPlane, int x, int y, int plane, int moved)
	{
		int best = -1;
		for (int i = 0; i < steps.size(); i++)
		{
			int[] st = steps.get(i);
			if (!passed.contains(i) && moved > 2 && fromPlane == st[3]
				&& Math.abs(fromX - st[1]) <= NEAR && Math.abs(fromY - st[2]) <= NEAR)
			{
				best = Math.max(best, i);
			}
			if (plane != st[3])
			{
				continue;
			}
			// next to a later step: everything before it is done
			if (i > 0 && Math.abs(x - st[1]) <= NEAR && Math.abs(y - st[2]) <= NEAR)
			{
				best = Math.max(best, i - 1);
			}
			// between this step and the next one, past this one
			if (i + 1 < steps.size() && steps.get(i + 1)[3] == plane)
			{
				int[] nx = steps.get(i + 1);
				double gap = Math.hypot(nx[1] - st[1], nx[2] - st[2]);
				double toNext = Math.hypot(nx[1] - x, nx[2] - y);
				double toThis = Math.hypot(st[1] - x, st[2] - y);
				// (not while you still stand next to this step: the fairy ring puts you right next to cave 1)
				if (toNext < gap - 2 && toThis <= gap && toThis > NEAR)
				{
					best = Math.max(best, i);
				}
			}
		}
		for (int k = 0; k <= best; k++)
		{
			if (!passed.contains(k))
			{
				return best;   // something new is done
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
