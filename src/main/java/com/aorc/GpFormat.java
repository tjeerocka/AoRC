package com.aorc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Short GP text: 600000 -> "600k", 1000000 -> "1m", 2791216 -> "2.79m". */
public final class GpFormat
{
	private GpFormat()
	{
	}

	public static String shortGp(long gp)
	{
		long abs = Math.abs(gp);
		String sign = gp < 0 ? "-" : "";
		if (abs >= 1_000_000_000L)
		{
			return sign + trim(abs / 1_000_000_000.0, 2) + "b";
		}
		if (abs >= 1_000_000L)
		{
			return sign + trim(abs / 1_000_000.0, 2) + "m";
		}
		if (abs >= 1_000L)
		{
			return sign + (abs / 1_000L) + "k";
		}
		return sign + abs;
	}

	private static String trim(double value, int decimals)
	{
		return BigDecimal.valueOf(value)
			.setScale(decimals, RoundingMode.DOWN)
			.stripTrailingZeros()
			.toPlainString();
	}

	/**
	 * Short per-hour text: up to 9,999 the full number ("2,356"), then one decimal with a
	 * comma and k or m, rounded down: 30895 -> "30,8k", 250000 -> "250k", 1234567 -> "1,23m".
	 */
	public static String shortRate(long value)
	{
		long abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs >= 1_000_000L)
		{
			return sign + trim(abs / 1_000_000.0, 2).replace('.', ',') + "m";
		}
		if (abs >= 10_000L)
		{
			return sign + trim(abs / 1_000.0, 1).replace('.', ',') + "k";
		}
		return sign + java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(abs);
	}

	/** Bonus % as a multiplier: 90 -> "x1.90", 0 -> "x1.00". */
	public static String multiplier(int bonusPercent)
	{
		return String.format(java.util.Locale.ROOT, "x%.2f", 1 + bonusPercent / 100.0);
	}
}
