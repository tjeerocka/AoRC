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
}
