package com.aorc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(AorcConfig.GROUP)
public interface AorcConfig extends Config
{
	String GROUP = "aorc";

	@ConfigSection(
		name = "Amount of Runes Created",
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
		keyName = "showSidebar",
		name = "Side panel at ZMI",
		description = "When you craft at ZMI (many rune types), show the AoRC button in the sidebar with the full list of runes",
		section = mainSection,
		position = 0
	)
	default boolean showSidebar()
	{
		return true;
	}
}
