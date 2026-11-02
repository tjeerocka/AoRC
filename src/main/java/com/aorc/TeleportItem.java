package com.aorc;

/** Teleport items for the bank / altar highlight (setting). Matched by item name, so all charges and variants work. */
public enum TeleportItem
{
	NONE("None"),
	SAILORS_AMULET("Sailors' amulet", "sailors' amulet", "sailor's amulet"),
	QUEST_CAPE("Quest point cape", "quest point cape"),
	CRAFTING_CAPE("Crafting cape", "crafting cape"),
	RUNECRAFT_CAPE("Runecraft cape", "runecraft cape"),
	CONSTRUCTION_CAPE("Construction cape", "construction cape"),
	MAX_CAPE("Max cape", "max cape"),
	ARDOUGNE_CLOAK("Ardougne cloak", "ardougne cloak"),
	RING_OF_DUELING("Ring of dueling", "ring of dueling"),
	RING_OF_WEALTH("Ring of wealth", "ring of wealth"),
	AMULET_OF_GLORY("Amulet of glory", "amulet of glory"),
	DRAKANS_MEDALLION("Drakan's medallion", "drakan's medallion"),
	ECTOPHIAL("Ectophial", "ectophial"),
	XERICS_TALISMAN("Xeric's talisman", "xeric's talisman"),
	BOOK_OF_THE_DEAD("Book of the dead", "book of the dead"),
	KHAREDSTS_MEMOIRS("Kharedst's memoirs", "kharedst's memoirs");

	private final String label;
	private final String[] names;

	TeleportItem(String label, String... names)
	{
		this.label = label;
		this.names = names;
	}

	/** Does this item name belong to this teleport (any charges / variant)? */
	public boolean matches(String itemName)
	{
		if (itemName == null)
		{
			return false;
		}
		String n = itemName.toLowerCase();
		for (String s : names)
		{
			if (n.startsWith(s))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
