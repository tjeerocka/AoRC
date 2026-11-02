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
			int[] r = steps.get(i);
			if (r[0] == step[0] && r[3] == step[3] && Math.abs(r[1] - step[1]) <= 2 && Math.abs(r[2] - step[2]) <= 2)
			{
				return i;
			}
		}
		return -1;
	}
}
