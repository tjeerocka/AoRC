package com.aorc;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
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
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
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
	private TeleportOverlay teleportOverlay;

	@Inject
	private RouteOverlay routeOverlay;

	@Inject
	private RouteDebugOverlay routeDebugOverlay;

	@Inject
	private PathOverlay pathOverlay;

	@Inject
	private BankEssenceOverlay bankEssenceOverlay;


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
	// essence pouches you carry: durability (fills left) read from the game every tick
	private final java.util.Set<PouchTracker.Pouch> pouchesCarried = java.util.EnumSet.noneOf(PouchTracker.Pouch.class);
	private final java.util.Set<PouchTracker.Pouch> pouchesDecayed = java.util.EnumSet.noneOf(PouchTracker.Pouch.class);
	private final Map<PouchTracker.Pouch, Integer> pouchFills = new java.util.EnumMap<>(PouchTracker.Pouch.class);
	// pouches we warned about (warn again only after a repair)
	private final java.util.Set<PouchTracker.Pouch> pouchesWarned = java.util.EnumSet.noneOf(PouchTracker.Pouch.class);
	// teleport to highlight now: BANK, ALTAR or null; item names looked up once
	private volatile Teleport teleportNow;
	private final Map<Integer, String> itemNames = new HashMap<>();
	private boolean smallPouchCarried;
	private int lastEssence = -1;           // essence item used in your last craft
	private final Map<Integer, java.util.List<net.runelite.api.TileObject>> routeObjects = new HashMap<>();
	private final java.util.Set<Integer> stepsPassed = new java.util.HashSet<>();   // this trip
	// route debug: what happened to your last click, and how far you moved last tick
	private String debugLastClick = "-";
	private int debugMoved;
	private int nextStep = -1;          // the route step highlighted now (-1 = none)
	// fairy rings in the scene (matched by name: rings can have different object IDs)
	private final java.util.Set<net.runelite.api.GameObject> fairyRings = new java.util.HashSet<>();
	// banks in the scene: objects and NPCs with a "Bank" option (chests, booths, bankers)
	private final java.util.Set<net.runelite.api.GameObject> bankObjects = new java.util.HashSet<>();
	// Zeah: the dense runestones in the scene (found by name)
	private final java.util.Set<net.runelite.api.GameObject> runestones = new java.util.HashSet<>();
	// altars in the scene: objects with a "Craft-rune" option
	private final java.util.Set<net.runelite.api.GameObject> altars = new java.util.HashSet<>();
	private final java.util.Set<net.runelite.api.NPC> bankNpcs = new java.util.HashSet<>();
	// altar teleport used (your location jumped): no more cyan box until you are full again
	private boolean altarTeleportUsed;
	private int lastX = -1;
	private int lastY = -1;
	private int lastPlane = -1;
	private int essenceDropped = -1;   // essence type that went down this tick
	private boolean fairyRingUsed;     // used this trip: no more fairy ring outline until the next trip
	private boolean fairyRingClicked;  // clicked: counts as used once you teleport
	private int[] fairyRingAt;         // the ring you clicked {x, y, plane}
	private boolean inventoryFull;     // no free inventory slot
	private boolean pouchesAllFull = true;

	enum Teleport
	{
		BANK,
		ALTAR
	}

	// talking to the Dark Mage while a pouch needs a repair: highlight what to click
	private volatile boolean repairGuide;
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
		overlayManager.add(teleportOverlay);
		overlayManager.add(routeOverlay);
		overlayManager.add(routeDebugOverlay);
		overlayManager.add(pathOverlay);
		overlayManager.add(bankEssenceOverlay);

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
		overlayManager.remove(teleportOverlay);
		overlayManager.remove(routeOverlay);
		overlayManager.remove(routeDebugOverlay);
		overlayManager.remove(pathOverlay);
		clearOurHintArrow();
		saveRecording();
		overlayManager.remove(bankEssenceOverlay);
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
		d.craftRegion = craftRegion;
		d.lastEssence = lastEssence;
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
		craftRegion = d.craftRegion != null ? d.craftRegion : -1;
		lastEssence = d.lastEssence != null ? d.lastEssence : -1;
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
		// essence pouches you carry: durability (fills left) / durability when repaired
		int rc = client.getRealSkillLevel(Skill.RUNECRAFT);
		for (PouchTracker.Pouch p : pouchesCarried)
		{
			d.pouchNames.add(p.getName());
			int left = pouchesDecayed.contains(p) ? 0 : pouchFills.getOrDefault(p, PouchTracker.fullFills(p, rc));
			d.pouchUses.add(left + "/" + PouchTracker.fullFills(p, rc));
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
		if (state == GameState.LOADING)
		{
			routeObjects.clear();   // new map area: the objects spawn again
			fairyRings.clear();
			bankObjects.clear();
			altars.clear();
			runestones.clear();
			bankNpcs.clear();
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

				// essence that went down: remembered (when it was a craft) for the bank highlight
				for (int ess : new int[]{RuneIds.PURE_ESSENCE, RuneIds.RUNE_ESSENCE, RuneIds.DAEYALT_ESSENCE})
				{
					if (now.getOrDefault(ess, 0) < lastInventory.getOrDefault(ess, 0))
					{
						essenceDropped = ess;
					}
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
			checkPouches(event.getItemContainer());
			zeahBlockSlot = closestToChisel(event.getItemContainer().getItems());
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
		String plain = Text.removeTags(event.getMessage());
		String low = plain.toLowerCase();
		if (low.contains("fragment") && (low.contains("can't") || low.contains("cannot") || low.contains("no more")
			|| low.contains("any more") || low.contains("anymore") || low.contains("too many")))
		{
			// Zeah: the fragment stack is full, no more chiselling: mine more dense blocks first
			zeahCapped = true;
		}
		if (plain.startsWith("Your pouch has decayed through use"))
		{
			if (config.warnPouch())
			{
				warn("pouch", "Your pouch has decayed (durability 0). Repair it with Astral Contact (Dark Mage).");
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

		updatePouchWear();
		updateTeleport();
		updateZeah();
		updateNextTarget();
		updateRecording();
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
				if (essenceDropped != -1)
				{
					lastEssence = essenceDropped;   // the essence this craft used
				}
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
		essenceDropped = -1;
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

	/** Which pouches you carry, and which of them have decayed (shown as the decayed item). */
	private void checkPouches(ItemContainer inv)
	{
		pouchesCarried.clear();
		pouchesDecayed.clear();
		smallPouchCarried = false;
		inventoryFull = false;
		if (inv == null)
		{
			return;
		}
		int used = 0;
		for (Item item : inv.getItems())
		{
			if (item.getId() > 0)
			{
				used++;
			}
		}
		inventoryFull = used >= 28;
		for (Item item : inv.getItems())
		{
			if (item.getId() == SMALL_POUCH)
			{
				smallPouchCarried = true;
			}
			PouchTracker.Pouch p = PouchTracker.of(item.getId());
			if (p != null)
			{
				pouchesCarried.add(p);
				if (PouchTracker.isDegraded(item.getId()))
				{
					pouchesDecayed.add(p);
				}
			}
		}
	}

	/**
	 * Every tick: durability of the pouches you carry, from the game's own wear counter.
	 * Warn once when one reaches 3 fills left (again after a repair).
	 */
	private void updatePouchWear()
	{
		int rc = client.getRealSkillLevel(Skill.RUNECRAFT);
		for (PouchTracker.Pouch p : PouchTracker.Pouch.values())
		{
			int left = PouchTracker.fillsLeft(p, pouchWear(p), rc);
			pouchFills.put(p, left);
			boolean repair = PouchTracker.needsRepair(left, pouchesDecayed.contains(p));
			if (!repair)
			{
				pouchesWarned.remove(p);    // repaired (or never worn): warn again next time
			}
			else if (pouchesCarried.contains(p) && config.warnPouch() && pouchesWarned.add(p))
			{
				warnPouch(p);
			}
		}
	}

	private static final int SMALL_POUCH = 5509;

	/**
	 * Which teleport to highlight: the bank one at the altar once your essence and pouches are
	 * empty, the altar one away from the altar once your essence and pouches are full.
	 */
	private void updateTeleport()
	{
		Teleport next = null;
		if (lastInventory != null)
		{
			int region = regionId();
			int essence = essenceCount(lastInventory);
			int rc = client.getRealSkillLevel(Skill.RUNECRAFT);
			boolean pouchesEmpty = true;
			boolean pouchesFull = true;
			for (PouchTracker.Pouch p : pouchesCarried)
			{
				int amount = pouchAmount(p);
				pouchesEmpty &= amount == 0;
				pouchesFull &= amount >= PouchTracker.capacity(p, pouchWear(p), rc);
			}
			if (smallPouchCarried)
			{
				int amount = client.getVarbitValue(VarbitID.SMALL_ESSENCE_POUCH);
				pouchesEmpty &= amount == 0;
				pouchesFull &= amount >= 3;
			}
			pouchesAllFull = pouchesFull;
			if (essence == 0)
			{
				altarTeleportUsed = false;   // used up: next time you are full, show it again
				fairyRingUsed = false;
				fairyRingClicked = false;
				stepsPassed.clear();
			}
			// Blood - Zeah: no bank and no altar teleport, you walk the whole cycle
			if (config.highlightTeleports() && !zeahActive())
			{
				// bank: only where you crafted last (known after one craft, also saved for restarts)
				if (craftRegion != -1 && region == craftRegion && essence == 0 && pouchesEmpty)
				{
					next = Teleport.BANK;
				}
				else if (region != craftRegion && essence > 0 && pouchesFull && !altarTeleportUsed)
				{
					next = Teleport.ALTAR;
				}
			}
		}
		// you teleported (moved more than 10 tiles in one tick): the altar teleport is done
		int fromX = lastX;
		int fromY = lastY;
		int fromPlane = lastPlane;
		int moved = moved();             // every tick, so it always compares with the last tick
		debugMoved = moved;
		boolean teleported = moved > 10;
		// past a step of the route (by where you stand; no clicks needed): it and every step before it are done
		if (lastInventory != null && essenceCount(lastInventory) > 0)
		{
			AltarRoute r = route();
			int i = r.passedNow(stepsPassed, fromX, fromY, fromPlane, lastX, lastY, lastPlane, moved);
			if (i >= 0)
			{
				for (int k = 0; k <= i; k++)
				{
					stepsPassed.add(k);
				}
				int[] st = r.steps.get(i);
				debugLastClick = "step " + (i + 1) + " PASSED (" + objectName(st[0]) + " " + st[0] + ", moved " + moved + ")";
			}
		}
		if (teleported && fairyRingClicked)
		{
			// only when the jump started at the ring you clicked (not another teleport later)
			if (fairyRingAt != null && fromPlane == fairyRingAt[2]
				&& Math.abs(fromX - fairyRingAt[0]) <= 3 && Math.abs(fromY - fairyRingAt[1]) <= 3)
			{
				fairyRingUsed = true;      // the fairy ring took you away: its outline is done
				debugLastClick = "Fairy ring -> USED (moved " + moved + " tiles)";
			}
			else
			{
				debugLastClick = "Fairy ring -> not used (you teleported from somewhere else)";
			}
			fairyRingClicked = false;
		}
		if (teleportNow == Teleport.ALTAR && teleported)
		{
			altarTeleportUsed = true;
			next = null;
		}
		teleportNow = next;
	}

	// ---------- route to the altar (set by hand in AltarRoute) ----------

	/** The fairy ring you clicked (used once you teleport from it). Route steps need no clicks. */
	@Subscribe
	public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked event)
	{
		String option = event.getMenuOption() == null ? "" : Text.removeTags(event.getMenuOption()).toLowerCase();
		String target = event.getMenuTarget() == null ? "" : Text.removeTags(event.getMenuTarget()).toLowerCase();
		int id = event.getId();
		if (event.getMenuAction() == net.runelite.api.MenuAction.WALK)
		{
			clickedObject = null;   // walked somewhere else: show the way again
		}
		else if (isObjectAction(event.getMenuAction()))
		{
			clickedObject = net.runelite.api.coords.WorldPoint.fromScene(
				client, event.getParam0(), event.getParam1(), client.getPlane());
			lastClickedObject = clickedObject;
		}
		if (option.equals("use") && target.contains("chisel") && target.contains("dark essence block") && lastInventory != null)
		{
			zeahChiselTick = client.getTickCount();
			zeahChiselDark = lastInventory.getOrDefault(RuneIds.DARK_ESSENCE_BLOCK, 0);
		}
		if (id <= 0 || !isObjectAction(event.getMenuAction()))
		{
			return;
		}
		if (option.equals("craft-rune"))
		{
			return;
		}
		if (target.contains("fairy ring") && essenceCount(lastInventory == null ? new HashMap<>() : lastInventory) > 0)
		{
			fairyRingClicked = true;     // used once you really teleport from it (see updateTeleport)
			net.runelite.api.coords.WorldPoint w = net.runelite.api.coords.WorldPoint.fromScene(
				client, event.getParam0(), event.getParam1(), client.getPlane());
			fairyRingAt = new int[]{w.getX(), w.getY(), w.getPlane()};
			debugLastClick = "Fairy ring " + id + " (" + w.getX() + ", " + w.getY() + ") -> waiting for the teleport";
		}
	}

	/** {x, y, plane} of the closest object with this ID in view, or null. */
	private int[] closestSpot(int id)
	{
		net.runelite.api.Player p = client.getLocalPlayer();
		java.util.List<net.runelite.api.TileObject> list = routeObjects.get(id);
		if (p == null || p.getWorldLocation() == null || list == null)
		{
			return null;
		}
		net.runelite.api.coords.WorldPoint me = p.getWorldLocation();
		int[] best = null;
		int bestD = Integer.MAX_VALUE;
		for (net.runelite.api.TileObject o : list)
		{
			net.runelite.api.coords.WorldPoint w = o.getWorldLocation();
			if (w == null)
			{
				continue;
			}
			int d = Math.max(Math.abs(w.getX() - me.getX()), Math.abs(w.getY() - me.getY()));
			if (d < bestD)
			{
				bestD = d;
				best = new int[]{w.getX(), w.getY(), w.getPlane()};
			}
		}
		return best;
	}

	private String objectName(int id)
	{
		net.runelite.api.ObjectComposition def = client.getObjectDefinition(id);
		if (def != null && def.getImpostorIds() != null && def.getImpostor() != null)
		{
			def = def.getImpostor();
		}
		return def == null || def.getName() == null ? "?" : def.getName();
	}

	/** Route debug: the text lines of the debug box. */
	java.util.List<String> routeDebugLines()
	{
		java.util.List<String> out = new java.util.ArrayList<>();
		AltarRoute r = route();
		if (zeahActive() && lastInventory != null)
		{
			int step = zeahObject();
			out.add("Zeah: next " + (step == 0 ? "chisel (slot " + (zeahBlockSlot + 1) + ")" : step == -1 ? "nothing"
				: objectName(step) + " " + step));
			out.add("dense " + lastInventory.getOrDefault(RuneIds.DENSE_ESSENCE_BLOCK, 0)
				+ "  dark " + lastInventory.getOrDefault(RuneIds.DARK_ESSENCE_BLOCK, 0)
				+ "  frags " + lastInventory.getOrDefault(RuneIds.DARK_ESSENCE_FRAGMENTS, 0)
				+ "  full " + inventoryFull + "  go " + zeahGo);
			out.add("fragments full " + zeahCapped + "  bound here " + zeahBound);
			out.add("near: runestone " + near(ZEAH_RUNESTONE, ZEAH_AT_RUNESTONE) + "  dark altar " + near(ZEAH_DARK_ALTAR, 15)
				+ "  blood altar " + near(ZEAH_BLOOD_ALTAR, 12));
			for (net.runelite.api.GameObject rs : runestones)
			{
				WorldPoint w = rs.getWorldLocation();
				boolean chip = objectsInView(ZEAH_RUNESTONE).contains(rs);
				out.add("runestone " + rs.getId() + " (" + (w == null ? "?" : w.getX() + ", " + w.getY()) + ") "
					+ (chip ? "can chip" : "depleted") + ", " + distanceTo(rs) + " tiles");
			}
			out.add("from blood altar " + zeahFromBlood + "  used " + zeahUsed + "  runestones " + runestones.size()
				+ "  agility " + client.getBoostedSkillLevel(Skill.AGILITY));
			return out;
		}
		out.add("Route: " + r.name);
		out.add("Region now: " + regionId() + "   altar region: " + craftRegion);
		out.add("Essence: " + (lastInventory == null ? 0 : essenceCount(lastInventory)) + "   moved: " + debugMoved + " tiles");
		out.add("Altar: " + (r.altar < 0 ? "none" : objectName(r.altar) + " " + r.altar));
		out.add("Fairy ring: " + (r.fairyRing ? (fairyRingUsed ? "yes (used)" : "yes") : "no"));
		out.add("Steps (" + r.steps.size() + "):");
		for (int i = 0; i < r.steps.size(); i++)
		{
			int[] st = r.steps.get(i);
			out.add("  " + (i + 1) + ". " + objectName(st[0]) + " " + st[0] + " (" + st[1] + ", " + st[2] + ", " + st[3] + ") "
				+ (stepsPassed.contains(i) ? "passed" : i == nextStep ? "NEXT" : ""));
		}
		out.add("Last: " + debugLastClick);
		return out;
	}

	/** Route debug: the label above every object of the route that is in view. */
	java.util.Map<net.runelite.api.TileObject, String> routeDebugLabels()
	{
		java.util.Map<net.runelite.api.TileObject, String> out = new java.util.LinkedHashMap<>();
		AltarRoute r = route();
		for (int i = 0; i < r.steps.size(); i++)
		{
			int[] st = r.steps.get(i);
			for (net.runelite.api.TileObject o : routeObjects.getOrDefault(st[0], java.util.Collections.emptyList()))
			{
				net.runelite.api.coords.WorldPoint w = o.getWorldLocation();
				if (w != null && AltarRoute.same(st, new int[]{st[0], w.getX(), w.getY(), w.getPlane()}))
				{
					out.put(o, "Step " + (i + 1) + "/" + r.steps.size() + " - " + st[0]
						+ (stepsPassed.contains(i) ? " - passed" : i == nextStep ? " - NEXT" : " - waiting"));
				}
			}
		}
		for (net.runelite.api.TileObject o : routeObjects.getOrDefault(r.altar, java.util.Collections.emptyList()))
		{
			out.put(o, "Altar - " + r.altar);
		}
		for (net.runelite.api.TileObject o : fairyRings)
		{
			out.putIfAbsent(o, "Fairy ring - " + o.getId());
		}
		out.keySet().removeIf(o -> r.hidden.contains(o.getId()));
		return out;
	}

	/** The route to the altar you are heading to (the one you crafted at last). */
	private AltarRoute route()
	{
		// your Agility level now (with boosts, like the game checks shortcuts) picks the route;
		// steps without a known spot use the closest object with that ID you can see
		return AltarRoute.of(craftRegion, client.getBoostedSkillLevel(Skill.AGILITY)).withSpots(this::closestSpot);
	}

	private static boolean isObjectAction(net.runelite.api.MenuAction a)
	{
		return a == net.runelite.api.MenuAction.GAME_OBJECT_FIRST_OPTION
			|| a == net.runelite.api.MenuAction.GAME_OBJECT_SECOND_OPTION
			|| a == net.runelite.api.MenuAction.GAME_OBJECT_THIRD_OPTION
			|| a == net.runelite.api.MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| a == net.runelite.api.MenuAction.GAME_OBJECT_FIFTH_OPTION;
	}

	@Subscribe
	public void onGameObjectSpawned(net.runelite.api.events.GameObjectSpawned event)
	{
		net.runelite.api.GameObject o = event.getGameObject();
		addRouteObject(o);
		if (isFairyRing(o.getId()))
		{
			fairyRings.add(o);
		}
		if (hasBankOption(client.getObjectDefinition(o.getId())))
		{
			bankObjects.add(o);
		}
		net.runelite.api.ObjectComposition def = client.getObjectDefinition(o.getId());
		if (def != null && def.getImpostorIds() != null && def.getImpostor() != null)
		{
			def = def.getImpostor();
		}
		if (def != null && hasOption(def.getActions(), "craft-rune"))
		{
			altars.add(o);
		}
		net.runelite.api.ObjectComposition base = client.getObjectDefinition(o.getId());
		if (o.getId() == ZEAH_RUNESTONE || (def != null && "Dense runestone".equalsIgnoreCase(def.getName()))
			|| (base != null && "Dense runestone".equalsIgnoreCase(base.getName())))
		{
			runestones.add(o);
		}
	}

	// caves, doors and openings in walls are wall / decoration objects, not game objects
	@Subscribe
	public void onWallObjectSpawned(net.runelite.api.events.WallObjectSpawned event)
	{
		addRouteObject(event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(net.runelite.api.events.WallObjectDespawned event)
	{
		removeRouteObject(event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(net.runelite.api.events.DecorativeObjectSpawned event)
	{
		addRouteObject(event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(net.runelite.api.events.DecorativeObjectDespawned event)
	{
		removeRouteObject(event.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(net.runelite.api.events.GroundObjectSpawned event)
	{
		addRouteObject(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(net.runelite.api.events.GroundObjectDespawned event)
	{
		removeRouteObject(event.getGroundObject());
	}

	private void addRouteObject(net.runelite.api.TileObject o)
	{
		java.util.List<net.runelite.api.TileObject> same = routeObjects.computeIfAbsent(o.getId(), k -> new java.util.ArrayList<>());
		if (!same.contains(o))
		{
			same.add(o);
		}
	}

	private void removeRouteObject(net.runelite.api.TileObject o)
	{
		java.util.List<net.runelite.api.TileObject> list = routeObjects.get(o.getId());
		if (list != null)
		{
			list.remove(o);
		}
	}

	/** The outline of any kind of object. */
	static java.awt.Shape hull(net.runelite.api.TileObject o)
	{
		if (o instanceof net.runelite.api.GameObject)
		{
			return ((net.runelite.api.GameObject) o).getConvexHull();
		}
		if (o instanceof net.runelite.api.WallObject)
		{
			return ((net.runelite.api.WallObject) o).getConvexHull();
		}
		if (o instanceof net.runelite.api.DecorativeObject)
		{
			return ((net.runelite.api.DecorativeObject) o).getConvexHull();
		}
		if (o instanceof net.runelite.api.GroundObject)
		{
			return ((net.runelite.api.GroundObject) o).getConvexHull();
		}
		return o.getClickbox();
	}

	/** Route debug (setting in the Developer section). */
	boolean routeDebug()
	{
		return config.routeDebug();
	}

	/** Pouch left-click: "Empty" near an altar, "Fill" in the bank (moved to the top, like Menu Entry Swapper). */
	@Subscribe
	public void onPostMenuSort(net.runelite.api.events.PostMenuSort event)
	{
		// near an altar: "Empty" is the left-click; in the bank: "Fill", or "Examine" when it is full
		boolean altar = pouchOnlyEmpty();
		boolean bank = !altar && bankOpen();
		if (client.isMenuOpen() || (!altar && !bank))
		{
			return;
		}
		net.runelite.api.MenuEntry[] all = client.getMenuEntries();
		for (int i = 0; i < all.length - 1; i++)
		{
			net.runelite.api.MenuEntry e = all[i];
			if (!isPouch(e.getItemId()))
			{
				continue;
			}
			String wanted = altar ? "empty" : pouchFull(e.getItemId()) ? "examine" : "fill";
			if (wanted.equalsIgnoreCase(Text.removeTags(e.getOption())))
			{
				// it can be a low-priority option (listed under "Use"); as the left-click the game
				// would open the menu instead, so make it a normal option (like Menu Entry Swapper)
				if (e.getType() == net.runelite.api.MenuAction.CC_OP_LOW_PRIORITY)
				{
					e.setType(net.runelite.api.MenuAction.CC_OP);
				}
				// the last entry is the left-click
				all[i] = all[all.length - 1];
				all[all.length - 1] = e;
				client.setMenuEntries(all);
				return;
			}
		}
	}

	/** Within 10 tiles of an altar (not at ZMI or Guardians of the Rift, where you fill there). */
	private boolean pouchOnlyEmpty()
	{
		return nearAltar() && !zmiMode && lastEssence != RuneIds.GUARDIAN_ESSENCE;
	}

	// ---------- the way to the next step: hint arrow, line, edge arrow; path recorder ----------

	private volatile WorldPoint nextTarget;     // the next thing to click (null = nothing, or you use it now)
	private WorldPoint clickedObject;            // the object you clicked last (its tile), for the line
	private WorldPoint lastClickedObject;        // same, kept when the next step changes (the rock you chip)
	private WorldPoint ourHintArrow;             // the hint arrow we set (we only clear our own)
	private final java.util.List<int[]> recording = new java.util.ArrayList<>();

	/** The next thing to click on your route, for the line and the arrows. */
	WorldPoint nextTargetPoint()
	{
		return nextTarget;
	}

	/** Each tick: the next target (the closest highlighted route object) and the hint arrow on it. */
	private void updateNextTarget()
	{
		net.runelite.api.TileObject o = closest(routeTargets());
		WorldPoint t = o == null ? null : o.getWorldLocation();
		if (o == null && config.highlightRoute())
		{
			// Zeah: the next object is too far away to be loaded (the blood altar from the dark
			// altar): point the line and arrows at where it is
			int z = zeahObject();
			int[] spot = z > 0 ? ZEAH_SPOTS.get(z) : null;
			if (spot != null)
			{
				nextTarget = new WorldPoint(spot[0], spot[1], spot[2]);
				clickedObject = null;
				if (config.routeHintArrow())
				{
					setOurHintArrow(nextTarget);
				}
				else
				{
					clearOurHintArrow();
				}
				return;
			}
		}
		// you clicked it and you stand next to it (you use it now): no line and no arrows, only
		// its outline; while you still walk there, the line stays
		if (t != null && clickedObject != null && t.getPlane() == clickedObject.getPlane()
			&& Math.abs(t.getX() - clickedObject.getX()) <= 3 && Math.abs(t.getY() - clickedObject.getY()) <= 3)
		{
			if (distanceTo(o) <= 2)
			{
				t = null;
			}
		}
		else if (t != null)
		{
			clickedObject = null;   // a new next step: show the way again
		}
		nextTarget = t;
		if (config.routeHintArrow() && nextTarget != null)
		{
			setOurHintArrow(nextTarget);
		}
		else
		{
			clearOurHintArrow();
		}
	}

	/** Our hint arrow on this spot (never over a quest's arrow). */
	private void setOurHintArrow(WorldPoint p)
	{
		if (!p.equals(ourHintArrow) && (!client.hasHintArrow() || ourHintArrow != null))
		{
			client.setHintArrow(p);
			ourHintArrow = p;
		}
	}

	/** Remove the hint arrow only if it is ours (not a quest's). */
	private void clearOurHintArrow()
	{
		if (ourHintArrow != null)
		{
			if (client.hasHintArrow() && ourHintArrow.equals(client.getHintArrowPoint()))
			{
				client.clearHintArrow();
			}
			ourHintArrow = null;
		}
	}

	/** The tiles walked while recording (for the yellow trail). */
	java.util.List<int[]> recordedWalk()
	{
		return recording;
	}

	/** Record path (Developer): every new tile you stand on; saved to a file when turned off. */
	private void updateRecording()
	{
		if (!config.recordPath())
		{
			saveRecording();
			return;
		}
		net.runelite.api.Player p = client.getLocalPlayer();
		if (p == null || p.getWorldLocation() == null)
		{
			return;
		}
		WorldPoint w = p.getWorldLocation();
		int[] last = recording.isEmpty() ? null : recording.get(recording.size() - 1);
		if (last == null || last[0] != w.getX() || last[1] != w.getY() || last[2] != w.getPlane())
		{
			recording.add(new int[]{w.getX(), w.getY(), w.getPlane()});
		}
	}

	/** Write the recorded walk (all tiles, and the corners per piece) to .runelite/aorc/paths. */
	private void saveRecording()
	{
		if (recording.isEmpty())
		{
			return;
		}
		java.io.File dir = new java.io.File(new java.io.File(RuneLite.RUNELITE_DIR, "aorc"), "paths");
		String name = config.recordPiece().file + "-"
			+ new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date()) + ".txt";
		StringBuilder sb = new StringBuilder("# AoRC recorded path: " + config.recordPiece() + "\n# x, y, plane\n"
			+ "# corners per piece (a cave / climb starts a new piece)\n");
		for (java.util.List<int[]> piece : AorcPaths.pieces(recording))
		{
			sb.append("piece\n");
			for (int[] pt : piece)
			{
				sb.append(pt[0]).append(", ").append(pt[1]).append(", ").append(pt[2]).append('\n');
			}
		}
		sb.append("# every tile\n");
		for (int[] pt : recording)
		{
			sb.append(pt[0]).append(", ").append(pt[1]).append(", ").append(pt[2]).append('\n');
		}
		recording.clear();
		try
		{
			java.nio.file.Files.createDirectories(dir.toPath());
			java.nio.file.Files.write(new java.io.File(dir, name).toPath(),
				sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"<col=00dcff>AoRC:</col> path saved: .runelite/aorc/paths/" + name, null);
			}
		}
		catch (java.io.IOException e)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff3030>AoRC:</col> could not save the path", null);
		}
	}

	/** Remove the menu entry that was just added (it is the last one). */
	private void removeMenuEntry(net.runelite.api.MenuEntry e)
	{
		net.runelite.api.MenuEntry[] all = client.getMenuEntries();
		if (all.length > 0 && all[all.length - 1] == e)
		{
			client.setMenuEntries(java.util.Arrays.copyOf(all, all.length - 1));
		}
	}

	/** Within this many tiles of an altar, the pouches have no Fill option. */
	private static final int ALTAR_RANGE = 10;

	/** You stand within 10 tiles of an altar (any object with "Craft-rune"). */
	private boolean nearAltar()
	{
		net.runelite.api.Player p = client.getLocalPlayer();
		if (p == null || p.getWorldLocation() == null)
		{
			return false;
		}
		net.runelite.api.coords.WorldPoint me = p.getWorldLocation();
		for (net.runelite.api.GameObject a : altars)
		{
			net.runelite.api.coords.WorldPoint w = a.getWorldLocation();
			if (w != null && w.getPlane() == me.getPlane()
				&& Math.abs(w.getX() - me.getX()) <= ALTAR_RANGE && Math.abs(w.getY() - me.getY()) <= ALTAR_RANGE)
			{
				return true;
			}
		}
		return false;
	}

	/** Any essence pouch (small to colossal, also decayed ones). */
	static boolean isPouch(int itemId)
	{
		return itemId == SMALL_POUCH || PouchTracker.of(itemId) != null;
	}

	/** Route debug: the object ID in the right-click menu. Also hides the wrong-way objects' options. ("Examine Cave (ID 43762)"). */
	@Subscribe
	public void onMenuEntryAdded(net.runelite.api.events.MenuEntryAdded event)
	{
		net.runelite.api.MenuEntry e = event.getMenuEntry();
		// while a step is the highlighted one: no "Enter" on the object next to it that takes you
		// back the wrong way (step 4 from the other side); "Walk here" and "Examine" stay
		java.util.Set<Integer> at = route().hiddenAt.get(e.getIdentifier());
		if (at != null && at.contains(nextStep) && isObjectAction(e.getType()))
		{
			removeMenuEntry(e);
			return;
		}
		// within 10 tiles of an altar: no "Fill" and no "Check" on the essence pouches, so "Empty" is
		// the left-click (the colossal pouch has Fill, Check, Empty: without Fill, Check was on top)
		// (not at ZMI or Guardians of the Rift: there you fill your pouches next to the altar)
		String opt = Text.removeTags(e.getOption());
		if (("fill".equalsIgnoreCase(opt) || "check".equalsIgnoreCase(opt)) && isPouch(e.getItemId())
			&& pouchOnlyEmpty())
		{
			removeMenuEntry(e);
			return;
		}
		// in the bank: a full pouch has no "Empty" and no "Deposit", so a misclick never empties
		// or banks it (the left-click is "Examine" then)
		if (("empty".equalsIgnoreCase(opt) || opt.toLowerCase().startsWith("deposit"))
			&& isPouch(e.getItemId()) && bankOpen() && pouchFull(e.getItemId()))
		{
			removeMenuEntry(e);
			return;
		}
		if (routeDebug() && e.getType() == net.runelite.api.MenuAction.EXAMINE_OBJECT)
		{
			net.runelite.api.coords.WorldPoint w = net.runelite.api.coords.WorldPoint.fromScene(
				client, e.getParam0(), e.getParam1(), client.getPlane());
			e.setTarget(e.getTarget() + " <col=ffff00>(ID " + e.getIdentifier() + ", "
				+ w.getX() + ", " + w.getY() + ", " + w.getPlane() + ")");
		}
	}

	private static boolean hasBankOption(net.runelite.api.ObjectComposition def)
	{
		if (def == null)
		{
			return false;
		}
		if (def.getImpostorIds() != null && def.getImpostor() != null)
		{
			def = def.getImpostor();
		}
		return hasOption(def.getActions(), "bank");
	}

	private static boolean hasOption(String[] actions, String option)
	{
		if (actions != null)
		{
			for (String a : actions)
			{
				if (a != null && a.equalsIgnoreCase(option))
				{
					return true;
				}
			}
		}
		return false;
	}

	@Subscribe
	public void onNpcSpawned(net.runelite.api.events.NpcSpawned event)
	{
		net.runelite.api.NPC npc = event.getNpc();
		if (npc.getComposition() != null && hasOption(npc.getComposition().getActions(), "bank"))
		{
			bankNpcs.add(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(net.runelite.api.events.NpcDespawned event)
	{
		bankNpcs.remove(event.getNpc());
	}

	/**
	 * Away from the altar until your inventory and pouches are full (bank not open right now):
	 * the bank chests, booths and bankers around you, to click.
	 */
	java.util.List<java.awt.Shape> bankTargets()
	{
		java.util.List<java.awt.Shape> out = new java.util.ArrayList<>();
		// until your inventory and your pouches are both full of essence
		if (!config.highlightRoute() || lastInventory == null || craftRegion == -1 || regionId() == craftRegion
			|| (inventoryFull && pouchesAllFull && essenceCount(lastInventory) > 0) || bankOpen())
		{
			return out;
		}
		for (net.runelite.api.GameObject o : bankObjects)
		{
			if (o.getPlane() == client.getPlane() && o.getConvexHull() != null)
			{
				out.add(o.getConvexHull());
			}
		}
		for (net.runelite.api.NPC n : bankNpcs)
		{
			if (n.getConvexHull() != null)
			{
				out.add(n.getConvexHull());
			}
		}
		return out;
	}

	/** The bank (or a deposit box) is open. */
	private boolean bankOpen()
	{
		net.runelite.api.widgets.Widget bank = client.getWidget(12, 0);
		net.runelite.api.widgets.Widget box = client.getWidget(192, 0);
		return (bank != null && !bank.isHidden()) || (box != null && !box.isHidden());
	}

	private boolean isFairyRing(int objectId)
	{
		net.runelite.api.ObjectComposition def = client.getObjectDefinition(objectId);
		if (def == null)
		{
			return false;
		}
		String name = def.getName();
		if ((name == null || "null".equals(name)) && def.getImpostorIds() != null)
		{
			def = def.getImpostor();
			name = def == null ? null : def.getName();
		}
		return name != null && name.toLowerCase().startsWith("fairy ring");
	}

	@Subscribe
	public void onGameObjectDespawned(net.runelite.api.events.GameObjectDespawned event)
	{
		removeRouteObject(event.getGameObject());
		fairyRings.remove(event.getGameObject());
		bankObjects.remove(event.getGameObject());
		altars.remove(event.getGameObject());
		runestones.remove(event.getGameObject());
	}

	/**
	 * What to highlight in the world now: on the way (essence on you, away from the altar) the
	 * fairy ring and every step of your route you have not passed yet; at the altar, the altar.
	 */
	java.util.List<net.runelite.api.TileObject> routeTargets()
	{
		java.util.List<net.runelite.api.TileObject> out = new java.util.ArrayList<>();
		// Blood - Zeah: its own cycle instead of a route
		int zeah = config.highlightRoute() ? zeahObject() : -2;
		if (zeah != -2)
		{
			nextStep = -1;
			if (zeah > 0)
			{
				// only the closest one (two runestones: the one next to you)
				java.util.List<net.runelite.api.TileObject> all = objectsInView(zeah);
				net.runelite.api.TileObject o = clickedOne(all);
				if (o == null)
				{
					o = closest(all);
				}
				if (o != null)
				{
					out.add(o);
				}
			}
			return out;
		}
		if (!config.highlightRoute() || lastInventory == null || essenceCount(lastInventory) == 0)
		{
			nextStep = -1;
			return out;
		}
		boolean atAltar = craftRegion != -1 && regionId() == craftRegion;
		AltarRoute route = route();
		int[] ids = {route.altar};
		// fairy ring on your route (any ring you see, they can have different IDs), until you used it
		if (!atAltar && route.fairyRing && !fairyRingUsed)
		{
			for (net.runelite.api.TileObject o : fairyRings)
			{
				if (o.getPlane() == client.getPlane())
				{
					out.add(o);
				}
			}
		}
		for (int id : ids)
		{
			for (net.runelite.api.TileObject o : routeObjects.getOrDefault(id, java.util.Collections.emptyList()))
			{
				if (o.getPlane() == client.getPlane())
				{
					out.add(o);
				}
			}
		}
		int next = -1;
		// only the NEXT step you have not passed yet this trip (the first one you can see), and
		// only that exact object: the cave on the other side, with the same name, is not your way
		for (int i = 0; i < route.steps.size(); i++)
		{
			if (stepsPassed.contains(i))
			{
				continue;
			}
			int[] r = route.steps.get(i);
			if (r[0] == route.altar)
			{
				continue;   // the altar has its own outline
			}
			boolean found = false;
			for (net.runelite.api.TileObject o : routeObjects.getOrDefault(r[0], java.util.Collections.emptyList()))
			{
				net.runelite.api.coords.WorldPoint w = o.getWorldLocation();
				if (w != null && AltarRoute.same(r, new int[]{r[0], w.getX(), w.getY(), w.getPlane()}))
				{
					out.add(o);
					found = true;
				}
			}
			if (found)
			{
				next = i;
				break;
			}
		}
		nextStep = next;
		// one outline per spot (two outlines on top of each other look twice as strong)
		java.util.Map<String, net.runelite.api.TileObject> one = new java.util.LinkedHashMap<>();
		for (net.runelite.api.TileObject o : out)
		{
			if (route.hidden.contains(o.getId()))
			{
				continue;   // never highlighted on this route
			}
			net.runelite.api.coords.WorldPoint w = o.getWorldLocation();
			one.putIfAbsent(o.getId() + ":" + w, o);
		}
		return new java.util.ArrayList<>(one.values());
	}

	/**
	 * At the altar with no essence left in your inventory: the pouch that still has essence,
	 * to empty next (this beats the repair box).
	 */
	boolean pouchToEmpty(int itemId)
	{
		if (!config.highlightRoute() || lastInventory == null || essenceCount(lastInventory) > 0
			|| craftRegion == -1 || regionId() != craftRegion)
		{
			return false;
		}
		if (itemId == SMALL_POUCH)
		{
			return client.getVarbitValue(VarbitID.SMALL_ESSENCE_POUCH) > 0;
		}
		PouchTracker.Pouch p = PouchTracker.of(itemId);
		return p != null && pouchAmount(p) > 0;
	}

	/** Bank: the essence to take (the one you used last), while you have none on you. */
	boolean isBankEssence(int itemId)
	{
		// until your inventory and your pouches are both full
		return config.highlightRoute() && itemId == lastEssence && !(inventoryFull && pouchesAllFull);
	}

	/** Your location jumped since the last tick (a teleport). */
	/** How far you moved since the last tick (tiles); 99 for another floor. */
	private int moved()
	{
		net.runelite.api.Player p = client.getLocalPlayer();
		if (p == null || p.getWorldLocation() == null)
		{
			return 0;
		}
		net.runelite.api.coords.WorldPoint w = p.getWorldLocation();
		int d = lastX == -1 ? 0 : w.getPlane() != lastPlane ? 99
			: Math.max(Math.abs(w.getX() - lastX), Math.abs(w.getY() - lastY));
		lastX = w.getX();
		lastY = w.getY();
		lastPlane = w.getPlane();
		return d;
	}

	/** Essence in a pouch now (the game keeps it in a varbit). */
	/** This pouch is full of essence. */
	private boolean pouchFull(int itemId)
	{
		if (itemId == SMALL_POUCH)
		{
			return client.getVarbitValue(VarbitID.SMALL_ESSENCE_POUCH) >= 3;
		}
		PouchTracker.Pouch p = PouchTracker.of(itemId);
		if (p == null)
		{
			return false;
		}
		int cap = PouchTracker.capacity(p, pouchWear(p), client.getRealSkillLevel(Skill.RUNECRAFT));
		return cap > 0 && pouchAmount(p) >= cap;
	}

	private int pouchAmount(PouchTracker.Pouch p)
	{
		switch (p)
		{
			case MEDIUM:
				return client.getVarbitValue(VarbitID.MEDIUM_ESSENCE_POUCH);
			case LARGE:
				return client.getVarbitValue(VarbitID.LARGE_ESSENCE_POUCH);
			case GIANT:
				return client.getVarbitValue(VarbitID.GIANT_ESSENCE_POUCH);
			default:
				return client.getVarbitValue(VarbitID.COLOSSAL_ESSENCE_POUCH);
		}
	}

	/** Colour of the box around this item (inventory / worn), or null: the teleport to use now. */
	// ---------- Blood - Zeah: the cycle (mine, venerate, chisel, bind, climb back) ----------

	static final int ZEAH_RUNESTONE = 8975;     // Dense runestone you can chip
	static final int ZEAH_SHORTCUT = 34741;     // on the way to the dark altar (69 Agility)
	static final int ZEAH_SHORTCUT_AGILITY = 69;
	static final int ZEAH_DARK_ALTAR = 27979;
	static final int ZEAH_BLOOD_ALTAR = 27978;
	static final int ZEAH_CLIMB_ROCKS = 27984;  // back to the runestones (73 Agility)
	static final int ZEAH_CLIMB_AGILITY = 73;
	static final int ZEAH_LOW_TO_STONE = 27987;   // 52 Agility: from the dark altar to the runestone
	static final int ZEAH_LOW_TO_ALTAR = 27988;   // 52 Agility: from the runestone to the dark altar
	static final int ZEAH_LOW_AGILITY = 52;
	// at the runestones: this close (the dark altar side of the 69 climb is only ~14 tiles away)
	static final int ZEAH_AT_RUNESTONE = 8;
	private boolean zeahFromBlood;                // on the way back from the blood altar
	private final Map<Integer, int[]> zeahApproach = new HashMap<>();             // side you came from
	private final java.util.Set<Integer> zeahUsed = new java.util.HashSet<>();      // went over it
	static final int CHISEL = 1755;
	/** Where the Zeah objects are (for the line and arrows when they are too far to be loaded). */
	static final Map<Integer, int[]> ZEAH_SPOTS = new HashMap<>();

	static
	{
		ZEAH_SPOTS.put(ZEAH_RUNESTONE, new int[]{1762, 3850, 0});    // between the two runestones
		ZEAH_SPOTS.put(ZEAH_SHORTCUT, new int[]{1761, 3873, 0});
		ZEAH_SPOTS.put(ZEAH_DARK_ALTAR, new int[]{1715, 3882, 0});
		ZEAH_SPOTS.put(ZEAH_BLOOD_ALTAR, new int[]{1715, 3828, 0});
		ZEAH_SPOTS.put(ZEAH_CLIMB_ROCKS, new int[]{1743, 3854, 0});
		ZEAH_SPOTS.put(ZEAH_LOW_TO_ALTAR, new int[]{1770, 3849, 0});
	}
	private boolean zeahGo;                      // these dark blocks go to the blood altar (not chiselled first)
	private int zeahLastDark = -1;
	private int zeahLastFrags = -1;
	private boolean zeahCapped;                  // the fragment stack is full (no more chiselling)
	private boolean zeahBound;                   // bound at the blood altar this visit
	private int zeahChiselTick = -1;             // you used the chisel on a dark block (tick)
	private int zeahChiselDark;                  // dark blocks at that moment

	/** You are at Zeah: one of its runestones, altars or shortcuts is in view. */
	private boolean zeahActive()
	{
		for (int id : new int[]{ZEAH_RUNESTONE, ZEAH_DARK_ALTAR, ZEAH_BLOOD_ALTAR,
			ZEAH_SHORTCUT, ZEAH_LOW_TO_ALTAR, ZEAH_CLIMB_ROCKS, ZEAH_LOW_TO_STONE})
		{
			if (!objectsInView(id).isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Each tick: decide once, when you get your dark blocks, if they go to the blood altar;
	 * and see which shortcut you used (stood next to it, now away from it).
	 */
	private void updateZeah()
	{
		if (lastInventory == null)
		{
			return;
		}
		int dense = lastInventory.getOrDefault(RuneIds.DENSE_ESSENCE_BLOCK, 0);
		int dark = lastInventory.getOrDefault(RuneIds.DARK_ESSENCE_BLOCK, 0);
		int frags = lastInventory.getOrDefault(RuneIds.DARK_ESSENCE_FRAGMENTS, 0);
		// (the fragments are one item: the game does not tell how many, so we go by what happens)
		if (dark > Math.max(0, zeahLastDark))
		{
			// new dark blocks (venerated): with fragments on you they go to the blood altar
			zeahGo = frags > 0;
		}
		boolean nearBlood = near(ZEAH_BLOOD_ALTAR, 12);
		if (zeahLastFrags > 0 && frags == 0)
		{
			zeahCapped = false;   // fragments used at the blood altar: room again
			if (nearBlood)
			{
				zeahBound = true; // bound: now chisel all blocks, then bind again
			}
		}
		if (!nearBlood || dense > 0)
		{
			zeahBound = false;
		}
		zeahLastFrags = frags;
		// chisel used but no block was chiselled: the fragment stack is full
		if (zeahChiselTick >= 0 && client.getTickCount() - zeahChiselTick >= 3)
		{
			if (dark >= zeahChiselDark && dark > 0 && frags > 0)
			{
				zeahCapped = true;
			}
			zeahChiselTick = -1;
		}
		if (dark == 0 && zeahLastDark > 0)
		{
			// all chiselled / bound: the shortcuts you use from now on are on the way back
			zeahUsed.clear();
			zeahApproach.clear();
		}
		if (dark == 0)
		{
			zeahGo = false;
		}
		zeahLastDark = dark;
		if (nearBlood)
		{
			zeahFromBlood = true;
		}
		if (dense > 0)
		{
			// mining: you made it back; the shortcuts are fresh for the next trip
			zeahFromBlood = false;
			if (!inventoryFull)
			{
				zeahUsed.clear();
				zeahApproach.clear();
			}
		}
		net.runelite.api.Player me = client.getLocalPlayer();
		WorldPoint at = me == null ? null : me.getWorldLocation();
		for (int id : new int[]{ZEAH_SHORTCUT, ZEAH_LOW_TO_ALTAR, ZEAH_CLIMB_ROCKS, ZEAH_LOW_TO_STONE})
		{
			net.runelite.api.TileObject o = closest(objectsInView(id));
			WorldPoint w = o == null ? null : o.getWorldLocation();
			if (at == null || w == null)
			{
				continue;
			}
			int dx = at.getX() - w.getX();
			int dy = at.getY() - w.getY();
			int d = Math.max(Math.abs(dx), Math.abs(dy));
			int[] side = zeahApproach.get(id);
			if (side == null)
			{
				if (d <= 4 && d > 0)
				{
					zeahApproach.put(id, new int[]{dx, dy});   // the side you came from
				}
			}
			else if (d >= 2)
			{
				// on the other side now (a short climb), or far away from it: used
				double cos = (side[0] * dx + side[1] * dy) / (Math.hypot(side[0], side[1]) * Math.hypot(dx, dy));
				if (cos < -0.3 || d > 7)
				{
					zeahUsed.add(id);
				}
			}
		}
	}

	/**
	 * The Zeah object to click now, 0 = the chisel and dark blocks (no object), -1 = nothing,
	 * -2 = not at Zeah. See zeahStep for the rules.
	 */
	private int zeahObject()
	{
		if (lastInventory == null || !zeahActive())
		{
			return -2;
		}
		int step = zeahStep(lastInventory.getOrDefault(RuneIds.DENSE_ESSENCE_BLOCK, 0),
			lastInventory.getOrDefault(RuneIds.DARK_ESSENCE_BLOCK, 0),
			lastInventory.getOrDefault(RuneIds.DARK_ESSENCE_FRAGMENTS, 0),
			inventoryFull, zeahGo || zeahCapped, zeahCapped, zeahBound, near(ZEAH_DARK_ALTAR, 15), near(ZEAH_BLOOD_ALTAR, 12), near(ZEAH_RUNESTONE, ZEAH_AT_RUNESTONE),
			zeahFromBlood, zeahUsed.contains(ZEAH_SHORTCUT) || zeahUsed.contains(ZEAH_LOW_TO_ALTAR),
			zeahUsed.contains(ZEAH_CLIMB_ROCKS) || zeahUsed.contains(ZEAH_LOW_TO_STONE) || zeahUsed.contains(ZEAH_SHORTCUT),
			client.getBoostedSkillLevel(Skill.AGILITY));
		// a shortcut you cannot see yet: show where you are going instead
		if ((step == ZEAH_SHORTCUT || step == ZEAH_LOW_TO_ALTAR) && objectsInView(step).isEmpty())
		{
			return ZEAH_DARK_ALTAR;
		}
		boolean toStone = lastInventory.getOrDefault(RuneIds.DENSE_ESSENCE_BLOCK, 0) == 0 && !inventoryFull;
		if ((step == ZEAH_CLIMB_ROCKS || step == ZEAH_LOW_TO_STONE || (step == ZEAH_SHORTCUT && toStone))
			&& objectsInView(step).isEmpty())
		{
			return ZEAH_RUNESTONE;
		}
		return step;
	}

	/** The Zeah cycle: what to click next (object ID, 0 = chisel the dark blocks, -1 = nothing). */
	static int zeahStep(int dense, int dark, int frags, boolean full, boolean go, boolean capped, boolean bound,
		boolean nearDarkAltar, boolean nearBloodAltar, boolean nearRunestone, boolean fromBlood, boolean usedToAltar,
		boolean usedToStone, int agility)
	{
		if (dark > 0 && nearBloodAltar)
		{
			// bind first; after that chisel ALL blocks, and only then bind again
			return frags > 0 && !bound ? ZEAH_BLOOD_ALTAR : 0;
		}
		if (dark > 0 && !(capped && dense > 0 && full))
		{
			if (capped && !full)
			{
				return ZEAH_RUNESTONE;                     // fragments full: fill up with dense blocks first
			}
			if (!capped || dense == 0)
			{
				return go ? ZEAH_BLOOD_ALTAR : 0;          // to the altar, or chisel them all first
			}
		}
		if (nearBloodAltar && frags > 0)
		{
			return ZEAH_BLOOD_ALTAR;   // bind the last ones
		}
		if (dense > 0 && full)
		{
			// to the dark altar: the shortcut for your Agility (69 or 52), or walk
			if (nearDarkAltar || usedToAltar)
			{
				return ZEAH_DARK_ALTAR;
			}
			return agility >= ZEAH_SHORTCUT_AGILITY ? ZEAH_SHORTCUT
				: agility >= ZEAH_LOW_AGILITY ? ZEAH_LOW_TO_ALTAR : ZEAH_DARK_ALTAR;
		}
		if (full)
		{
			return -1;
		}
		if (dense == 0 && !usedToStone && !nearRunestone)
		{
			if (nearBloodAltar || fromBlood)
			{
				// back from the blood altar: the 73 climb, or walk
				return agility >= ZEAH_CLIMB_AGILITY ? ZEAH_CLIMB_ROCKS : ZEAH_RUNESTONE;
			}
			// back from the dark altar (first trip, after chiselling): 69 climb, 52 shortcut, or walk
			return agility >= ZEAH_SHORTCUT_AGILITY ? ZEAH_SHORTCUT
				: agility >= ZEAH_LOW_AGILITY ? ZEAH_LOW_TO_STONE : ZEAH_RUNESTONE;
		}
		return ZEAH_RUNESTONE;
	}

	private int zeahBlockSlot = -1;   // the dark block closest to the chisel (inventory slot)

	/** Inventory slot of the dark essence block closest to the chisel (4 per row), or -1. */
	static int closestToChisel(net.runelite.api.Item[] items)
	{
		int chisel = -1;
		for (int i = 0; i < items.length; i++)
		{
			if (items[i] != null && items[i].getId() == CHISEL)
			{
				chisel = i;
			}
		}
		int best = -1;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < items.length; i++)
		{
			if (items[i] != null && items[i].getId() == RuneIds.DARK_ESSENCE_BLOCK)
			{
				// same row first (the block next to it), then the row above
				double d = chisel < 0 ? i : Math.hypot(i % 4 - chisel % 4, 1.5 * (i / 4 - chisel / 4));
				if (d < bestD)
				{
					bestD = d;
					best = i;
				}
			}
		}
		return best;
	}

	/** Zeah: the chisel and the dark block closest to it are highlighted when you should chisel. */
	boolean zeahItem(int itemId, int slot)
	{
		if (!config.highlightRoute() || (itemId != CHISEL && itemId != RuneIds.DARK_ESSENCE_BLOCK))
		{
			return false;
		}
		if (itemId == RuneIds.DARK_ESSENCE_BLOCK && slot != zeahBlockSlot)
		{
			return false;
		}
		return zeahObject() == 0;
	}

	/** Objects with this ID in view (on your floor). The Zeah runestone: every one you can chip now. */
	private java.util.List<net.runelite.api.TileObject> objectsInView(int id)
	{
		java.util.List<net.runelite.api.TileObject> out = new java.util.ArrayList<>();
		if (id == ZEAH_RUNESTONE)
		{
			// the runestones change between "can chip" and depleted (other IDs): find them by name
			for (net.runelite.api.GameObject o : runestones)
			{
				net.runelite.api.ObjectComposition def = client.getObjectDefinition(o.getId());
				if (def != null && def.getImpostorIds() != null && def.getImpostor() != null)
				{
					def = def.getImpostor();
				}
				if (o.getPlane() == client.getPlane() && def != null && hasOption(def.getActions(), "chip"))
				{
					out.add(o);
				}
			}
			return out;
		}
		for (net.runelite.api.TileObject o : routeObjects.getOrDefault(id, java.util.Collections.emptyList()))
		{
			if (o.getPlane() == client.getPlane())
			{
				out.add(o);
			}
		}
		return out;
	}

	/** The object closest to you, or null. */
	private net.runelite.api.TileObject closest(java.util.List<net.runelite.api.TileObject> list)
	{
		net.runelite.api.TileObject best = null;
		int bestD = Integer.MAX_VALUE;
		for (net.runelite.api.TileObject o : list)
		{
			int d = distanceTo(o);
			if (best == null || d < bestD)
			{
				bestD = d;
				best = o;
			}
		}
		return best;
	}

	/** The one of these you clicked last and stand next to (you are using it: it stays lit), or null. */
	private net.runelite.api.TileObject clickedOne(java.util.List<net.runelite.api.TileObject> list)
	{
		if (lastClickedObject == null)
		{
			return null;
		}
		for (net.runelite.api.TileObject o : list)
		{
			WorldPoint w = o.getWorldLocation();
			if (w != null && distanceTo(o) <= 2 && w.getPlane() == lastClickedObject.getPlane()
				&& Math.abs(w.getX() - lastClickedObject.getX()) <= 3 && Math.abs(w.getY() - lastClickedObject.getY()) <= 3)
			{
				return o;
			}
		}
		return null;
	}

	/** Tiles from you to the nearest edge of this object (big rocks: not to one point of it). */
	private int distanceTo(net.runelite.api.TileObject o)
	{
		net.runelite.api.Player p = client.getLocalPlayer();
		if (p == null || p.getWorldLocation() == null)
		{
			return Integer.MAX_VALUE - 1;
		}
		WorldPoint me = p.getWorldLocation();
		int minX;
		int minY;
		int maxX;
		int maxY;
		if (o instanceof net.runelite.api.GameObject)
		{
			net.runelite.api.GameObject g = (net.runelite.api.GameObject) o;
			WorldPoint a = WorldPoint.fromScene(client, g.getSceneMinLocation().getX(), g.getSceneMinLocation().getY(), g.getPlane());
			WorldPoint b = WorldPoint.fromScene(client, g.getSceneMaxLocation().getX(), g.getSceneMaxLocation().getY(), g.getPlane());
			minX = Math.min(a.getX(), b.getX());
			maxX = Math.max(a.getX(), b.getX());
			minY = Math.min(a.getY(), b.getY());
			maxY = Math.max(a.getY(), b.getY());
		}
		else
		{
			WorldPoint w = o.getWorldLocation();
			if (w == null)
			{
				return Integer.MAX_VALUE - 1;
			}
			minX = maxX = w.getX();
			minY = maxY = w.getY();
		}
		int dx = me.getX() < minX ? minX - me.getX() : me.getX() > maxX ? me.getX() - maxX : 0;
		int dy = me.getY() < minY ? minY - me.getY() : me.getY() > maxY ? me.getY() - maxY : 0;
		return Math.max(dx, dy);
	}

	/** You stand within 'range' tiles of an object with this ID. */
	private boolean near(int id, int range)
	{
		net.runelite.api.Player p = client.getLocalPlayer();
		if (p == null || p.getWorldLocation() == null)
		{
			return false;
		}
		net.runelite.api.coords.WorldPoint me = p.getWorldLocation();
		for (net.runelite.api.TileObject o : objectsInView(id))
		{
			net.runelite.api.coords.WorldPoint w = o.getWorldLocation();
			if (w != null && Math.abs(w.getX() - me.getX()) <= range && Math.abs(w.getY() - me.getY()) <= range)
			{
				return true;
			}
		}
		return false;
	}

	Color teleportHighlight(int itemId, int slot)
	{
		if (zeahItem(itemId, slot))
		{
			return RouteOverlay.ROUTE;
		}
		Teleport t = teleportNow;
		if (t == null)
		{
			return null;
		}
		TeleportItem wanted = t == Teleport.BANK ? config.bankTeleport() : config.altarTeleport();
		if (wanted == null || wanted == TeleportItem.NONE)
		{
			return null;
		}
		String name = itemNames.computeIfAbsent(itemId, id -> itemManager.getItemComposition(id).getName());
		if (!wanted.matches(name))
		{
			return null;
		}
		return t == Teleport.BANK ? TeleportOverlay.BANK : TeleportOverlay.ALTAR;
	}

	/** The game's hidden wear counter of a pouch (0 = just repaired). */
	private int pouchWear(PouchTracker.Pouch p)
	{
		switch (p)
		{
			case MEDIUM:
				return client.getVarpValue(VarPlayerID.RCU_POUCH_DEGRADATION_MED);
			case LARGE:
				return client.getVarpValue(VarPlayerID.RCU_POUCH_DEGRADATION_LARGE);
			case GIANT:
				return client.getVarpValue(VarPlayerID.RCU_POUCH_DEGRADATION_GIANT);
			default:
				return client.getVarbitValue(VarbitID.RCU_POUCH_DEGRADATION_COLOSSAL);
		}
	}

	private void warnPouch(PouchTracker.Pouch p)
	{
		int rc = client.getRealSkillLevel(Skill.RUNECRAFT);
		int left = pouchesDecayed.contains(p) ? 0 : pouchFills.getOrDefault(p, 0);
		warn("pouch", p.getName() + " durability " + left + "/" + PouchTracker.fullFills(p, rc)
			+ ". Repair it with Astral Contact (Dark Mage).");
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
		else if ((mageTalks || repairOption) && config.warnPouch() && anyPouchNeedsRepair())
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
		return p != null && config.warnPouch() && pouchesCarried.contains(p)
			&& PouchTracker.needsRepair(pouchFills.getOrDefault(p, 99), pouchesDecayed.contains(p));
	}

	/** A pouch you carry needs a repair (for the spellbook highlight). */
	boolean anyPouchNeedsRepair()
	{
		if (!config.warnPouch())
		{
			return false;
		}
		for (PouchTracker.Pouch p : pouchesCarried)
		{
			if (PouchTracker.needsRepair(pouchFills.getOrDefault(p, 99), pouchesDecayed.contains(p)))
			{
				return true;
			}
		}
		return false;
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
