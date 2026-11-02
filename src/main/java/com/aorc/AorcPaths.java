package com.aorc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Walking paths, recorded with "Record path" (Developer settings) and stored here as corner
 * points {x, y, plane}. The line to the next step follows a path that goes past you and past
 * the next step, so it never goes straight through walls. No path there: a straight line.
 */
final class AorcPaths
{
	/** Every known path, each in walking order. */
	static final List<int[][]> PATHS = new ArrayList<>();

	private AorcPaths()
	{
	}

	/** A path as x, y, plane, x, y, plane, ... */
	private static void add(int... xyz)
	{
		int[][] points = new int[xyz.length / 3][];
		for (int i = 0; i < points.length; i++)
		{
			points[i] = new int[]{xyz[3 * i], xyz[3 * i + 1], xyz[3 * i + 2]};
		}
		PATHS.add(points);
	}

	static
	{
		// Blood - Zeah (recorded 2 Nov 2026)
		// Zeah: shortcut > dark altar
		add(1761, 3874, 0, 1761, 3876, 0, 1753, 3876, 0, 1751, 3874, 0, 1747, 3874, 0, 1745, 3873, 0, 1737, 3873, 0, 1729, 3879, 0, 1721, 3879, 0, 1718, 3882, 0);
		// Zeah: runestone > shortcut
		add(1765, 3855, 0, 1761, 3855, 0, 1757, 3850, 0, 1755, 3850, 0, 1755, 3852, 0, 1754, 3854, 0, 1754, 3864, 0, 1761, 3870, 0, 1761, 3872, 0);
		// Zeah: blood altar > climb
		add(1719, 3828, 0, 1721, 3828, 0, 1723, 3827, 0, 1731, 3827, 0, 1734, 3830, 0, 1734, 3842, 0, 1735, 3844, 0, 1735, 3846, 0, 1742, 3852, 0, 1742, 3854, 0);
		// Zeah: dark altar > runestone
		add(1718, 3882, 0, 1722, 3880, 0, 1730, 3880, 0, 1736, 3875, 0, 1738, 3875, 0, 1742, 3873, 0, 1752, 3873, 0, 1758, 3876, 0, 1760, 3876, 0, 1761, 3875, 0, 1761, 3870, 0, 1754, 3862, 0, 1754, 3852, 0, 1755, 3850, 0, 1757, 3850, 0, 1762, 3855, 0);
		// Zeah: climb > runestone
		add(1752, 3854, 0, 1756, 3850, 0, 1758, 3850, 0, 1758, 3852, 0, 1761, 3852, 0);
		// Zeah: dark altar > blood altar
		add(1718, 3882, 0, 1717, 3881, 0, 1693, 3881, 0, 1691, 3880, 0, 1661, 3880, 0, 1659, 3879, 0, 1659, 3869, 0, 1664, 3864, 0, 1666, 3864, 0, 1670, 3862, 0, 1674, 3862, 0, 1676, 3861, 0, 1686, 3861, 0, 1688, 3860, 0, 1710, 3860, 0, 1712, 3859, 0, 1714, 3859, 0, 1716, 3858, 0, 1720, 3858, 0, 1722, 3857, 0, 1728, 3857, 0, 1735, 3849, 0, 1735, 3831, 0, 1734, 3829, 0, 1730, 3829, 0, 1726, 3827, 0, 1720, 3827, 0, 1719, 3828, 0);
	}

	/** On a path: this close (tiles) to one of its points. */
	static final int ON_PATH = 4;

	/**
	 * The points to draw from (x, y) to the target: along a known path when one goes past you
	 * and past the target, otherwise just the target (a straight line).
	 */
	static List<int[]> line(int x, int y, int plane, int tx, int ty, int tPlane)
	{
		List<int[]> best = null;
		for (int[][] path : PATHS)
		{
			int from = nearestPart(path, x, y, plane);
			int to = nearestPart(path, tx, ty, tPlane);
			if (from < 0 || to < 0)
			{
				continue;
			}
			// part i runs from point i to point i+1; paths can be walked both ways
			List<int[]> pts = new ArrayList<>();
			if (from < to)
			{
				for (int i = from + 1; i <= to; i++)
				{
					pts.add(path[i]);
				}
			}
			else
			{
				for (int i = from; i > to; i--)
				{
					pts.add(path[i]);
				}
			}
			if (best == null || pts.size() < best.size())
			{
				best = pts;
			}
		}
		if (best == null)
		{
			best = new ArrayList<>();
		}
		best.add(new int[]{tx, ty, tPlane});
		return best;
	}

	/** The part (point i to i+1) of this path closest to (x, y), if within ON_PATH tiles; else -1. */
	static int nearestPart(int[][] path, int x, int y, int plane)
	{
		int best = -1;
		double bestD = ON_PATH + 0.5;
		for (int i = 0; i + 1 < path.length; i++)
		{
			int[] a = path[i];
			int[] b = path[i + 1];
			if (a[2] != plane || b[2] != plane)
			{
				continue;
			}
			double d = distToPart(x, y, a[0], a[1], b[0], b[1]);
			if (d < bestD)
			{
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	private static double distToPart(double px, double py, double ax, double ay, double bx, double by)
	{
		double dx = bx - ax;
		double dy = by - ay;
		double len = dx * dx + dy * dy;
		double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len));
		return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
	}

	/**
	 * Turn a recorded walk (every tile) into path pieces with only their corners (where the
	 * direction changes, plus the first and last point). A jump (cave, climb, teleport: more
	 * than 2 tiles in one tick) or a floor change starts a new piece, so a line never crosses it.
	 */
	static List<List<int[]>> pieces(List<int[]> walk)
	{
		List<List<int[]>> out = new ArrayList<>();
		List<int[]> piece = new ArrayList<>();
		for (int[] p : walk)
		{
			if (!piece.isEmpty())
			{
				int[] last = piece.get(piece.size() - 1);
				if (last[2] != p[2] || Math.max(Math.abs(last[0] - p[0]), Math.abs(last[1] - p[1])) > 2)
				{
					out.add(corners(piece));
					piece = new ArrayList<>();
				}
			}
			piece.add(p);
		}
		if (!piece.isEmpty())
		{
			out.add(corners(piece));
		}
		return out;
	}

	/** Only the corners of one piece (no jumps in it). */
	static List<int[]> corners(List<int[]> walk)
	{
		if (walk.size() < 3)
		{
			return new ArrayList<>(walk);
		}
		List<int[]> out = new ArrayList<>();
		out.add(walk.get(0));
		for (int i = 1; i < walk.size() - 1; i++)
		{
			int[] a = walk.get(i - 1);
			int[] b = walk.get(i);
			int[] c = walk.get(i + 1);
			if (Integer.signum(b[0] - a[0]) != Integer.signum(c[0] - b[0])
				|| Integer.signum(b[1] - a[1]) != Integer.signum(c[1] - b[1]))
			{
				out.add(b);
			}
		}
		out.add(walk.get(walk.size() - 1));
		return out;
	}
}
