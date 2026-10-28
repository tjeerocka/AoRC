package com.aorc;

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "AoRC",
	description = "Amount of Runes Created - counts runes crafted per hour (blood runes first), RC xp/hr and active rune bonuses",
	tags = {"aorc", "runecraft", "runecrafting", "blood", "runes", "rc", "arceuus"}
)
public class AorcPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private AorcConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private AorcOverlay overlay;

	@Getter
	private final RuneSession session = new RuneSession();

	// What we saw in the inventory last time (runes + essence blocks only)
	private Map<Integer, Integer> lastInventory;

	// Gains seen this tick, checked against xp on the next GameTick
	private final Map<Integer, Integer> pendingGains = new HashMap<>();

	private int lastRcXp = -1;
	private int tick;
	private int lastXpTick = -100;

	// Current gear state, shown in the overlay
	@Getter
	private int raimentsPieces;
	@Getter
	private BonusCalculator.Lantern lantern = BonusCalculator.Lantern.NONE;
	@Getter
	private boolean bloodEssenceActive;
	@Getter
	private int lastCraftedRune = RuneIds.BLOOD_RUNE;

	@Provides
	AorcConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AorcConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		clearTracking();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		session.reset();
		clearTracking();
	}

	public void resetSession()
	{
		session.reset();
	}

	public long idleTimeoutMillis()
	{
		return config.idleTimeout() * 60_000L;
	}

	private void clearTracking()
	{
		lastInventory = null;
		pendingGains.clear();
		lastRcXp = -1;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			// Inventory and xp are sent again after login; do not count them as crafted.
			clearTracking();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.RUNECRAFT)
		{
			return;
		}

		int xp = event.getXp();
		if (lastRcXp >= 0 && xp > lastRcXp)
		{
			startNewSessionIfIdle();
			session.addXp(xp - lastRcXp, System.currentTimeMillis());
			lastXpTick = tick;
		}
		lastRcXp = xp;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INVENTORY.getId())
		{
			Map<Integer, Integer> now = countTracked(event.getItemContainer());
			bloodEssenceActive = now.getOrDefault(RuneIds.BLOOD_ESSENCE_ACTIVE, 0) > 0;

			if (lastInventory != null)
			{
				for (Map.Entry<Integer, Integer> e : now.entrySet())
				{
					int gain = e.getValue() - lastInventory.getOrDefault(e.getKey(), 0);
					if (gain > 0)
					{
						pendingGains.merge(e.getKey(), gain, Integer::sum);
					}
				}
			}
			lastInventory = now;
		}
		else if (event.getContainerId() == InventoryID.EQUIPMENT.getId())
		{
			updateGear(event.getItemContainer());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;

		if (pendingGains.isEmpty())
		{
			return;
		}

		// Only count gains that came with runecraft xp (this tick or the one before).
		// Bank withdrawals and picking up runes give no xp, so they are ignored.
		if (tick - lastXpTick <= 1)
		{
			long now = System.currentTimeMillis();
			boolean madeRunes = false;

			for (Map.Entry<Integer, Integer> e : pendingGains.entrySet())
			{
				int id = e.getKey();
				int amount = e.getValue();
				if (RuneIds.isRune(id))
				{
					session.addRunes(id, amount, now);
					lastCraftedRune = id;
					madeRunes = true;
				}
				else if (id == RuneIds.DARK_ESSENCE_BLOCK)
				{
					// Dense -> dark at the Dark Altar (2.5 xp each, already in the xp total)
					session.addBlocksImbued(amount, now);
				}
			}

			if (madeRunes)
			{
				session.addCraft(now);
			}
		}

		pendingGains.clear();
	}

	private void startNewSessionIfIdle()
	{
		if (config.resetOnIdle() && session.isIdle(System.currentTimeMillis(), idleTimeoutMillis()))
		{
			session.reset();
		}
	}

	private static Map<Integer, Integer> countTracked(ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		if (container == null)
		{
			return counts;
		}
		for (Item item : container.getItems())
		{
			int id = item.getId();
			if (RuneIds.isRune(id)
				|| id == RuneIds.DARK_ESSENCE_BLOCK
				|| id == RuneIds.BLOOD_ESSENCE_ACTIVE)
			{
				counts.merge(id, item.getQuantity(), Integer::sum);
			}
		}
		return counts;
	}

	private void updateGear(ItemContainer equipment)
	{
		int pieces = 0;
		BonusCalculator.Lantern lit = BonusCalculator.Lantern.NONE;

		if (equipment != null)
		{
			for (Item item : equipment.getItems())
			{
				int id = item.getId();
				if (id <= 0)
				{
					continue;
				}

				switch (id)
				{
					case RuneIds.LANTERN_BLISTERWOOD:
						lit = BonusCalculator.Lantern.BLISTERWOOD;
						continue;
					case RuneIds.LANTERN_MAGIC:
						lit = BonusCalculator.Lantern.MAGIC;
						continue;
					case RuneIds.LANTERN_WILLOW:
						lit = BonusCalculator.Lantern.WILLOW;
						continue;
					case RuneIds.LANTERN_REDWOOD:
						lit = BonusCalculator.Lantern.REDWOOD;
						continue;
					default:
						break;
				}

				// Raiments have colour variants, so match by name: "Hat of the Eye (red)" etc.
				String name = client.getItemDefinition(id).getName();
				if (name != null && name.contains("of the Eye") && !name.startsWith("Amulet"))
				{
					pieces++;
				}
			}
		}

		raimentsPieces = Math.min(4, pieces);
		lantern = lit;
	}

	/** Bonuses for the rune type you crafted last. */
	public List<BonusCalculator.Bonus> currentBonuses()
	{
		// Blood-only bonuses (diary, blisterwood) only count once blood runes were crafted this session
		boolean bloodCrafted = session.getRunes(RuneIds.BLOOD_RUNE) > 0;

		BonusCalculator.Lantern usedLantern = lantern;
		if (lantern == BonusCalculator.Lantern.BLISTERWOOD && (!config.blisterwoodLantern() || !bloodCrafted))
		{
			usedLantern = BonusCalculator.Lantern.NONE;
		}

		return BonusCalculator.activeBonuses(
			lastCraftedRune == RuneIds.BLOOD_RUNE,
			raimentsPieces,
			usedLantern,
			bloodEssenceActive,
			config.eliteDiary() && bloodCrafted);
	}
}
