package com.aorc;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Runey's animation frames (pre-rendered from the 3D model), with the headband
 * recoloured per account type. Frames are 96x96 in one sheet:
 * 0-7 idle sway, 8-15 cheer, 16-19 spit to the left, 20-23 spit to the right,
 * 24-36 turn (yaw -36 .. 0 degrees in steps of 3; 0 = facing you).
 */
final class RuneyFrames
{
	static final int SIZE = 96;
	static final int IDLE = 0;
	static final int CHEER = 8;
	static final int SPIT_LEFT = 16;
	static final int SPIT_RIGHT = 20;
	static final int TURN = 24;
	static final int TURN_MIN_YAW = -36;
	static final int TURN_STEP = 3;
	static final int TURN_FRAMES = 13;
	static final int COUNT = TURN + TURN_FRAMES;

	/** Frame for this yaw (degrees, -36 .. 0). */
	static int turnFrame(double yaw)
	{
		int i = (int) Math.round((yaw - TURN_MIN_YAW) / TURN_STEP);
		return TURN + Math.max(0, Math.min(TURN_FRAMES - 1, i));
	}

	private static BufferedImage sheet;
	private static BufferedImage mask;
	private static boolean failed;
	private static Point[] noses;
	private static Point[] heads;
	private static final Map<AccountType, BufferedImage[]> CACHE = new EnumMap<>(AccountType.class);

	private RuneyFrames()
	{
	}

	/** Frames for this account type, or null if the images could not be loaded. */
	static synchronized BufferedImage[] get(AccountType type)
	{
		if (!load())
		{
			return null;
		}
		return CACHE.computeIfAbsent(type, RuneyFrames::build);
	}

	/** Nose tip in frame i (where the spit comes out), in frame pixels. */
	static synchronized Point nose(int i)
	{
		return load() ? noses[i] : new Point(SIZE / 2, SIZE / 2);
	}

	/** Top centre of the headband in frame i (where a partyhat sits), in frame pixels. */
	static synchronized Point head(int i)
	{
		return load() ? heads[i] : new Point(SIZE / 2, 10);
	}

	private static boolean load()
	{
		if (sheet != null)
		{
			return true;
		}
		if (failed)
		{
			return false;
		}
		try (InputStream a = RuneyFrames.class.getResourceAsStream("runey_sheet.png");
			InputStream b = RuneyFrames.class.getResourceAsStream("runey_mask.png"))
		{
			if (a == null || b == null)
			{
				failed = true;
				return false;
			}
			BufferedImage s = ImageIO.read(a);
			BufferedImage m = ImageIO.read(b);
			noses = new Point[COUNT];
			heads = new Point[COUNT];
			for (int i = 0; i < COUNT; i++)
			{
				findAnchors(s, m, i);
			}
			sheet = s;
			mask = m;
			return true;
		}
		catch (Exception e)
		{
			failed = true;
			return false;
		}
	}

	/** The nose is the big dark-red blob in the face; the headband is the masked area. */
	private static void findAnchors(BufferedImage s, BufferedImage m, int i)
	{
		int ox = i * SIZE;
		long nx = 0;
		long ny = 0;
		int n = 0;
		int bandTop = Integer.MAX_VALUE;
		long bx = 0;
		int bn = 0;
		for (int y = 0; y < SIZE; y++)
		{
			for (int x = 0; x < SIZE; x++)
			{
				int band = m.getRaster().getSample(ox + x, y, 0);
				int argb = s.getRGB(ox + x, y);
				if ((argb >>> 24) < 128)
				{
					continue;
				}
				if (band > 128)
				{
					bandTop = Math.min(bandTop, y);
					bx += x;
					bn++;
					continue;
				}
				int r = (argb >> 16) & 255;
				int g = (argb >> 8) & 255;
				int bl = argb & 255;
				if (y > 25 && y < 68 && r > 90 && g < r / 2 && bl < r / 2)
				{
					nx += x;
					ny += y;
					n++;
				}
			}
		}
		noses[i] = n == 0 ? new Point(SIZE / 2, SIZE / 2) : new Point((int) (nx / n), (int) (ny / n));
		heads[i] = bn == 0 ? new Point(SIZE / 2, 10) : new Point((int) (bx / bn), bandTop);
	}

	private static BufferedImage[] build(AccountType type)
	{
		BufferedImage[] out = new BufferedImage[COUNT];
		int[] accent = accent(type);
		for (int i = 0; i < COUNT; i++)
		{
			BufferedImage f = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < SIZE; y++)
			{
				for (int x = 0; x < SIZE; x++)
				{
					int argb = sheet.getRGB(i * SIZE + x, y);
					float k = mask.getRaster().getSample(i * SIZE + x, y, 0) / 255f;
					if (accent != null && k > 0)
					{
						argb = tint(argb, accent, k);
					}
					f.setRGB(x, y, argb);
				}
			}
			out[i] = f;
		}
		return out;
	}

	/** Keep the shading of the red headband, but in the account colour. */
	private static int tint(int argb, int[] accent, float k)
	{
		int a = argb >>> 24;
		int r = (argb >> 16) & 255;
		int g = (argb >> 8) & 255;
		int b = argb & 255;
		// the red band is about 0.33 bright; scale the accent by how light this pixel is
		float light = Math.min(1.6f, (0.3f * r + 0.59f * g + 0.11f * b) / 255f / 0.33f);
		int nr = clamp(accent[0] * light);
		int ng = clamp(accent[1] * light);
		int nb = clamp(accent[2] * light);
		r = Math.round(r + (nr - r) * k);
		g = Math.round(g + (ng - g) * k);
		b = Math.round(b + (nb - b) * k);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int clamp(float v)
	{
		return Math.max(0, Math.min(255, Math.round(v)));
	}

	/** Headband colour per account type; null keeps the model's own red. */
	static int[] accent(AccountType t)
	{
		switch (t)
		{
			case IRONMAN:
				return new int[]{140, 140, 150};
			case ULTIMATE:
				return new int[]{215, 215, 222};
			case HARDCORE:
				return null;                         // red, like the model
			case GROUP:
			case HARDCORE_GROUP:
				return new int[]{50, 110, 220};      // blue (ranked group)
			case UNRANKED_GROUP:
				return new int[]{40, 170, 70};       // green
			default:
				return new int[]{235, 185, 30};      // main: gold
		}
	}
}
