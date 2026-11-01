package com.aorc;

import java.awt.Color;

/** Colour for the pouch repair highlights (setting). All read well on the beige chat box. */
public enum HighlightColor
{
	DARK_GREEN("Dark green", 0x007F00),
	GREEN("Green", 0x00B000),
	ORANGE("Orange", 0xE06A00),
	PURPLE("Purple", 0x8000A0),
	MAGENTA("Magenta", 0xC00080),
	TEAL("Teal", 0x007878),
	DARK_RED("Dark red", 0x9A0000),
	BROWN("Brown", 0x7A4A00);

	private final String name;
	private final int rgb;

	HighlightColor(String name, int rgb)
	{
		this.name = name;
		this.rgb = rgb;
	}

	/** For game text (0xRRGGBB). */
	public int getRgb()
	{
		return rgb;
	}

	/** For the boxes around the pouch and the spell (a bit brighter, so they show on dark backgrounds). */
	public Color boxColor()
	{
		Color c = new Color(rgb);
		float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
		return Color.getHSBColor(hsb[0], hsb[1], Math.max(hsb[2], 0.95f));
	}

	@Override
	public String toString()
	{
		return name;
	}
}
