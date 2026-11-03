package com.runecraftcompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The way to one altar, set by hand: whether you use a fairy ring, every object
 * you go through on the way ({object ID, x, y, plane}, in order) and the altar itself.
 * The spot makes sure only that exact object is highlighted (the cave on the other side has
 * the same ID). A spot of -1 means "not known yet": the closest object with that ID is used.
 * Add a route to ROUTES below; the key is the altar's map area (region). An altar can have
 * more routes for different Agility levels (shortcuts): the one with the highest level you have
 * (with boosts, like the game checks shortcuts) is used.
 */
public class AltarRoute
{
	final String name;
	final int minAgility;
	final boolean fairyRing;
	final int altar;
	final List<int[]> steps;
	/** Objects never highlighted on this route (the way back through a cave, from the other side). */
	final java.util.Set<Integer> hidden = new java.util.HashSet<>();
	/** Object ID -> steps (index): no "Enter" on that object while one of those steps is the highlighted one. */
	final Map<Integer, java.util.Set<Integer>> hiddenAt = new HashMap<>();

	AltarRoute(String name, boolean fairyRing, int altar, int[]... steps)
	{
		this(name, 0, fairyRing, altar, steps);
	}

	AltarRoute(String name, int minAgility, boolean fairyRing, int altar, int[]... steps)
	{
		this.name = name;
		this.minAgility = minAgility;
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
			hiddenAt.computeIfAbsent(id, k -> new java.util.HashSet<>()).add(step - 1);
		}
		return this;
	}

	/** No route known for this altar. */
	static final AltarRoute NONE = new AltarRoute("none", false, -1);

	/** Routes by the altar's map area (region ID, shown as "altar region" in Route debug). */
	static final Map<Integer, List<AltarRoute>> ROUTES = new HashMap<>();

	private static void add(int region, AltarRoute r)
	{
		ROUTES.computeIfAbsent(region, k -> new ArrayList<>()).add(r);
	}

	static
	{
		// Blood - Mory: fairy ring DLS, then the caves to the blood altar
		int[] cave1 = {16308, 3447, 9821, 0};    // Cave entrance
		int[] cave2 = {5046, 3467, 9820, 0};     // Cave entrance
		int[] ruins = {25380, 3560, 9780, 0};    // Mysterious ruins
		// 93 Agility: the shortcut
		add(12875, new AltarRoute("Blood - Mory (93 Agility)", 93, true, 43479,
			cave1, cave2,
			new int[]{43759, 3500, 9803, 0},    // 3. Cave (93 Agility)
			new int[]{43762, 3540, 9772, 0},    // 4. Cave
			ruins)
			.hide(4, 43763));                   // step 4 from the other side (the way back)
		int[] cave3 = {12770, 3484, 9832, 0};   // Cave entrance
		int[] cave4 = {12771, 3492, 9861, 0};   // Cave entrance
		// 74 Agility
		add(12875, new AltarRoute("Blood - Mory (74 Agility)", 74, true, 43479,
			cave1, cave2, cave3, cave4,
			new int[]{43755, 3560, 9813, 0},    // 5. Cave (74 Agility)
			new int[]{43758, 3555, 9787, 0},    // 6. Cave
			ruins)
			.hide(5, 43756, 43757)              // steps 5 and 6 from the other side (the way back)
			.hide(6, 43756, 43757));
		// below 74 Agility: walk around after step 4
		add(12875, new AltarRoute("Blood - Mory", 0, true, 43479,
			cave1, cave2, cave3, cave4,
			ruins));
	}

	/** The route to this altar for your Agility level (with boosts). */
	static AltarRoute of(int altarRegion, int agility)
	{
		AltarRoute best = NONE;
		for (AltarRoute r : ROUTES.getOrDefault(altarRegion, Collections.emptyList()))
		{
			if (agility >= r.minAgility && (best == NONE || r.minAgility > best.minAgility))
			{
				best = r;
			}
		}
		return best;
	}

	/** This route with the unknown spots (-1) filled in by 'spotOf' (object ID -> {x, y, plane}, or null). */
	AltarRoute withSpots(java.util.function.IntFunction<int[]> spotOf)
	{
		boolean unknown = false;
		for (int[] st : steps)
		{
			unknown |= st[1] < 0;
		}
		if (!unknown)
		{
			return this;
		}
		int[][] filled = new int[steps.size()][];
		for (int i = 0; i < steps.size(); i++)
		{
			int[] st = steps.get(i);
			int[] at = st[1] < 0 ? spotOf.apply(st[0]) : null;
			filled[i] = at == null ? st : new int[]{st[0], at[0], at[1], at[2]};
		}
		AltarRoute r = new AltarRoute(name, minAgility, fairyRing, altar, filled);
		r.hidden.addAll(hidden);
		r.hiddenAt.putAll(hiddenAt);
		return r;
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
			if (st[1] < 0)
			{
				continue;   // spot not known yet
			}
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
			if (i + 1 < steps.size() && steps.get(i + 1)[3] == plane && steps.get(i + 1)[1] >= 0)
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
