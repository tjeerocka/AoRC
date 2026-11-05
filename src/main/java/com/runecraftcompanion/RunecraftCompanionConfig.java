package com.runecraftcompanion;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(RunecraftCompanionConfig.GROUP)
public interface RunecraftCompanionConfig extends Config
{
	String GROUP = "runecraftcompanion";
	/** The settings group of the plugin's first name: moved to GROUP once. */
	String OLD_GROUP = "aorc";

	@ConfigSection(
		name = "Runecraft Companion",
		description = "Counts runes crafted per hour",
		position = 0
	)
	String mainSection = "main";

	@ConfigItem(
		keyName = "showAllRunes",
		name = "All rune types in overlay",
		description = "Show every rune type in the overlay. The side panel always shows all rune types.",
		section = mainSection,
		position = 1
	)
	default boolean showAllRunes()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showBonuses",
		name = "Show bonuses",
		description = "Show active rune bonuses (Raiments, lantern, blood essence, diary)",
		section = mainSection,
		position = 3
	)
	default boolean showBonuses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showProfit",
		name = "Show GP per hour",
		description = "Show the value of crafted runes per hour (GE price)",
		section = mainSection,
		position = 4
	)
	default boolean showProfit()
	{
		return true;
	}

	@Range(min = 1, max = 60)
	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "idleTimeout",
		name = "Idle timeout",
		description = "After this many minutes with no runecrafting, the timer stops and the overlay hides",
		section = mainSection,
		position = 5
	)
	default int idleTimeout()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "resetOnIdle",
		name = "New session after idle",
		description = "Start a new session (reset numbers) when you craft again after being idle",
		section = mainSection,
		position = 6
	)
	default boolean resetOnIdle()
	{
		return false;
	}

	@ConfigItem(
		keyName = "rememberSession",
		name = "Remember after closing",
		description = "Keep your numbers when you close the client. Off = start fresh every time you open RuneLite (logging out and in still keeps them).",
		section = mainSection,
		position = 7
	)
	default boolean rememberSession()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBuddy",
		name = "Hype buddy",
		description = "Show Runey, a little rune buddy at the bottom of the side panel who cheers you on",
		section = mainSection,
		position = 8
	)
	default boolean showBuddy()
	{
		return true;
	}

	@ConfigItem(
		keyName = "spitOnPlayer",
		name = "Runey spits on you (mains)",
		description = "Main accounts only: Runey spits on your character in the game (1 minute after login, then every 30 minutes). Only you see it.",
		section = mainSection,
		position = 9
	)
	default boolean spitOnPlayer()
	{
		return true;
	}

	// ---- Goals ----

	@ConfigSection(
		name = "Goals",
		description = "Time left to reach your goals, at your current speed",
		position = 20
	)
	String goalSection = "goals";

	@Range(min = 2, max = 99)
	@ConfigItem(
		keyName = "goalLevel",
		name = "Runecraft level goal",
		description = "Shows the time left to reach this Runecraft level",
		section = goalSection,
		position = 21
	)
	default int goalLevel()
	{
		return 99;
	}

	@ConfigItem(
		keyName = "goalRuneType",
		name = "Rune goal type",
		description = "Which runes count for the rune goal. Any rune = all rune types together.",
		section = goalSection,
		position = 22
	)
	default GoalRune goalRuneType()
	{
		return GoalRune.BLOOD;
	}

	@ConfigItem(
		keyName = "goalBloodRunes",   // old key name kept, so your saved goal stays
		name = "Rune goal",
		description = "Shows the time left to craft this many runes (of the type above). Has its own counter: the reset buttons do not clear it, only \"Clear goal progress\" in the side panel. 0 = off",
		section = goalSection,
		position = 23
	)
	default int goalRunes()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "goalInOverlay",
		name = "Level goal in overlay",
		description = "Show the time to your level goal in the overlay",
		section = goalSection,
		position = 24
	)
	default boolean goalInOverlay()
	{
		return true;
	}

	// ---- Warnings ----

	@ConfigSection(
		name = "Warnings",
		description = "Get a notification when something is missing (checked at the Dark Altar)",
		position = 30
	)
	String warnSection = "warnings";

	@ConfigItem(
		keyName = "warnLantern",
		name = "No blisterwood lantern",
		description = "Warn when you imbue at the Dark Altar without the blisterwood lantern lit",
		section = warnSection,
		position = 31
	)
	default boolean warnLantern()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnEssence",
		name = "Blood essence not active",
		description = "Warn when you carry a blood essence that is not activated",
		section = warnSection,
		position = 32
	)
	default boolean warnEssence()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnRaiments",
		name = "Raiments piece missing",
		description = "Warn when you wear fewer Raiments of the Eye pieces than before",
		section = warnSection,
		position = 33
	)
	default boolean warnRaiments()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnLowCharges",
		name = "Blood essence almost empty",
		description = "Warn when your blood essence has about 100 charges or less left (each time it goes down, at most once a minute), "
			+ "and when it is used up: a new blood essence in your inventory is highlighted until you activate it",
		section = warnSection,
		position = 34
	)
	default boolean warnLowCharges()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnEnergy",
		name = "Low run energy",
		description = "The boot on the run orb flashes yellow at 30% run energy, red with a message next to it at 20%, "
			+ "and RESTORE STAMINA ASAP! above you at 10% (while you runecraft)",
		section = warnSection,
		position = 36
	)
	default boolean warnEnergy()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnPouch",
		name = "Essence pouch needs repair",
		description = "Warn when an essence pouch has 3 durability left (3/12: fills before it decays, read from the game). The pouch is highlighted in your inventory "
			+ "and Astral Contact in your spellbook, until the Dark Mage repairs it.",
		section = warnSection,
		position = 35
	)
	default boolean warnPouch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "repairColor",
		name = "Repair chat colour",
		description = "Colour of the text to click in the chat box when talking to the Dark Mage "
			+ "(the pouch and Astral Contact stay orange)",
		section = warnSection,
		position = 36
	)
	default HighlightColor repairColor()
	{
		return HighlightColor.DARK_GREEN;
	}

	// ---- Teleport highlights ----

	@ConfigSection(
		name = "Teleport highlights",
		description = "Highlight the teleport to use next, in your inventory or worn equipment",
		position = 40
	)
	String teleportSection = "teleports";

	@ConfigItem(
		keyName = "highlightTeleports",
		name = "Highlight teleports",
		description = "After your last craft (essence and pouches empty) the bank teleport is highlighted. "
			+ "When you are full of essence (pouches full too) the altar teleport is highlighted (both in cyan, like the route).",
		section = teleportSection,
		position = 41
	)
	default boolean highlightTeleports()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankTeleport",
		name = "Bank teleport",
		description = "The item you teleport to the bank with",
		section = teleportSection,
		position = 42
	)
	default TeleportItem bankTeleport()
	{
		return TeleportItem.NONE;
	}

	@ConfigItem(
		keyName = "altarTeleport",
		name = "Altar teleport",
		description = "The item you teleport to the altar with",
		section = teleportSection,
		position = 43
	)
	default TeleportItem altarTeleport()
	{
		return TeleportItem.NONE;
	}

	@ConfigItem(
		keyName = "highlightRoute",
		name = "Highlight route to the altar",
		description = "Highlight the fairy ring, every cave on the way and the altar (built-in: Blood - Mory and the Blood - Zeah cycle, with the shortcuts for your Agility level). "
			+ "While a cave is highlighted, the cave next to it that takes you back has no Enter option. Also highlights "
			+ "the essence to take in the bank (the essence you used last)",
		section = teleportSection,
		position = 44
	)
	default boolean highlightRoute()
	{
		return true;
	}

	@ConfigItem(
		keyName = "routeHintArrow",
		name = "Arrow above the next step",
		description = "The game's yellow arrow above the next thing to click on your route (also on the minimap)",
		section = teleportSection,
		position = 47
	)
	default boolean routeHintArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "routeLine",
		name = "Line to the next step",
		description = "A line on the ground from you to the next thing to click (along recorded paths, not through walls)",
		section = teleportSection,
		position = 48
	)
	default boolean routeLine()
	{
		return true;
	}

	// ---- Developer ----

	@ConfigSection(
		name = "Developer",
		description = "Tools to check the routes (object IDs and spots)",
		position = 90,
		closedByDefault = true
	)
	String devSection = "developer";

	@ConfigItem(
		keyName = "routeDebug",
		name = "Route debug",
		description = "Show the route: a box with every step (object ID and spot), the object ID in the right-click menu "
			+ "and a label above each route object (step number, passed / next)",
		section = devSection,
		position = 45
	)
	default boolean routeDebug()
	{
		return false;
	}

	@ConfigItem(
		keyName = "recordPath",
		name = "Record path",
		description = "Turn on, walk the way, turn off: the tiles you walked are saved to a file in "
			+ ".runelite/runecraft-companion/paths (the file name is shown in the chat)",
		section = devSection,
		position = 46
	)
	default boolean recordPath()
	{
		return false;
	}

	@ConfigItem(
		keyName = "recordPiece",
		name = "Recording",
		description = "What you are recording (start and stop right next to the objects); it becomes the file name",
		section = devSection,
		position = 47
	)
	default RecordPiece recordPiece()
	{
		return RecordPiece.OTHER;
	}
}
