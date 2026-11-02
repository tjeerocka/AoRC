package com.aorc;

/** What you are recording with "Record path": becomes part of the file name. */
public enum RecordPiece
{
	OTHER("Other", "other"),
	ZEAH_STONE_TO_SHORTCUT("Zeah: runestone > shortcut", "zeah-runestone-shortcut"),
	ZEAH_SHORTCUT_TO_DARK("Zeah: shortcut > dark altar", "zeah-shortcut-darkaltar"),
	ZEAH_STONE_TO_DARK_WALK("Zeah: runestone > dark altar (walk)", "zeah-runestone-darkaltar-walk"),
	ZEAH_DARK_TO_BLOOD("Zeah: dark altar > blood altar", "zeah-darkaltar-bloodaltar"),
	ZEAH_BLOOD_TO_CLIMB("Zeah: blood altar > climb", "zeah-bloodaltar-climb"),
	ZEAH_CLIMB_TO_STONE("Zeah: climb > runestone", "zeah-climb-runestone"),
	ZEAH_BLOOD_TO_STONE_WALK("Zeah: blood altar > runestone (walk)", "zeah-bloodaltar-runestone-walk"),
	ZEAH_DARK_TO_STONE("Zeah: dark altar > runestone", "zeah-darkaltar-runestone"),
	MORY_RING_TO_CAVE1("Mory: fairy ring > cave 1", "mory-ring-cave1"),
	MORY_CAVE1_TO_CAVE2("Mory: cave 1 > cave 2", "mory-cave1-cave2"),
	MORY_93("Mory: cave 2 > ruins (93)", "mory-93"),
	MORY_74("Mory: cave 2 > ruins (74)", "mory-74"),
	MORY_WALK("Mory: cave 2 > ruins (walk)", "mory-walk");

	private final String name;
	final String file;

	RecordPiece(String name, String file)
	{
		this.name = name;
		this.file = file;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
