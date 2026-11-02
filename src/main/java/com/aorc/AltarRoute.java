package com.aorc;

import java.util.ArrayList;
import java.util.List;

/**
 * The way to one altar, learned from your own clicks: whether you use a fairy ring, every
 * object you click on the way ({object ID, x, y, plane}, in order) and the altar itself.
 * AoRC keeps one per altar (by the altar's map area).
 */
public class AltarRoute
{
	boolean fairyRing;
	int altar = -1;
	List<int[]> steps = new ArrayList<>();

	/** Index of this step (same object, within 2 tiles), or -1. */
	int find(int[] step)
	{
		for (int i = 0; i < steps.size(); i++)
		{
			if (same(steps.get(i), step))
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
