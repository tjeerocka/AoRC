package com.aorc;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "AoRC",
	description = "Amount of Runes Crafted - counts runes crafted per hour (blood runes first), RC xp/hr and active rune bonuses",
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
	private SpitOverlay spitOverlay;

	@Inject
	private PouchOverlay pouchOverlay;

	@Inject
	private RepairSpellOverlay repairSpellOverlay;


	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Notifier notifier;


	private AorcPanel panel;
	private NavigationButton navButton;
	private boolean navShown;

	/** ZMI (Ourania): one craft gives several rune types. Changes the overlay and bonuses. */
	@Getter
	private boolean zmiMode;
	// Paused: the clocks stand still and nothing is counted until Resume
	@Getter
	private volatile boolean paused;


	/** "Your blood essence has 940 charges remaining." */
	private static final Pattern ESSENCE_CHECK = Pattern.compile("Your blood essence has ([\\d,]+) charges? remaining");

	/** Trip stats (reset with "Reset per hour"). */
	private final TripTracker trips = new TripTracker();

	/** Charges left on the active blood essence. */
	private final EssenceCharges essence = new EssenceCharges();

	/** Latest numbers, also used by the overlay. Made on the game thread. */
	@Getter
	private PanelData lastData = new PanelData();

	// Dark essence fragments were used up this tick (= Arceuus blood altar)
	private boolean fragmentsUsed;
	// The inventory just ran out of essence (= end of a trip), handled on the next tick
	private boolean essenceEmptied;
	// Last blood craft was at the Arceuus altar (diary bonus only works there)
	private boolean lastBloodAtArceuus = true;
	// trip stats per place (Blood - Zeah, Blood - Mory, Lava - Fire altar, ...)
	private final TripLocations places = new TripLocations();
	// guardian essence used this tick (Guardians of the Rift)
	private boolean guardianUsed;
	// blocks imbued at the Dark Altar since your last craft
	private boolean imbuedSinceCraft;
	// map area of your last craft: moving to another area = you left the altar
	private int craftRegion = -1;
	// essence pouch wear
	private final PouchTracker pouches = new PouchTracker();
	private final java.util.Set<PouchTracker.Pouch> pouchesCarried = java.util.EnumSet.noneOf(PouchTracker.Pouch.class);
	private PouchTracker.Pouch emptyPouch;  // pouch you just clicked "Empty" on
	private int emptyTick;
	private static final int BANK_GROUP = 12;          // bank interface
	private static final int DEPOSIT_BOX_GROUP = 192;  // bank deposit box
	private boolean noPouchWear;
	// talking to the Dark Mage while a pouch needs a repair: highlight what to click
	private volatile boolean repairGuide;            // Runecraft / max cape or redwood lantern: pouches do not decay
	// Inactive blood essence in the inventory
	private int inactiveEssence;
	// Most Raiments pieces seen worn (for the "piece missing" warning)
	private int maxRaimentsSeen;
	// Next time Runey spits on the character (mains), -1 = not planned
	private long nextGameSpit = -1;
	// Last time each warning was shown
	private final Map<String, Long> lastWarn = new HashMap<>();

	/** Runes and time per day (best day, today, all time). Never reset by "Reset all". */
	private final DayLog history = new DayLog();

	/** Per-hour numbers (rate timer, xp, runes for /hr, GP). */
	@Getter
	private final RuneSession session = new RuneSession();

	/** Rune counts and bonus runes, each with its own reset. */
	@Getter
	private final RuneTotals totals = new RuneTotals();

	// true when something changed that should be saved
	private boolean dirty;

	// Own save file per account in .runelite/aorc/
	private final AorcStorage storage = new AorcStorage(RuneLite.RUNELITE_DIR);
	// Account whose numbers are loaded, -1 = none yet
	private long loadedAccount = -1;

	// Accounts already loaded since the client was opened
	private final java.util.Set<String> profilesThisRun = new java.util.HashSet<>();

	// What we saw in the inventory last time (runes + essence blocks only)
	private Map<Integer, Integer> lastInventory;

	// Gains seen this tick, checked against xp on the next GameTick
	private final Map<Integer, Integer> pendingGains = new HashMap<>();

	private int lastRcXp = -1;
	private int tick;
	// runes crafted towards the rune goal, per type; only "Clear goal progress" resets it
	private final Map<Integer, Integer> goalRunes = new java.util.LinkedHashMap<>();
	// level goal bar: starts at the start of the level you had when you set the goal
	private int goalStartLevel = -1;
	private int goalStartXp = -1;

	/** Game tick counter (the overlay uses it to work out its lines once per tick). */
	int getTick()
	{
		return tick;
	}
	private int lastXpTick = -100;

	// Current gear state, shown in the overlay
	private int raimentsPieces;
	private BonusCalculator.Lantern lantern = BonusCalculator.Lantern.NONE;
	private boolean bloodEssenceActive;
	private int lastCraftedRune = RuneIds.BLOOD_RUNE;

	/** Elite Kourend &amp; Kebos diary done, read from the game (varbit 7928 = 1). */
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
		overlayManager.add(spitOverlay);
		overlayManager.add(pouchOverlay);
		overlayManager.add(repairSpellOverlay);

		panel = injector.getInstance(AorcPanel.class);
		navButton = buildNav(ImageUtil.loadImageResource(getClass(), "icon.png"));
		updateNav();

		// Use the real blood rune icon from the game for the sidebar button once it is loaded
		net.runelite.client.util.AsyncBufferedImage blood = itemManager.getImage(RuneIds.BLOOD_RUNE);
		blood.onLoaded(() -> useGameIcon(blood));
		useGameIcon(blood); // in case it was already loaded

		clearTracking();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loadForAccount();
		}
	}

	@Override
	protected void shutDown()
	{
		save();
		overlayManager.remove(overlay);
		overlayManager.remove(spitOverlay);
		overlayManager.remove(pouchOverlay);
		overlayManager.remove(repairSpellOverlay);
		if (panel != null)
		{
			panel.stop();
		}
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
		trips.reset();
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

	/** Pause or resume tracking (call on the client thread). */
	public void togglePause()
	{
		long now = System.currentTimeMillis();
		if (!paused)
		{
			paused = true;
			session.pause(now);
			trips.pause(now);
			history.pause();
		}
		else
		{
			paused = false;
			trips.resume(now);
			// the clocks run again right away (not only after the next xp drop)
			session.addActivity(now);
			activity();
		}
		dirty = true;
		lastData = buildPanelData();
		if (panel != null)
		{
			panel.post(lastData);
		}
	}

	/** Side panel sections: open (true) or folded, remembered in the RuneLite settings. */
	boolean isSectionOpen(String key)
	{
		String v = configManager.getConfiguration(AorcConfig.GROUP, "open." + key);
		return v == null || Boolean.parseBoolean(v);
	}

	void setSectionOpen(String key, boolean open)
	{
		configManager.setConfiguration(AorcConfig.GROUP, "open." + key, open);
	}

	/** New numbers for the side panel right away (for example after changing the History filter). */
	public void refreshPanel()
	{
		lastData = buildPanelData();
		if (panel != null)
		{
			panel.post(lastData);
		}
	}

	/** Start counting the blood rune goal from 0 again (the resets do not touch it). */
	public void clearGoalProgress()
	{
		goalRunes.clear();
		save();
	}

	/** Clear all trip numbers, also the records per place. */
	public void resetTrips()
	{
		trips.reset();
		places.reset();
		save();
		refreshPanel();
	}

	public void resetAll()
	{
		session.reset();
		trips.reset();
		places.reset();
		totals.resetRunes();
		totals.resetBonus();
		zmiMode = false;
		updateNav();
		save();
	}

	// ---- saving between sessions (per account) ----

	/** Write everything to .runelite/aorc/<account>.json (only after the account is loaded). */
	private void save()
	{
		dirty = false;
		if (loadedAccount == -1)
		{
			return;
		}
		AorcStorage.Data d = new AorcStorage.Data();
		d.session = session.save(System.currentTimeMillis());
		d.totals = totals.save();
		d.history = history.save();
		d.trips = trips.save();
		d.tripPlaces = places.save();
		d.pouches = pouches.save();
		d.essence = essence.save();
		d.goalRunes = new java.util.LinkedHashMap<>(goalRunes);
		d.goalStartLevel = goalStartLevel;
		d.goalStartXp = goalStartXp;
		try
		{
			storage.write(loadedAccount, gson.toJson(d));
		}
		catch (IOException | RuntimeException e)
		{
			// Disk problem: try again on the next save
			dirty = true;
		}
	}

	private static String today()
	{
		return java.time.LocalDate.now().toString();
	}

	/** Something runecrafting-related happened (counts toward time spent). */
	private void activity()
	{
		history.activity(today(), System.currentTimeMillis(), idleTimeoutMillis());
		dirty = true;
	}

	/**
	 * Load the saved numbers for the logged-in account.
	 * Only once per account: a world hop or a second login keeps what is in memory (that is newer).
	 */
	private void loadForAccount()
	{
		long hash = client.getAccountHash();
		if (hash == -1 || hash == loadedAccount)
		{
			return;
		}
		if (loadedAccount != -1 && dirty)
		{
			save(); // other account was logged in before: save that one first
		}
		loadedAccount = hash;
		paused = false;
		session.setIdleTimeoutMillis(idleTimeoutMillis());

		AorcStorage.Data d = null;
		try
		{
			String json = storage.read(hash);
			if (json != null)
			{
				d = gson.fromJson(json, AorcStorage.Data.class);
			}
		}
		catch (IOException | RuntimeException e)
		{
			d = null;
		}
		if (d == null)
		{
			d = new AorcStorage.Data();
		}

		// History, essence charges and goal progress are always kept
		history.load(d.history);
		essence.load(d.essence);
		places.load(d.tripPlaces);
		pouches.load(d.pouches);
		goalStartLevel = d.goalStartLevel != null ? d.goalStartLevel : -1;
		goalStartXp = d.goalStartXp != null ? d.goalStartXp : -1;
		// older files have no goal counter yet: start from the rune counts
		goalRunes.clear();
		if (d.goalRunes != null)
		{
			goalRunes.putAll(d.goalRunes);
		}
		else if (d.totals != null && d.totals.runes != null)
		{
			goalRunes.putAll(d.totals.runes);
		}

		boolean fresh = profilesThisRun.add(String.valueOf(hash)) && !config.rememberSession();
		if (fresh)
		{
			// "Remember after closing" is off: start fresh once per client start
			session.reset();
			totals.load(null);
			trips.reset();
		}
		else
		{
			session.load(d.session);
			totals.load(d.totals);
			trips.load(d.trips);
		}
		detectZmiFromTotals();
		dirty = fresh;
	}

	/** Spit on the character: from the side panel if Runey is visible there, otherwise he peeks in. */
	private void spitNow()
	{
		boolean iron = AccountType.fromVarbit(client.getVarbitValue(AccountType.VARBIT)) != AccountType.MAIN;
		boolean fromPanel = panel != null && panel.isBuddyShowing();
		if (fromPanel)
		{
			panel.spitAcross(iron);
		}
		spitOverlay.spit(fromPanel);
	}

	/** Mains: Runey spits on the character 1 minute after login, then every 30 minutes. */
	private void maybeSpitOnPlayer()
	{
		if (!config.spitOnPlayer()
			|| AccountType.fromVarbit(client.getVarbitValue(AccountType.VARBIT)) != AccountType.MAIN)
		{
			nextGameSpit = -1;
			return;
		}
		long now = System.currentTimeMillis();
		if (nextGameSpit < 0)
		{
			nextGameSpit = now + 60_000L;
		}
		else if (now >= nextGameSpit)
		{
			spitNow();
			nextGameSpit = now + 30 * 60_000L;
		}
	}

	private NavigationButton buildNav(java.awt.image.BufferedImage icon)
	{
		return NavigationButton.builder()
			.tooltip("AoRC - Amount of Runes Crafted")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
	}

	/** Swap the sidebar icon for the game's blood rune (cut to the stone, 16x16). */
	private void useGameIcon(java.awt.image.BufferedImage rune)
	{
		java.awt.image.BufferedImage icon = toSidebarIcon(rune);
		if (icon == null || navButton == null)
		{
			return; // not loaded yet
		}
		NavigationButton old = navButton;
		navButton = buildNav(icon);
		if (navShown)
		{
			clientToolbar.removeNavigation(old);
			clientToolbar.addNavigation(navButton);
		}
	}

	/** Crop an item icon to its visible pixels and scale it to 16x16. Null if empty. */
	static java.awt.image.BufferedImage toSidebarIcon(java.awt.image.BufferedImage img)
	{
		if (img == null)
		{
			return null;
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < img.getHeight(); y++)
		{
			for (int x = 0; x < img.getWidth(); x++)
			{
				if ((img.getRGB(x, y) >>> 24) > 40)
				{
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (maxX < 0)
		{
			return null;
		}
		int w = maxX - minX + 1;
		int h = maxY - minY + 1;
		int side = Math.max(w, h);
		java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
		// keep it square and centred
		int dx = (side - w) / 2;
		int dy = (side - h) / 2;
		g.drawImage(img, dx * 16 / side, dy * 16 / side, (dx + w) * 16 / side, (dy + h) * 16 / side,
			minX, minY, maxX + 1, maxY + 1, null);
		g.dispose();
		return out;
	}

	/** The side panel is always shown. */
	private void updateNav()
	{
		if (navButton != null && !navShown)
		{
			clientToolbar.addNavigation(navButton);
			navShown = true;
		}
	}

	/** ZMI if the saved counts have many rune types (Ourania gives random runes). */
	/**
	 * ZMI mode follows your last craft (several rune types in one craft = ZMI).
	 * Not from the saved counts: crafting blood, water and earth on different days is not ZMI.
	 */
	private void detectZmiFromTotals()
	{
		zmiMode = false;
		updateNav();
	}

	/** Snapshot for the side panel. Runs on the game thread. */
	private PanelData buildPanelData()
	{
		long now = System.currentTimeMillis();
		long elapsed = session.elapsedMillis(now, idleTimeoutMillis());

		PanelData d = new PanelData();
		d.paused = paused;
		d.crafting = session.isStarted() && !session.isIdle(now, idleTimeoutMillis()) && !session.getRunesCrafted().isEmpty();
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

		// Profit: GP minus the blood essence used (1 charge per extra rune, 1,000 per essence)
		d.essenceUsed = session.getEssenceRunes();
		long essenceCost = Math.round(d.essenceUsed / EssenceCharges.FULL * itemManager.getItemPrice(RuneIds.BLOOD_ESSENCE));
		d.profitPerHour = RuneSession.perHour(gp - essenceCost, elapsed);
		d.essenceCharges = bloodEssenceActive ? essence.remaining() : -1;
		d.essenceEstimate = essence.isEstimate();

		// Trips: all trips, or one place (dropdown in the side panel; "" = where you craft now)
		d.tripPlaces = places.names();
		d.currentPlace = places.getCurrentOrLast();
		String place = panel == null ? "" : panel.getTripFilter();
		if ("".equals(place))
		{
			place = places.getLast();
		}
		if (place == null || "*".equals(place))
		{
			d.tripPlace = null;
			d.trips = trips.getTrips();
			d.avgTripRunes = trips.getAvgRunes();
			d.avgTripMillis = trips.getAvgTripMillis();
			d.fastestTripMillis = trips.getFastestMillis();
			d.lastTripMillis = trips.getLastTripMillis();
		}
		else
		{
			TripLocations.Stats p = places.get(place);
			d.tripPlace = place;
			d.trips = p.getTrips();
			d.avgTripRunes = p.getAvgRunes();
			d.avgTripMillis = p.getAvgMillis();
			d.fastestTripMillis = p.getFastestMillis();
			d.lastTripMillis = p.getLastMillis();
		}
		d.showBlocks = isAtZeah();
		// essence pouches you carry: part used since the last repair
		for (PouchTracker.Pouch p : pouchesCarried)
		{
			d.pouchNames.add(p.getName());
			d.pouchUsed.add(pouches.used(p));
		}
		d.currentTripMillis = trips.getCurrentMillis(now);
		d.currentTripRunes = trips.getCurrentRunes();

		// Goals
		int xp = client.getSkillExperience(Skill.RUNECRAFT);
		d.goalLevel = config.goalLevel();
		d.currentLevel = client.getRealSkillLevel(Skill.RUNECRAFT);
		d.xpLeft = Math.max(0, Goals.xpForLevel(d.goalLevel) - xp);
		d.goalLevelMillis = Goals.millisToGoal(d.xpLeft, d.xpPerHour);
		GoalRune goalType = config.goalRuneType() == null ? GoalRune.BLOOD : config.goalRuneType();
		d.goalRunes = config.goalRunes();
		d.goalRuneName = goalType.shortName();
		int goalDone = 0;
		for (Map.Entry<Integer, Integer> e : goalRunes.entrySet())
		{
			if (goalType == GoalRune.ANY || e.getKey() == goalType.getItemId())
			{
				goalDone += e.getValue();
			}
		}
		d.runesLeft = Math.max(0, d.goalRunes - goalDone);
		d.runeGoalProgress = d.goalRunes <= 0 ? 0 : Math.min(1.0, goalDone / (double) d.goalRunes);
		// Level bar: from where you started this goal to the goal level (not from level 1).
		// A new goal level (or no start yet) starts the bar at the start of your current level.
		if (xp > 0 && (goalStartLevel != d.goalLevel || goalStartXp < 0 || goalStartXp > xp))
		{
			goalStartLevel = d.goalLevel;
			goalStartXp = Goals.xpForLevel(Math.max(1, d.currentLevel));
			dirty = true;
		}
		d.levelProgress = Goals.progress(xp, goalStartXp, Goals.xpForLevel(d.goalLevel));
		long goalRate = goalType == GoalRune.ANY ? session.getTotalRunes() : session.getRunes(goalType.getItemId());
		d.goalRunesMillis = Goals.millisToGoal(d.runesLeft, RuneSession.perHour(goalRate, elapsed));

		java.util.Set<Integer> types = new java.util.LinkedHashSet<>(totals.getRunes().keySet());
		types.addAll(session.getRunesCrafted().keySet());
		for (int id : types)
		{
			d.rows.add(new PanelData.Row(id, RuneIds.RUNES.get(id), totals.getRunes(id),
				RuneSession.perHour(session.getRunes(id), elapsed)));
		}
		PanelData.sortRows(d.rows);

		// Live part: time since the last action, so the timers tick every second
		String today = today();
		long live = history.liveMillis(now, idleTimeoutMillis());

		// History filter (dropdown in the side panel): 0 = all runes, else one rune type.
		// Time is only kept per day (not per rune type), so it shows for "All" only.
		int f = panel == null ? 0 : panel.getHistoryFilter();
		d.historyFilter = f;
		DayLog.Day t = history.get(today);
		d.todayRunes = t.runesOf(f);
		d.todayMillis = f == 0 ? t.getActiveMillis() + live : -1;
		String best = history.bestDate(f);
		if (best != null)
		{
			DayLog.Day b = history.get(best);
			d.bestDate = best;
			d.bestRunes = b.runesOf(f);
			d.bestMillis = f == 0 ? b.getActiveMillis() + (best.equals(today) ? live : 0) : -1;
		}
		DayLog.Day all = history.total();
		d.allRunes = all.runesOf(f);
		d.allMillis = f == 0 ? all.getActiveMillis() + live : -1;
		d.days = history.dayCount(f);

		// Top card: the rune you craft now. Blood when you craft blood, else your most crafted
		// rune this session, else the last rune you crafted. ZMI: all runes together.
		if (zmiMode)
		{
			d.mainName = "All runes";
			d.mainPerHour = d.allRunesPerHour;
			int count = 0;
			for (int v : totals.getRunes().values())
			{
				count += v;
			}
			d.mainCount = count;
			d.mainToday = t.getRunes();
		}
		else
		{
			int main = -1;
			if (session.getRunes(RuneIds.BLOOD_RUNE) > 0)
			{
				main = RuneIds.BLOOD_RUNE;
			}
			else
			{
				int most = 0;
				for (Map.Entry<Integer, Integer> e : session.getRunesCrafted().entrySet())
				{
					if (e.getValue() > most)
					{
						most = e.getValue();
						main = e.getKey();
					}
				}
			}
			if (main < 0 && totals.getRunes(lastCraftedRune) > 0)
			{
				main = lastCraftedRune;
			}
			if (main > 0)
			{
				d.mainRuneId = main;
				d.mainName = RuneIds.RUNES.get(main);
				d.mainPerHour = RuneSession.perHour(session.getRunes(main), elapsed);
				d.mainCount = totals.getRunes(main);
				d.mainToday = t.runesOf(main);
			}
		}
		// history per rune type: rune list order, older untyped runes last
		Map<Integer, Integer> todayTypes = t.getTypes();
		for (int id : RuneIds.RUNES.keySet())
		{
			int n = all.getTypes().getOrDefault(id, 0);
			if (n > 0)
			{
				d.historyTypes.add(new PanelData.TypeRow(id, RuneIds.RUNES.get(id), n, todayTypes.getOrDefault(id, 0)));
			}
		}
		int other = all.getTypes().getOrDefault(DayLog.OTHER, 0);
		if (other > 0)
		{
			d.historyTypes.add(new PanelData.TypeRow(DayLog.OTHER, "Other (older)", other, todayTypes.getOrDefault(DayLog.OTHER, 0)));
		}
		d.showBuddy = config.showBuddy();
		d.accountType = AccountType.fromVarbit(client.getVarbitValue(AccountType.VARBIT));
		return d;
	}

	// High priority: run before RuneLite writes its settings file on close,
	// so our last numbers are in that file
	@Subscribe(priority = 1000)
	public void onClientShutdown(ClientShutdown event)
	{
		// RuneLite is closing: if the last craft just happened (same tick),
		// finish that trip first, then save everything
		if (essenceEmptied)
		{
			int runes = 0;
			for (Map.Entry<Integer, Integer> e : pendingGains.entrySet())
			{
				if (RuneIds.isRune(e.getKey()))
				{
					runes += e.getValue();
				}
			}
			trips.onCraft(runes);
			essenceEmptied = false;
			tripEvent(() -> trips.onEssenceEmpty(System.currentTimeMillis()));
		}
		save();
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
		if (state == GameState.LOGGED_IN)
		{
			// Logged in: load this account's numbers (only the first time)
			loadForAccount();
		}
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			fragmentsUsed = false;
			essenceEmptied = false;
			nextGameSpit = -1;
			if (dirty)
			{
				save();
			}
			// Inventory and xp are sent again after login; do not count them as crafted.
			clearTracking();
			history.pause();
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
		if (paused && lastRcXp >= 0 && xp > lastRcXp)
		{
			// Runecraft xp while paused: resume by itself, and count this xp
			togglePause();
		}
		if (lastRcXp >= 0 && xp > lastRcXp && !paused)
		{
			startNewSessionIfIdle();
			session.addXp(xp - lastRcXp, System.currentTimeMillis());
			history.addXp(today(), xp - lastRcXp);
			activity();
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
			inactiveEssence = now.getOrDefault(RuneIds.BLOOD_ESSENCE, 0);

			if (lastInventory == null && essenceCount(now) == 0)
			{
				// Logged in with no essence: the next trip is a full trip
				tripEvent(() -> trips.onStartEmpty(System.currentTimeMillis()));
			}

			if (lastInventory != null)
			{
				int activeBefore = lastInventory.getOrDefault(RuneIds.BLOOD_ESSENCE_ACTIVE, 0);
				int activeNow = now.getOrDefault(RuneIds.BLOOD_ESSENCE_ACTIVE, 0);
				int inactiveBefore = lastInventory.getOrDefault(RuneIds.BLOOD_ESSENCE, 0);
				if (activeNow > activeBefore && inactiveEssence < inactiveBefore)
				{
					// An inactive essence became active: 1,000 charges
					essence.activated();
					dirty = true;
				}
				else if (activeNow < activeBefore && essence.remaining() >= 0 && essence.remaining() <= 150)
				{
					// Gone while almost empty: used up
					essence.empty();
					dirty = true;
				}

				if (essenceCount(lastInventory) > 0 && essenceCount(now) == 0)
				{
					essenceEmptied = true;
				}

				if (now.getOrDefault(RuneIds.DARK_ESSENCE_FRAGMENTS, 0)
					< lastInventory.getOrDefault(RuneIds.DARK_ESSENCE_FRAGMENTS, 0))
				{
					// Fragments used up: crafting at the Arceuus blood altar
					fragmentsUsed = true;
				}
				if (now.getOrDefault(RuneIds.GUARDIAN_ESSENCE, 0)
					< lastInventory.getOrDefault(RuneIds.GUARDIAN_ESSENCE, 0))
				{
					// Guardian essence used up: crafting inside Guardians of the Rift
					guardianUsed = true;
				}

				for (Map.Entry<Integer, Integer> e : now.entrySet())
				{
					int gain = e.getValue() - lastInventory.getOrDefault(e.getKey(), 0);
					if (gain <= 0)
					{
						continue;
					}
					int id = e.getKey();
					if (id == RuneIds.DENSE_ESSENCE_BLOCK || id == RuneIds.DARK_ESSENCE_BLOCK
						|| id == RuneIds.DARK_ESSENCE_FRAGMENTS)
					{
						// Mining, imbuing or chiseling: starts / keeps the Active time timer running
						if (!paused)
						{
							startNewSessionIfIdle();
							session.addActivity(System.currentTimeMillis());
							activity();
						}
					}
					if (id != RuneIds.DENSE_ESSENCE_BLOCK && id != RuneIds.DARK_ESSENCE_FRAGMENTS)
					{
						// Runes and dark blocks are checked against RC xp on the next tick
						pendingGains.merge(id, gain, Integer::sum);
					}
				}
			}
			checkPouches(event.getItemContainer(), now);
			lastInventory = now;
		}
		else if (event.getContainerId() == InventoryID.EQUIPMENT.getId())
		{
			updateGear(event.getItemContainer());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		if (Text.removeTags(event.getMessage()).startsWith("Your pouch has decayed through use"))
		{
			// one of the pouches you carry decayed (the game does not say which): all need a repair
			for (PouchTracker.Pouch p : new java.util.ArrayList<>(pouchesCarried))
			{
				pouches.markDecayed(p);
			}
			dirty = true;
			if (config.warnPouch())
			{
				warn("pouch", "Your pouch has decayed. Repair it with Astral Contact (Dark Mage).");
			}
		}
		Matcher m = ESSENCE_CHECK.matcher(Text.removeTags(event.getMessage()));
		if (m.find())
		{
			try
			{
				essence.setExact(Integer.parseInt(m.group(1).replace(",", "")));
				dirty = true;
			}
			catch (NumberFormatException ignored)
			{
				// not a number: ignore
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;
		session.setIdleTimeoutMillis(idleTimeoutMillis());
		eliteDiaryDone = client.getVarbitValue(RuneIds.VARBIT_DIARY_KOUREND_ELITE) == 1;
		maybeSpitOnPlayer();

		// Fresh numbers every tick (0.6 s) for the overlay and the side panel.
		// Every tick (not every 2nd = 1.2 s), so the timers never skip a second.
		// The panel skips the work while it is closed and only changes label text.
		lastData = buildPanelData();
		if (panel != null)
		{
			panel.post(lastData);
		}

		// Save every 30 seconds when something changed (50 ticks).
		// Crafts, trip ends, logout and closing the client save right away.
		if (dirty && tick % 50 == 0)
		{
			save();
		}

		checkRepairDialogue();
		updateRepairGuide();

		// A trip that ended at the altar is counted when you leave the altar (other map area),
		// or 60 s after the last craft if you stay.
		if (!paused)
		{
			long t = System.currentTimeMillis();
			if (trips.isAtAltar() && craftRegion != -1 && regionId() != -1 && regionId() != craftRegion)
			{
				tripEvent(() -> trips.leaveAltar(t));
			}
			tripEvent(() -> trips.tick(t));
		}

		if (paused)
		{
			// nothing is counted while paused (also no trip end)
			pendingGains.clear();
			fragmentsUsed = false;
			essenceEmptied = false;
			return;
		}

		if (pendingGains.isEmpty())
		{
			fragmentsUsed = false;
			endTripIfEmptied();
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
			boolean arceuusCraft = fragmentsUsed && pendingGains.containsKey(RuneIds.BLOOD_RUNE);
			boolean imbued = false;
			int craftRunes = 0;

			if (runeTypes >= 1 && (runeTypes >= 2) != zmiMode)
			{
				// Several rune types from one craft = ZMI altar; one type = a normal altar
				zmiMode = runeTypes >= 2;
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
					// Elite diary only works at the Arceuus blood altar
					int pct = BonusCalculator.totalPercent(BonusCalculator.activeBonuses(
						bloodAltar, raimentsPieces, lantern, bloodEssenceActive, eliteDiaryDone && arceuusCraft));
					totals.addCraft(id, amount, pct);
					goalRunes.merge(id, amount, Integer::sum);
					craftRunes += amount;
					if (bloodAltar)
					{
						lastBloodAtArceuus = arceuusCraft;
						if (bloodEssenceActive)
						{
							double ess = EssenceCharges.expectedEssenceRunes(amount, pct);
							essence.use(ess);
							session.addEssenceRunes(ess);
						}
					}
					history.addRunes(today(), id, amount);
					madeRunes = true;
					dirty = true;
				}
				else if (id == RuneIds.DARK_ESSENCE_BLOCK)
				{
					// Dense -> dark at the Dark Altar (2.5 xp each, already in the xp total)
					session.addBlocksImbued(amount, now);
					imbued = true;
					dirty = true;
				}
			}

			if (madeRunes)
			{
				session.addCraft(now);
				trips.onCraft(craftRunes);
				// the place of this craft (for trips per place)
				int mainRune = -1;
				int most = 0;
				for (Map.Entry<Integer, Integer> e : pendingGains.entrySet())
				{
					if (RuneIds.isRune(e.getKey()) && e.getValue() > most)
					{
						most = e.getValue();
						mainRune = e.getKey();
					}
				}
				boolean soulAtZeah = fragmentsUsed && pendingGains.containsKey(RuneIds.SOUL_RUNE);
				places.onCraft(TripLocations.placeOf(mainRune, runeTypes, arceuusCraft || soulAtZeah, guardianUsed, regionId()));
				imbuedSinceCraft = false;
				craftRegion = regionId();
			}
			if (imbued)
			{
				imbuedSinceCraft = true;
			}
			if (imbued)
			{
				// At the Dark Altar: check the setup before going to the blood altar
				checkWarnings();
			}
			if (dirty)
			{
				save();
			}
		}

		pendingGains.clear();
		fragmentsUsed = false;
		guardianUsed = false;
		// After the runes of this craft are counted, so they belong to this trip
		endTripIfEmptied();
	}

	/** Run a trip event; when it finished a trip, add that trip to its place too. */
	private void tripEvent(Runnable event)
	{
		int before = trips.getTrips();
		event.run();
		if (trips.getTrips() > before)
		{
			places.onTripDone(trips.getLastTripRunes(), trips.getLastTripMillis());
		}
	}

	/** Map region of your character (-1 when unknown). */
	private int regionId()
	{
		net.runelite.api.Player p = client.getLocalPlayer();
		return p == null || p.getWorldLocation() == null ? -1 : p.getWorldLocation().getRegionID();
	}

	/** At Zeah right now: your last craft was at the Arceuus blood or soul altar, or you just imbued blocks. */
	boolean isAtZeah()
	{
		String last = places.getLast();
		return imbuedSinceCraft || (last != null && last.endsWith("- Zeah"));
	}

	private void endTripIfEmptied()
	{
		if (essenceEmptied)
		{
			essenceEmptied = false;
			tripEvent(() -> trips.onEssenceEmpty(System.currentTimeMillis()));
			// Save right away, so a finished trip is never lost
			save();
		}
	}

	private static int essenceCount(Map<Integer, Integer> inv)
	{
		int n = 0;
		for (Map.Entry<Integer, Integer> e : inv.entrySet())
		{
			if (RuneIds.isTripEssence(e.getKey()))
			{
				n += e.getValue();
			}
		}
		return n;
	}

	private void checkWarnings()
	{
		if (config.warnLantern() && lantern != BonusCalculator.Lantern.BLISTERWOOD)
		{
			warn("lantern", "Blisterwood lantern is not lit / equipped.");
		}
		if (config.warnEssence() && inactiveEssence > 0 && !bloodEssenceActive)
		{
			warn("essence", "Your blood essence is not activated.");
		}
		if (config.warnRaiments() && raimentsPieces < maxRaimentsSeen)
		{
			warn("raiments", "Raiments of the Eye: only " + raimentsPieces + "/" + maxRaimentsSeen + " pieces worn.");
		}
		int left = essence.remaining();
		if (config.warnLowCharges() && bloodEssenceActive && left >= 0 && left <= 100)
		{
			warn("charges", "Blood essence almost empty (about " + left + " charges left).");
		}
	}

	/** Notification + chat message, at most once every 3 minutes per warning. */
	private void warn(String key, String message)
	{
		long now = System.currentTimeMillis();
		Long last = lastWarn.get(key);
		if (last != null && now - last < 180_000L)
		{
			return;
		}
		lastWarn.put(key, now);
		notifier.notify("AoRC: " + message);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff3030>AoRC:</col> " + message, null);
	}

	// ---------- essence pouches ----------

	/** Pouches in your inventory, essence taken out of a pouch, decayed pouches. */
	private void checkPouches(ItemContainer inv, Map<Integer, Integer> now)
	{
		java.util.Set<PouchTracker.Pouch> carried = java.util.EnumSet.noneOf(PouchTracker.Pouch.class);
		boolean changed = false;
		if (inv != null)
		{
			for (Item item : inv.getItems())
			{
				PouchTracker.Pouch p = PouchTracker.of(item.getId());
				if (p == null)
				{
					continue;
				}
				carried.add(p);
				if (PouchTracker.isDegraded(item.getId()) && !pouches.needsRepair(p))
				{
					pouches.markDecayed(p);   // decayed already: needs a repair now
					changed = true;
				}
			}
		}
		pouchesCarried.clear();
		pouchesCarried.addAll(carried);

		// "Empty" clicked on a pouch, and essence came into the inventory right after: that essence
		// was put in that pouch before. Counted when it comes out (not when filling), so it also
		// works when you fill your pouches straight from the bank.
		// If the click was not seen (another menu, a key bind, ...): several essence that appear at
		// once while the bank is closed come out of a pouch too (mining gives them one by one).
		// With one wearing pouch on you (the small pouch does not wear) it is that one.
		PouchTracker.Pouch from = emptyPouch != null && tick - emptyTick <= 3 ? emptyPouch : null;
		boolean guessed = false;
		if (from == null && carried.size() == 1 && !bankOpen())
		{
			from = carried.iterator().next();
			guessed = true;
		}
		if (from != null && lastInventory != null)
		{
			int before = pouchEssence(lastInventory);
			int after = pouchEssence(now);
			if (after > before && (!guessed || after - before >= 2))
			{
				if (!noPouchWear && pouches.addFilled(from, after - before) && config.warnPouch())
				{
					warn("pouch", from.getName() + " is about to decay (25% left). Repair it with Astral Contact (Dark Mage).");
				}
				emptyPouch = null;
				changed = true;
			}
		}
		if (changed)
		{
			dirty = true;
		}
	}

	/** The bank is open (essence that appears then is withdrawn, not taken out of a pouch). */
	private boolean bankOpen()
	{
		net.runelite.api.widgets.Widget bank = client.getWidget(BANK_GROUP, 0);
		net.runelite.api.widgets.Widget box = client.getWidget(DEPOSIT_BOX_GROUP, 0);
		return (bank != null && !bank.isHidden()) || (box != null && !box.isHidden());
	}

	/** Essence that fits in pouches. */
	private static int pouchEssence(Map<Integer, Integer> inv)
	{
		return inv.getOrDefault(RuneIds.RUNE_ESSENCE, 0) + inv.getOrDefault(RuneIds.PURE_ESSENCE, 0)
			+ inv.getOrDefault(RuneIds.DAEYALT_ESSENCE, 0) + inv.getOrDefault(RuneIds.GUARDIAN_ESSENCE, 0);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		PouchTracker.Pouch p = PouchTracker.of(event.getItemId());
		if (p != null && event.getMenuOption() != null && event.getMenuOption().startsWith("Empty"))
		{
			emptyPouch = p;
			emptyTick = tick;
		}
	}

	/** The Dark Mage (or Cordelia) repaired your pouches: his dialogue says so. */
	private void checkRepairDialogue()
	{
		net.runelite.api.widgets.Widget text = client.getWidget(RepairDialog.DIALOG_NPC, RepairDialog.DIALOG_NPC_TEXT);
		if (text == null || text.isHidden() || text.getText() == null)
		{
			return;
		}
		String t = Text.removeTags(text.getText()).toLowerCase();
		if (t.contains("repaired your pouches") || t.contains("transfiguration spell") || t.contains("repaired your pouch"))
		{
			if (pouches.hasAny())
			{
				pouches.repaired();
				dirty = true;
			}
		}
	}

	/**
	 * Test the pouch warning: the pouches you carry (or the colossal pouch) count as worn,
	 * so you see the warning and the highlights. They go away after a repair at the Dark Mage.
	 */
	public void testPouchWarning()
	{
		java.util.Set<PouchTracker.Pouch> which = pouchesCarried.isEmpty()
			? java.util.EnumSet.of(PouchTracker.Pouch.COLOSSAL) : pouchesCarried;
		for (PouchTracker.Pouch p : which)
		{
			pouches.markNeedsRepair(p);
		}
		dirty = true;
		String name = which.iterator().next().getName();
		notifier.notify("AoRC: " + name + " is about to decay (25% left). Repair it with Astral Contact (Dark Mage). (test)");
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=ff3030>AoRC:</col> " + name + " is about to decay (25% left). Repair it with Astral Contact (Dark Mage). (test)", null);
		refreshPanel();
	}

	/**
	 * The repair guide runs from the moment you talk to the Dark Mage with a worn pouch
	 * until the conversation is over (also the lines after the repair).
	 */
	private void updateRepairGuide()
	{
		net.runelite.api.widgets.Widget name = client.getWidget(RepairDialog.DIALOG_NPC, RepairDialog.DIALOG_NPC_NAME);
		net.runelite.api.widgets.Widget line = client.getWidget(RepairDialog.DIALOG_NPC, RepairDialog.DIALOG_NPC_TEXT);
		boolean mageTalks = (name != null && !name.isHidden() && name.getText() != null
			&& Text.removeTags(name.getText()).contains("Dark Mage"))
			// his first line, in case the name is somewhere else in the chat box
			|| (line != null && !line.isHidden() && line.getText() != null
			&& Text.removeTags(line.getText()).toLowerCase().contains("can't you see i'm busy"));
		boolean repairOption = RepairDialog.findOption(client, RepairDialog.REPAIR_OPTION) != null;
		boolean anyDialogue = RepairDialog.findContinue(client) != null || repairOption;

		if (!anyDialogue && !mageTalks)
		{
			repairGuide = false;     // conversation over
		}
		else if ((mageTalks || repairOption) && config.warnPouch() && pouches.anyNeedsRepair(java.util.EnumSet.allOf(PouchTracker.Pouch.class)))
		{
			repairGuide = true;      // started: keep going until the end of the conversation
		}
	}

	/** Every frame: colour what to click orange while the repair guide runs. */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (repairGuide)
		{
			RepairDialog.colourTarget(client, repairColor().getRgb());
		}
	}

	/** Colour of the repair highlights (setting). */
	HighlightColor repairColor()
	{
		HighlightColor c = config.repairColor();
		return c == null ? HighlightColor.DARK_GREEN : c;
	}

	/** An essence pouch with this item ID needs a repair (for the inventory highlight). */
	boolean pouchNeedsRepair(int itemId)
	{
		PouchTracker.Pouch p = PouchTracker.of(itemId);
		return p != null && config.warnPouch() && pouches.needsRepair(p);
	}

	/** A pouch you carry needs a repair (for the spellbook highlight). */
	boolean anyPouchNeedsRepair()
	{
		return config.warnPouch() && pouches.anyNeedsRepair(pouchesCarried);
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
				|| id == RuneIds.DENSE_ESSENCE_BLOCK
				|| RuneIds.isTripEssence(id)
				|| id == RuneIds.BLOOD_ESSENCE_ACTIVE
				|| id == RuneIds.BLOOD_ESSENCE)
			{
				counts.merge(id, item.getQuantity(), Integer::sum);
			}
		}
		return counts;
	}

	private void updateGear(ItemContainer equipment)
	{
		int pieces = 0;
		boolean noWear = false;
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
				// Runecraft cape or a max cape: essence pouches do not decay
				if (name != null && (name.startsWith("Runecraft cape") || name.contains("Max cape") || name.contains("max cape")))
				{
					noWear = true;
				}
				if (name != null && name.contains("of the Eye") && !name.startsWith("Amulet"))
				{
					pieces++;
				}
			}
		}

		noPouchWear = noWear || lit == BonusCalculator.Lantern.REDWOOD;
		raimentsPieces = Math.min(4, pieces);
		maxRaimentsSeen = Math.max(maxRaimentsSeen, raimentsPieces);
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
			eliteDiaryDone && bloodCrafted && lastBloodAtArceuus);
	}
}
