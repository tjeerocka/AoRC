package com.aorc;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(AorcConfig.GROUP)
public interface AorcConfig extends Config
{
	String GROUP = "aorc";

	@ConfigItem(
		keyName = "showAllRunes",
		name = "Show all rune types",
		description = "Show every rune type you craft, not only blood runes",
		position = 1
	)
	default boolean showAllRunes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "eliteDiary",
		name = "Elite Kourend diary done",
		description = "Tick this if you finished the elite Kourend & Kebos diary (+10% blood runes at the Arceuus altar)",
		position = 2
	)
	default boolean eliteDiary()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showBonuses",
		name = "Show bonuses",
		description = "Show active rune bonuses (Raiments, lantern, blood essence, diary)",
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
		position = 6
	)
	default boolean resetOnIdle()
	{
		return false;
	}
}
