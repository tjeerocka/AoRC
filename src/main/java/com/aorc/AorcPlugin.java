package com.aorc;

import com.google.gson.Gson;
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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.events.RuneScapeProfileChanged;
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

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	private AorcPanel panel;
	private NavigationButton navButton;
	private boolean navShown;

	/** ZMI (Ourania): one craft gives several rune types. Turns on the side panel. */
	@Getter
	private boolean zmiMode;

	private static final String KEY_SESSION = "savedSession";
	private static final String KEY_TOTALS = "savedTotals";

	/** Per-hour numbers (rate timer, xp, runes for /hr, GP). */
	@Getter
	private final RuneSession session = new RuneSession();

	/** Rune counts and bonus runes, each with its own reset. */
	@Getter
	private final RuneTotals totals = new RuneTotals();

	// true when something changed that should be saved
	private boolean dirty;

	// Accounts already loaded since the client was opened
	private final java.util.Set<String> profilesThisRun = new java.util.HashSet<>();

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

	/** Elite Kourend &amp; Kebos diary done, read from the game (varbit 7928 = 1). */
	@Getter
	private boolean eliteDiaryDone;

	@Provides
	AorcConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AorcConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);

		panel = injector.getInstance(AorcPanel.class);
		navButton = NavigationButton.builder()
			.tooltip("AoRC - Amount of Runes Created")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		updateNav();

		clearTracking();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			load();
		}
	}

	@Override
	protected void shutDown()
	{
		save();
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		navShown = false;
		session.reset();
		totals.resetRunes();
		totals.resetBonus();
		clearTracking();
	}

	/** Reset Blood/hr, RC xp/hr and GP/hr (the timer starts again). */
	public void resetRates()
	{
		session.reset();
		save();
	}

	/** Reset the rune counts (Blood runes and other rune types). */
	public void resetRuneCount()
	{
		totals.resetRunes();
		zmiMode = false;
		updateNav();
		save();
	}

	/** Reset the bonus runes count. */
	public void resetBonusRunes()
	{
		totals.resetBonus();
		save();
	}

	public void resetAll()
	{
		session.reset();
		totals.resetRunes();
		totals.resetBonus();
		zmiMode = false;
		updateNav();
		save();
	}

	// ---- saving between sessions (per account) ----

	private void save()
	{
		dirty = false;
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		configManager.setRSProfileConfiguration(AorcConfig.GROUP, KEY_SESSION, gson.toJson(session.save(now)));
		configManager.setRSProfileConfiguration(AorcConfig.GROUP, KEY_TOTALS, gson.toJson(totals.save()));
	}

	private void load()
	{
		session.setIdleTimeoutMillis(idleTimeoutMillis());

		String profile = configManager.getRSProfileKey();
		boolean firstTimeThisRun = profile != null && profilesThisRun.add(profile);
		if (firstTimeThisRun && !config.rememberSession())
		{
			// "Remember after closing" is off: start fresh once per client start
			session.reset();
			totals.load(null);
			save();
			return;
		}

		try
		{
			String s = configManager.getRSProfileConfiguration(AorcConfig.GROUP, KEY_SESSION);
			session.load(s == null ? null : gson.fromJson(s, RuneSession.Saved.class));
			String t = configManager.getRSProfileConfiguration(AorcConfig.GROUP, KEY_TOTALS);
			totals.load(t == null ? null : gson.fromJson(t, RuneTotals.Saved.class));
			detectZmiFromTotals();
		}
		catch (RuntimeException e)
		{
			// Broken saved data: start clean instead of crashing
			session.reset();
			totals.load(null);
		}
		dirty = false;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!AorcConfig.GROUP.equals(event.getGroup()) || !"showSidebar".equals(event.getKey()))
		{
			return;
		}
		updateNav();
	}

	/** Side panel button only while crafting at ZMI (and the option is on). */
	private void updateNav()
	{
		if (navButton == null)
		{
			return;
		}
		boolean want = zmiMode && config.showSidebar();
		if (want && !navShown)
		{
			clientToolbar.addNavigation(navButton);
		}
		else if (!want && navShown)
		{
			clientToolbar.removeNavigation(navButton);
		}
		navShown = want;
	}

	/** ZMI if the saved counts have many rune types (Ourania gives random runes). */
	private void detectZmiFromTotals()
	{
		zmiMode = totals.getRunes().size() >= 3;
		updateNav();
	}

	/** Snapshot for the side panel. Runs on the game thread. */
	private PanelData buildPanelData()
	{
		long now = System.currentTimeMillis();
		long elapsed = session.elapsedMillis(now, idleTimeoutMillis());

		PanelData d = new PanelData();
		d.activeMillis = elapsed;
		d.xpPerHour = RuneSession.perHour(session.getXpGained(), elapsed);
		d.allRunesPerHour = RuneSession.perHour(session.getTotalRunes(), elapsed);
		d.crafts = session.getCrafts();
		d.blocksImbued = session.getBlocksImbued();
		d.bonusPercent = BonusCalculator.totalPercent(currentBonuses());
		d.bonusRunes = totals.getBonusRunes();

		long gp = 0;
		for (Map.Entry<Integer, Integer> e : session.getRunesCrafted().entrySet())
		{
			gp += (long) itemManager.getItemPrice(e.getKey()) * e.getValue();
		}
		d.gpPerHour = RuneSession.perHour(gp, elapsed);

		java.util.Set<Integer> types = new java.util.LinkedHashSet<>(totals.getRunes().keySet());
		types.addAll(session.getRunesCrafted().keySet());
		for (int id : types)
		{
			d.rows.add(new PanelData.Row(id, RuneIds.RUNES.get(id), totals.getRunes(id),
				RuneSession.perHour(session.getRunes(id), elapsed)));
		}
		PanelData.sortRows(d.rows);
		return d;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// Logged in (or switched account): load that account's saved numbers
		load();
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
			if (dirty)
			{
				save();
			}
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
			dirty = true;
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
		session.setIdleTimeoutMillis(idleTimeoutMillis());
		eliteDiaryDone = client.getVarbitValue(RuneIds.VARBIT_DIARY_KOUREND_ELITE) == 1;

		// Update the side panel about once a second
		if (panel != null && tick % 2 == 0)
		{
			panel.post(buildPanelData());
		}

		// Save about every 10 seconds when something changed
		if (dirty && tick % 16 == 0)
		{
			save();
		}

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

			int runeTypes = 0;
			for (int id : pendingGains.keySet())
			{
				if (RuneIds.isRune(id))
				{
					runeTypes++;
				}
			}
			if (runeTypes >= 2 && !zmiMode)
			{
				// Several rune types from one craft = ZMI altar
				zmiMode = true;
				updateNav();
			}

			for (Map.Entry<Integer, Integer> e : pendingGains.entrySet())
			{
				int id = e.getKey();
				int amount = e.getValue();
				if (RuneIds.isRune(id))
				{
					session.addRunes(id, amount, now);
					lastCraftedRune = id;
					// Bonus % right now, for this rune type
					// At ZMI the blood-only bonuses (essence, diary, blisterwood) do not work
					boolean bloodAltar = id == RuneIds.BLOOD_RUNE && runeTypes < 2;
					int pct = BonusCalculator.totalPercent(BonusCalculator.activeBonuses(
						bloodAltar, raimentsPieces, lantern, bloodEssenceActive, eliteDiaryDone));
					totals.addCraft(id, amount, pct);
					madeRunes = true;
					dirty = true;
				}
				else if (id == RuneIds.DARK_ESSENCE_BLOCK)
				{
					// Dense -> dark at the Dark Altar (2.5 xp each, already in the xp total)
					session.addBlocksImbued(amount, now);
					dirty = true;
				}
			}

			if (madeRunes)
			{
				session.addCraft(now);
			}
			if (dirty)
			{
				save();
			}
		}

		pendingGains.clear();
	}

	private void startNewSessionIfIdle()
	{
		if (config.resetOnIdle() && session.isIdle(System.currentTimeMillis(), idleTimeoutMillis()))
		{
			// Only the per-hour numbers start again; rune and bonus counts stay
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
		if (lantern == BonusCalculator.Lantern.BLISTERWOOD && !bloodCrafted)
		{
			usedLantern = BonusCalculator.Lantern.NONE;
		}

		return BonusCalculator.activeBonuses(
			// At ZMI only the all-rune bonuses count
			lastCraftedRune == RuneIds.BLOOD_RUNE && !zmiMode,
			raimentsPieces,
			usedLantern,
			bloodEssenceActive,
			eliteDiaryDone && bloodCrafted);
	}
}
