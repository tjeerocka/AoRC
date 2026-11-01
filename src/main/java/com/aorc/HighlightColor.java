package com.aorc;

/** Colour of the text to click in the chat box during a pouch repair (setting). All read well on the beige chat box. */
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

	@Override
	public String toString()
	{
		return name;
	}
}
