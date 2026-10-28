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
		name = "Show all rune types",
		description = "Show every rune type you craft, not only blood runes",
		section = mainSection,
		position = 1
	)
	default boolean showAllRunes()
	{
		return true;
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
}
