package com.aorc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel. Top: a card with the most important numbers. Below: sections you can fold
 * (Session, Trips, Goals, History, Runes crafted), then the reset menu and Runey.
 * Labels are kept between updates; only their text changes.
 */
class AorcPanel extends PluginPanel
{
	private static final Color BLOOD = new Color(200, 40, 40);
	private static final Color TITLE_RED = new Color(224, 64, 64);
	private static final Color BONUS = new Color(120, 200, 120);
	private static final Color GOLD = new Color(255, 210, 60);
	private static final Color TIME = new Color(95, 215, 232);
	private static final Color MUTED = new Color(150, 150, 150);
	private static final Color OTHER_RUNE = new Color(90, 160, 232);
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color INSET = new Color(22, 22, 22);
	private static final Color LINE = new Color(60, 60, 60);

	private final ItemManager itemManager;
	private final AorcPlugin plugin;
	private final NumberFormat nf = NumberFormat.getIntegerInstance();

	// top card
	private final Dot statusDot = new Dot();
	private final JLabel statusLabel = new JLabel();
	private final JButton pauseButton = new JButton("Pause");
	private final JLabel bigNumber = new JLabel("-");
	private final JLabel bigUnit = new JLabel();
	private final JLabel heroSub = new JLabel(" ");
	private final JLabel chipXp = new JLabel("-");
	private final JLabel chipGp = new JLabel("-");
	private final JLabel chipBonus = new JLabel("-");

	// sections
	private final JPanel sessionBody = new JPanel(new GridLayout(0, 2, 4, 3));
	private final Grid session = new Grid(sessionBody);
	private final Bar essenceBar = new Bar();
	private final JPanel tripsBody = new JPanel(new GridLayout(0, 2, 4, 3));
	private final Grid tripsGrid = new Grid(tripsBody);
	private final JLabel[] levelRow = row2();
	private final Bar levelBar = new Bar();
	private final JLabel[] runeGoalRow = row2();
	private final JPanel runeGoalLine = line(runeGoalRow);
	private final Bar runeGoalBar = new Bar();
	private final JLabel goalSub = new JLabel();
	private final JPanel historyPanel = new JPanel(new GridLayout(0, 2, 4, 3));
	private final Grid historyGrid = new Grid(historyPanel);
	private final JPanel typesPanel = new JPanel(new GridBagLayout());
	private final List<Integer> typeIds = new ArrayList<>();
	private final List<JLabel[]> typeLabels = new ArrayList<>();
	private final JPanel runesBody = new JPanel();
	private final List<Integer> rowIds = new ArrayList<>();
	private final List<JLabel[]> rowLabels = new ArrayList<>();
	private final List<Bar> rowBars = new ArrayList<>();
	private final JLabel emptyLabel = new JLabel("Craft some runes to start.");
	private final Section sessionSec;
	private final Section tripsSec;
	private final Section goalsSec;
	private final Section historySec;
	private final Section runesSec;

	// Trips filter: "" = where you craft now, "*" = all trips, else a place
	private final JComboBox<String> tripFilterBox = new JComboBox<>();
	private final List<String> tripFilterKeys = new ArrayList<>();
	private volatile String tripFilter = "";
	private boolean fillingTrips;

	// History filter: "All runes" or one rune type you crafted
	private final JComboBox<String> historyFilterBox = new JComboBox<>();
	private final List<Integer> filterIds = new ArrayList<>();
	private volatile int historyFilter;
	private boolean fillingFilter;

	private volatile PanelData latest = new PanelData();
	private final BuddyPanel buddy;
	private final HypeBuddy hype = new HypeBuddy();

	@Inject
	AorcPanel(AorcPlugin plugin, ItemManager itemManager, ClientThread clientThread)
	{
		// wrapped in a scroll pane: a long panel scrolls instead of making the client window taller
		super(true);
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.buddy = new BuddyPanel(itemManager);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel content = column();

		content.add(buildHero());
		content.add(gap(6));

		// Session
		JPanel sessionWrap = column();
		sessionBody.setOpaque(false);
		sessionBody.setAlignmentX(LEFT_ALIGNMENT);
		sessionWrap.add(sessionBody);
		essenceBar.setVisible(false);
		sessionWrap.add(essenceBar);
		sessionSec = new Section("session", "Session", sessionWrap);
		content.add(sessionSec);
		content.add(gap(6));

		// Trips: dropdown (where you craft now / all / one place) and the numbers
		JPanel tripsWrap = column();
		tripFilterBox.setToolTipText("Trips where you craft now, all trips, or one place (like Blood - Zeah or Blood - Mory)");
		tripFilterBox.setAlignmentX(LEFT_ALIGNMENT);
		tripFilterBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		tripFilterKeys.add("");
		tripFilterKeys.add("*");
		tripFilterBox.addItem("This location");
		tripFilterBox.addItem("All trips");
		tripFilterBox.addActionListener(e ->
		{
			int i = tripFilterBox.getSelectedIndex();
			if (fillingTrips || i < 0 || i >= tripFilterKeys.size())
			{
				return;
			}
			tripFilter = tripFilterKeys.get(i);
			run(clientThread, () -> plugin.refreshPanel());
		});
		tripsWrap.add(tripFilterBox);
		tripsWrap.add(gap(5));
		tripsBody.setOpaque(false);
		tripsBody.setAlignmentX(LEFT_ALIGNMENT);
		tripsWrap.add(tripsBody);
		tripsSec = new Section("trips", "Trips", tripsWrap);
		content.add(tripsSec);
		content.add(gap(6));

		// Goals: lines with progress bars
		JPanel goalsWrap = column();
		goalsWrap.add(line(levelRow));
		goalsWrap.add(levelBar);
		goalsWrap.add(runeGoalLine);
		goalsWrap.add(runeGoalBar);
		goalSub.setForeground(MUTED);
		goalSub.setFont(FontManager.getRunescapeSmallFont());
		goalSub.setAlignmentX(LEFT_ALIGNMENT);
		goalsWrap.add(goalSub);
		goalsSec = new Section("goals", "Goals", goalsWrap);
		content.add(goalsSec);
		content.add(gap(6));

		// History: dropdown, numbers, table per rune type
		JPanel historyWrap = column();
		historyFilterBox.setToolTipText("Show the history of all runes, or of one rune type");
		historyFilterBox.setAlignmentX(LEFT_ALIGNMENT);
		historyFilterBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		filterIds.add(0);
		historyFilterBox.addItem("All runes");
		historyFilterBox.addActionListener(e ->
		{
			int i = historyFilterBox.getSelectedIndex();
			if (fillingFilter || i < 0 || i >= filterIds.size())
			{
				return;
			}
			historyFilter = filterIds.get(i);
			run(clientThread, () -> plugin.refreshPanel());
		});
		historyWrap.add(historyFilterBox);
		historyWrap.add(gap(5));
		historyPanel.setOpaque(false);
		historyPanel.setAlignmentX(LEFT_ALIGNMENT);
		historyWrap.add(historyPanel);
		historyWrap.add(gap(6));
		typesPanel.setOpaque(false);
		typesPanel.setAlignmentX(LEFT_ALIGNMENT);
		typesPanel.setToolTipText("Runes crafted per type: all time, and today.");
		historyWrap.add(typesPanel);
		historySec = new Section("history", "History", historyWrap);
		content.add(historySec);
		content.add(gap(6));

		// Runes crafted
		runesBody.setLayout(new BoxLayout(runesBody, BoxLayout.Y_AXIS));
		runesBody.setOpaque(false);
		emptyLabel.setForeground(MUTED);
		runesSec = new Section("runes", "Runes crafted", runesBody);
		content.add(runesSec);
		content.add(gap(6));

		// Reset menu + clear goal
		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setOpaque(false);
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		JButton reset = button("Reset \u25BE");
		JPopupMenu menu = new JPopupMenu();
		menu.add(menuItem("Reset per hour", "Per-hour numbers, active time and trips",
			() -> run(clientThread, () -> plugin.resetRates())));
		menu.add(menuItem("Reset rune count", "The rune counts (Runes crafted)",
			() -> run(clientThread, () -> plugin.resetRuneCount())));
		menu.add(menuItem("Reset bonus runes", "The bonus rune count",
			() -> run(clientThread, () -> plugin.resetBonusRunes())));
		menu.addSeparator();
		menu.add(menuItem("Reset all", "All of the above (goals and history stay)",
			() -> run(clientThread, () -> plugin.resetAll())));
		reset.addActionListener(e -> menu.show(reset, 0, reset.getHeight()));
		reset.setToolTipText("Reset numbers. Your goals and history are never reset.");
		JButton clearGoal = button("Clear goal");
		clearGoal.setToolTipText("Count the rune goal from 0 again. The reset menu never touches your goals.");
		clearGoal.addActionListener(e -> run(clientThread, () -> plugin.clearGoalProgress()));
		buttons.add(reset);
		buttons.add(clearGoal);
		content.add(buttons);
		content.add(gap(8));

		buddy.setAlignmentX(LEFT_ALIGNMENT);
		content.add(buddy);

		add(content, BorderLayout.NORTH);

		pauseButton.addActionListener(e -> run(clientThread, () -> plugin.togglePause()));
		update(new PanelData());

		// the panel is only updated while it is open; catch up when it is opened
		addHierarchyListener(e ->
		{
			if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing())
			{
				update(latest);
			}
		});
	}

	/** Run on the game thread (does nothing in previews without a plugin). */
	private void run(ClientThread clientThread, Runnable r)
	{
		if (plugin != null)
		{
			clientThread.invokeLater(r);
		}
	}

	// ---------- top card ----------

	private JPanel buildHero()
	{
		Card hero = new Card(BLOOD, CARD);
		hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
		hero.setBorder(BorderFactory.createEmptyBorder(8, 11, 8, 8));

		JLabel title = new JLabel("Amount of Runes Crafted");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(TITLE_RED);
		title.setAlignmentX(LEFT_ALIGNMENT);
		hero.add(title);
		hero.add(gap(2));

		// status light + text on the left, pause on the right
		JPanel status = new JPanel(new BorderLayout(4, 0));
		status.setOpaque(false);
		status.setAlignmentX(LEFT_ALIGNMENT);
		status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		// dot and text side by side (never wraps to a second line)
		JPanel left = new JPanel(new BorderLayout(5, 0));
		left.setOpaque(false);
		JPanel dotBox = new JPanel(new GridBagLayout());
		dotBox.setOpaque(false);
		dotBox.add(statusDot);
		left.add(dotBox, BorderLayout.WEST);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		left.add(statusLabel, BorderLayout.CENTER);
		status.add(left, BorderLayout.CENTER);
		pauseButton.setFocusPainted(false);
		pauseButton.setMargin(new Insets(2, 6, 2, 6));
		pauseButton.setToolTipText("Pause: the timers stop and nothing is counted. Resumes by itself when you gain Runecraft xp.");
		status.add(pauseButton, BorderLayout.EAST);
		hero.add(status);
		hero.add(gap(2));

		// big number: runes per hour of the rune you craft now
		JPanel big = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		big.setOpaque(false);
		big.setAlignmentX(LEFT_ALIGNMENT);
		Font bold = FontManager.getRunescapeBoldFont();
		bigNumber.setFont(bold.deriveFont(bold.getSize2D() * 2f));
		bigNumber.setForeground(Color.WHITE);
		bigUnit.setForeground(MUTED);
		bigUnit.setBorder(BorderFactory.createEmptyBorder(9, 6, 0, 0));
		big.add(bigNumber);
		big.add(bigUnit);
		big.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		hero.add(big);
		heroSub.setForeground(MUTED);
		heroSub.setFont(FontManager.getRunescapeSmallFont());
		heroSub.setAlignmentX(LEFT_ALIGNMENT);
		hero.add(heroSub);
		hero.add(gap(6));

		// three small tiles
		JPanel chips = new JPanel(new GridLayout(1, 3, 4, 0));
		chips.setOpaque(false);
		chips.setAlignmentX(LEFT_ALIGNMENT);
		chips.add(chip("XP / HR", chipXp, Color.WHITE));
		chips.add(chip("GP / HR", chipGp, GOLD));
		chips.add(chip("BONUS", chipBonus, BONUS));
		chips.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		hero.add(chips);
		return hero;
	}

	private static JPanel chip(String caption, JLabel value, Color color)
	{
		JPanel p = new Card(null, INSET);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(BorderFactory.createEmptyBorder(3, 5, 4, 3));
		JLabel c = new JLabel(caption);
		c.setFont(FontManager.getRunescapeSmallFont());
		c.setForeground(MUTED);
		value.setFont(FontManager.getRunescapeBoldFont());
		value.setForeground(color);
		p.add(c);
		p.add(value);
		return p;
	}

	// ---------- used by the plugin ----------

	/** Runey is visible in the side panel right now. */
	boolean isBuddyShowing()
	{
		return buddy.isVisible() && buddy.isShowing();
	}

	/** Runey spits out of the panel towards the game screen (any thread). */
	void spitAcross(boolean iron)
	{
		SwingUtilities.invokeLater(() -> buddy.spitAcross(iron));
	}

	/** Stop the buddy animation (plugin turned off). */
	void stop()
	{
		buddy.stop();
	}

	/** Trips filter chosen in the dropdown: "" = where you craft now, "*" = all, else a place (any thread). */
	String getTripFilter()
	{
		return tripFilter;
	}

	/** Trips dropdown: "This location (Blood - Zeah)", "All trips", then every place with trips. */
	private void updateTripChoices(List<String> placeNames, String shown)
	{
		List<String> keys = new ArrayList<>();
		keys.add("");
		keys.add("*");
		keys.addAll(placeNames);
		String hereText = "This location" + (shown != null && "".equals(tripFilter) ? " (" + shown + ")" : "");
		if (keys.equals(tripFilterKeys) && hereText.equals(tripFilterBox.getItemAt(0)))
		{
			return;
		}
		fillingTrips = true;
		try
		{
			String keep = tripFilter;
			tripFilterKeys.clear();
			tripFilterKeys.addAll(keys);
			tripFilterBox.removeAllItems();
			for (String k : keys)
			{
				tripFilterBox.addItem("".equals(k) ? hereText : "*".equals(k) ? "All trips" : k);
			}
			int i = Math.max(0, tripFilterKeys.indexOf(keep));
			tripFilterBox.setSelectedIndex(i);
			tripFilter = tripFilterKeys.get(i);
		}
		finally
		{
			fillingTrips = false;
		}
	}

	/** History filter chosen in the dropdown: 0 = all runes, else a rune item ID (any thread). */
	int getHistoryFilter()
	{
		return historyFilter;
	}

	/** Call from any thread; the panel updates on the Swing thread. */
	void post(PanelData data)
	{
		latest = data;
		SwingUtilities.invokeLater(() ->
		{
			// closed panel: skip the work (it catches up when opened)
			if (isShowing() && latest == data)
			{
				update(data);
			}
		});
	}

	// ---------- update ----------

	private void update(PanelData d)
	{
		// status light
		if (d.paused)
		{
			statusDot.setColor(Color.ORANGE);
			statusLabel.setForeground(Color.ORANGE);
			statusLabel.setText("Paused - " + PanelData.formatTime(d.activeMillis));
			pauseButton.setText("Resume");
		}
		else
		{
			statusDot.setColor(d.crafting ? new Color(86, 196, 86) : MUTED);
			statusLabel.setForeground(d.crafting ? new Color(127, 209, 127) : MUTED);
			statusLabel.setText((d.crafting ? "Crafting" : "Idle") + " - " + PanelData.formatTime(d.activeMillis));
			pauseButton.setText("Pause");
		}

		// big number
		if (d.mainName == null)
		{
			bigNumber.setText("-");
			bigUnit.setText("runes / hr");
			heroSub.setText("Craft some runes to start.");
		}
		else
		{
			String what = d.mainName.toLowerCase();
			bigNumber.setText(GpFormat.shortRate(d.mainPerHour));
			bigUnit.setText(what + " / hr");
			heroSub.setText(GpFormat.shortRate(d.mainCount) + " " + what
				+ (d.mainName.equals("All runes") ? "" : " runes") + ", +" + GpFormat.shortRate(d.mainToday) + " today");
		}
		chipXp.setText(GpFormat.shortRate(d.xpPerHour));
		chipGp.setText(GpFormat.shortGp(d.gpPerHour));
		chipBonus.setText(GpFormat.multiplier(d.bonusPercent));

		// Session
		session.begin();
		if (d.essenceUsed > 0)
		{
			session.cell("Profit / hr", GpFormat.shortGp(d.profitPerHour), GOLD);
		}
		session.cell("All runes / hr", GpFormat.shortRate(d.allRunesPerHour), Color.WHITE);
		session.cell("Crafts", fmt(d.crafts), Color.WHITE);
		if (d.showBlocks)
		{
			// only at Zeah (blood / soul altar there, or the Dark Altar)
			session.cell("Blocks imbued", fmt(d.blocksImbued), Color.WHITE);
		}
		session.cell("Bonus runes", GpFormat.shortRate(d.bonusRunes), BONUS);
		if (d.essenceCharges >= 0)
		{
			session.cell("Blood essence", (d.essenceEstimate ? "~" : "") + fmt(d.essenceCharges),
				d.essenceCharges <= 100 ? Color.ORANGE : BONUS);
		}
		session.end();
		essenceBar.setVisible(d.essenceCharges >= 0);
		essenceBar.set(d.essenceCharges / (double) EssenceCharges.FULL, d.essenceCharges <= 100 ? Color.ORANGE : BONUS);
		sessionSec.setSummary(GpFormat.shortRate(d.xpPerHour) + " xp / hr");

		// Trips
		tripsGrid.begin();
		tripsGrid.cell("Current trip", time(d.currentTripMillis), TIME);
		if (d.currentTripRunes > 0)
		{
			tripsGrid.cell("Runes this trip", fmt(d.currentTripRunes), Color.LIGHT_GRAY);
		}
		tripsGrid.cell("Trips", fmt(d.trips), Color.WHITE);
		tripsGrid.cell("Avg trip", time(d.avgTripMillis), Color.WHITE);
		tripsGrid.cell("Fastest", time(d.fastestTripMillis), BONUS);
		tripsGrid.cell("Last trip", time(d.lastTripMillis), Color.LIGHT_GRAY);
		tripsGrid.cell("Avg runes / trip", fmt(d.avgTripRunes), Color.WHITE);
		tripsGrid.end();
		updateTripChoices(d.tripPlaces, d.tripPlace);
		tripsSec.setSummary(d.currentTripMillis >= 0 ? "now " + time(d.currentTripMillis) : fmt(d.trips) + " trips");

		// Goals
		boolean levelDone = d.currentLevel >= d.goalLevel && d.currentLevel > 0;
		levelRow[0].setText("Level " + d.goalLevel);
		levelRow[1].setText(levelDone ? "Reached!" : time(d.goalLevelMillis));
		levelRow[1].setForeground(levelDone ? BONUS : TIME);
		levelBar.set(d.levelProgress, TIME);
		boolean runeGoal = d.goalRunes > 0;
		runeGoalLine.setVisible(runeGoal);
		runeGoalBar.setVisible(runeGoal);
		if (runeGoal)
		{
			boolean done = d.runesLeft <= 0;
			runeGoalRow[0].setText(fmt(d.goalRunes) + " " + d.goalRuneName);
			runeGoalRow[1].setText(done ? "Reached!" : time(d.goalRunesMillis));
			runeGoalRow[1].setForeground(done ? BONUS : TIME);
			runeGoalBar.set(d.runeGoalProgress, "blood".equals(d.goalRuneName) ? BLOOD : OTHER_RUNE);
		}
		String sub = levelDone ? "" : GpFormat.shortGp(d.xpLeft) + " xp left";
		if (runeGoal && d.runesLeft > 0)
		{
			sub += (sub.isEmpty() ? "" :  ", ") + fmt(d.runesLeft) + " runes left";
		}
		goalSub.setText(sub);
		goalSub.setVisible(!sub.isEmpty());
		goalsSec.setSummary(levelDone ? "level " + d.goalLevel + " reached" : "lvl " + d.goalLevel + " in " + time(d.goalLevelMillis));

		// History
		updateFilterChoices(d.historyTypes);
		historyGrid.begin();
		historyGrid.cell("Today", GpFormat.shortRate(d.todayRunes) + " runes", Color.WHITE);
		historyGrid.cell("", d.todayMillis < 0 ? "" : PanelData.formatTime(d.todayMillis), MUTED);
		historyGrid.cell("Best day", d.bestDate == null ? "-" : GpFormat.shortRate(d.bestRunes) + " runes", BLOOD);
		historyGrid.cell(PanelData.formatDate(d.bestDate), d.bestDate == null || d.bestMillis < 0 ? "" : PanelData.formatTime(d.bestMillis), MUTED);
		historyGrid.cell("All time", GpFormat.shortRate(d.allRunes) + " runes", Color.WHITE);
		historyGrid.cell(d.days + (d.days == 1 ? " day" : " days"), d.allMillis < 0 ? "" : PanelData.formatTime(d.allMillis), MUTED);
		historyGrid.end();
		typesPanel.setVisible(d.historyFilter == 0);
		updateTypeRows(d.historyTypes);
		historySec.setSummary(GpFormat.shortRate(d.todayRunes) + " today");

		// Runes crafted
		updateRuneRows(d.rows);
		runesSec.setSummary(d.rows.size() + (d.rows.size() == 1 ? " type" : " types"));

		buddy.setVisible(d.showBuddy);
		if (d.showBuddy)
		{
			buddy.setAccountType(d.accountType);
			buddy.setMessage(hype.message(d, System.currentTimeMillis()));
		}
	}

	// ---------- history ----------

	/** Dropdown: "All runes" plus every rune type in your history (kept in rune order). */
	private void updateFilterChoices(List<PanelData.TypeRow> types)
	{
		List<Integer> ids = new ArrayList<>();
		ids.add(0);
		for (PanelData.TypeRow r : types)
		{
			if (r.itemId != DayLog.OTHER)
			{
				ids.add(r.itemId);
			}
		}
		if (ids.equals(filterIds))
		{
			return;
		}
		fillingFilter = true;
		try
		{
			int keep = historyFilter;
			filterIds.clear();
			filterIds.addAll(ids);
			historyFilterBox.removeAllItems();
			for (int id : ids)
			{
				historyFilterBox.addItem(id == 0 ? "All runes" : RuneIds.RUNES.get(id));
			}
			int i = Math.max(0, filterIds.indexOf(keep));
			historyFilterBox.setSelectedIndex(i);
			historyFilter = filterIds.get(i);
		}
		finally
		{
			fillingFilter = false;
		}
	}

	/**
	 * History per rune type as a table: type | all time | today, with a thin line between
	 * all time and today. Rows are built again only when the rune types change.
	 */
	private void updateTypeRows(List<PanelData.TypeRow> rows)
	{
		List<Integer> ids = new ArrayList<>();
		for (PanelData.TypeRow r : rows)
		{
			ids.add(r.itemId);
		}
		if (!ids.equals(typeIds) || typesPanel.getComponentCount() == 0)
		{
			typeIds.clear();
			typeIds.addAll(ids);
			typeLabels.clear();
			typesPanel.removeAll();
			addTypeRow(0, "Rune type", "All time", "Today", Color.LIGHT_GRAY);
			for (int i = 0; i < rows.size(); i++)
			{
				PanelData.TypeRow r = rows.get(i);
				typeLabels.add(addTypeRow(i + 1, r.name, "", "",
					r.itemId == RuneIds.BLOOD_RUNE ? BLOOD : Color.WHITE));
			}
			if (rows.isEmpty())
			{
				addTypeRow(1, "-", "", "", Color.LIGHT_GRAY);
			}
			typesPanel.revalidate();
			typesPanel.repaint();
		}
		for (int i = 0; i < rows.size(); i++)
		{
			PanelData.TypeRow r = rows.get(i);
			typeLabels.get(i)[0].setText(GpFormat.shortRate(r.allTime));
			typeLabels.get(i)[1].setText(r.today > 0 ? "+" + GpFormat.shortRate(r.today) : "-");
		}
	}

	/** One table row; returns the all-time and today labels. */
	private JLabel[] addTypeRow(int row, String name, String allTime, String today, Color color)
	{
		boolean head = row == 0;
		JLabel n = new JLabel(name);
		n.setForeground(head ? MUTED : Color.LIGHT_GRAY);
		JLabel a = new JLabel(allTime, SwingConstants.RIGHT);
		a.setForeground(head ? MUTED : color);
		JLabel t = new JLabel(today, SwingConstants.RIGHT);
		t.setForeground(MUTED);
		// same top/bottom padding on every label and no gaps, so the line runs unbroken
		n.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		a.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 6));
		t.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 1, 0, 0, LINE),
			BorderFactory.createEmptyBorder(2, 6, 2, 0)));

		GridBagConstraints c = new GridBagConstraints();
		c.gridy = row;
		c.fill = GridBagConstraints.BOTH;
		c.gridx = 0;
		c.weightx = 1;
		typesPanel.add(n, c);
		c.gridx = 1;
		c.weightx = 0;
		typesPanel.add(a, c);
		c.gridx = 2;
		typesPanel.add(t, c);
		return new JLabel[]{a, t};
	}

	// ---------- runes crafted ----------

	/** Rune rows: same rune types = only new text; other types = build the rows again. */
	private void updateRuneRows(List<PanelData.Row> rows)
	{
		List<Integer> ids = new ArrayList<>();
		int max = 1;
		for (PanelData.Row r : rows)
		{
			ids.add(r.itemId);
			max = Math.max(max, r.count);
		}
		if (!ids.equals(rowIds) || runesBody.getComponentCount() == 0)
		{
			rowIds.clear();
			rowIds.addAll(ids);
			rowLabels.clear();
			rowBars.clear();
			runesBody.removeAll();
			if (rows.isEmpty())
			{
				runesBody.add(emptyLabel);
			}
			for (int i = 0; i < rows.size(); i++)
			{
				runesBody.add(runeRow(rows.get(i), i > 0));
			}
			runesBody.revalidate();
			runesBody.repaint();
		}
		for (int i = 0; i < rows.size(); i++)
		{
			PanelData.Row r = rows.get(i);
			rowLabels.get(i)[0].setText(r.name);
			rowLabels.get(i)[1].setText("<html><div style='text-align:right'>" + GpFormat.shortRate(r.count)
				+ "<br><span style='color:#969696'>" + GpFormat.shortRate(r.perHour) + " / hr</span></div></html>");
			rowBars.get(i).set(r.count / (double) max, r.itemId == RuneIds.BLOOD_RUNE ? BLOOD : OTHER_RUNE);
		}
	}

	private JPanel runeRow(PanelData.Row r, boolean lineAbove)
	{
		JPanel row = new JPanel(new BorderLayout(7, 0));
		row.setOpaque(false);
		row.setBorder(lineAbove
			? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE), BorderFactory.createEmptyBorder(4, 0, 2, 0))
			: BorderFactory.createEmptyBorder(0, 0, 2, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		itemManager.getImage(r.itemId).addTo(icon);
		row.add(icon, BorderLayout.WEST);

		// name with a small bar: this rune's share (the most crafted rune = full bar)
		JPanel mid = column();
		JLabel name = new JLabel();
		name.setForeground(r.itemId == RuneIds.BLOOD_RUNE ? TITLE_RED : Color.WHITE);
		name.setAlignmentX(LEFT_ALIGNMENT);
		mid.add(gap(5));
		mid.add(name);
		Bar bar = new Bar();
		mid.add(bar);
		row.add(mid, BorderLayout.CENTER);

		JLabel right = new JLabel();
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		right.setForeground(Color.WHITE);
		row.add(right, BorderLayout.EAST);

		rowLabels.add(new JLabel[]{name, right});
		rowBars.add(bar);
		return row;
	}

	// ---------- small parts ----------

	private static JLabel[] row2()
	{
		JLabel l = new JLabel();
		l.setForeground(Color.LIGHT_GRAY);
		JLabel r = new JLabel();
		r.setHorizontalAlignment(SwingConstants.RIGHT);
		return new JLabel[]{l, r};
	}

	private static JPanel line(JLabel[] pair)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		p.add(pair[0], BorderLayout.WEST);
		p.add(pair[1], BorderLayout.EAST);
		return p;
	}

	private static JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	private static JButton button(String text)
	{
		JButton b = new JButton(text);
		b.setFocusPainted(false);
		return b;
	}

	private static JMenuItem menuItem(String text, String tip, Runnable action)
	{
		JMenuItem m = new JMenuItem(text);
		m.setToolTipText(tip);
		m.addActionListener(e -> action.run());
		return m;
	}

	private static JPanel gap(int height)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(1, height));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	/** Time text, or "-" when unknown. */
	private static String time(long millis)
	{
		return millis < 0 ? "-" : PanelData.formatTime(millis);
	}

	private String fmt(long v)
	{
		return nf.format(v);
	}

	/** A dark card with rounded corners, optionally with a coloured stripe on the left. */
	private static class Card extends JPanel
	{
		private final Color stripe;
		private final Color fill;

		Card(Color stripe, Color fill)
		{
			this.stripe = stripe;
			this.fill = fill;
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
			if (stripe != null)
			{
				g2.setColor(stripe);
				g2.fillRoundRect(0, 0, 3, getHeight(), 3, 3);
			}
			g2.dispose();
			super.paintComponent(g);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	/**
	 * A card with a title you can click to fold it away. Folded, it shows a short summary.
	 * Open / folded is remembered in the RuneLite settings.
	 */
	private class Section extends Card
	{
		private final JComponent body;
		private final JLabel summary = new JLabel();
		private final JLabel chevron = new JLabel();
		private boolean open;

		Section(String key, String title, JComponent body)
		{
			super(null, CARD);
			this.body = body;
			setLayout(new BorderLayout(0, 5));
			setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));

			JPanel head = new JPanel(new BorderLayout(4, 0));
			head.setOpaque(false);
			JLabel t = new JLabel(title);
			t.setFont(FontManager.getRunescapeBoldFont());
			t.setForeground(Color.WHITE);
			head.add(t, BorderLayout.WEST);
			JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			right.setOpaque(false);
			summary.setForeground(MUTED);
			summary.setFont(FontManager.getRunescapeSmallFont());
			chevron.setForeground(MUTED);
			right.add(summary);
			right.add(chevron);
			head.add(right, BorderLayout.EAST);
			head.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			head.setToolTipText("Click to fold or unfold");
			head.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					setOpen(!open);
					if (plugin != null)
					{
						plugin.setSectionOpen(key, open);
					}
				}
			});
			add(head, BorderLayout.NORTH);
			add(body, BorderLayout.CENTER);
			setOpen(plugin == null || plugin.isSectionOpen(key));
		}

		void setOpen(boolean open)
		{
			this.open = open;
			body.setVisible(open);
			summary.setVisible(!open);
			chevron.setText(open ? "\u25BE" : "\u25B8");
			revalidate();
			repaint();
		}

		void setSummary(String text)
		{
			summary.setText(text);
		}
	}

	/** A thin rounded progress bar. */
	private static class Bar extends JComponent
	{
		private double fraction;
		private Color color = BONUS;

		Bar()
		{
			setPreferredSize(new Dimension(10, 9));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 9));
			setAlignmentX(LEFT_ALIGNMENT);
		}

		void set(double fraction, Color color)
		{
			double f = Double.isNaN(fraction) ? 0 : Math.max(0, Math.min(1, fraction));
			if (f != this.fraction || !color.equals(this.color))
			{
				this.fraction = f;
				this.color = color;
				repaint();
			}
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int y = 2;
			int h = 5;
			g2.setColor(INSET);
			g2.fillRoundRect(0, y, getWidth(), h, h, h);
			int w = (int) Math.round(getWidth() * fraction);
			if (w > 0)
			{
				g2.setColor(color);
				g2.fillRoundRect(0, y, Math.max(h, w), h, h, h);
			}
			g2.dispose();
		}
	}

	/** The small status light (green crafting, grey idle, orange paused). */
	private static class Dot extends JComponent
	{
		private Color color = MUTED;

		Dot()
		{
			setPreferredSize(new Dimension(8, 8));
		}

		void setColor(Color c)
		{
			if (!c.equals(color))
			{
				color = c;
				repaint();
			}
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
			g2.fillOval(0, 0, 8, 8);
			g2.setColor(color);
			g2.fillOval(1, 1, 6, 6);
			g2.dispose();
		}
	}

	/**
	 * A 2-column grid whose labels are kept between updates: only the text changes.
	 * Labels are only added or removed when the number of lines changes.
	 */
	private static final class Grid
	{
		private final JPanel panel;
		private final List<JLabel[]> cells = new ArrayList<>();
		private int used;

		Grid(JPanel panel)
		{
			this.panel = panel;
		}

		void begin()
		{
			used = 0;
		}

		void cell(String left, String right, Color color)
		{
			JLabel[] pair;
			if (used < cells.size())
			{
				pair = cells.get(used);
			}
			else
			{
				pair = new JLabel[]{new JLabel(), new JLabel()};
				pair[0].setForeground(Color.LIGHT_GRAY);
				pair[1].setHorizontalAlignment(SwingConstants.RIGHT);
				cells.add(pair);
			}
			if (pair[0].getParent() != panel)
			{
				panel.add(pair[0]);
				panel.add(pair[1]);
				panel.revalidate();
			}
			pair[0].setText(left);
			pair[1].setText(right);
			if (!color.equals(pair[1].getForeground()))
			{
				pair[1].setForeground(color);
			}
			used++;
		}

		void end()
		{
			if (panel.getComponentCount() > used * 2)
			{
				while (panel.getComponentCount() > used * 2)
				{
					panel.remove(panel.getComponentCount() - 1);
				}
				panel.revalidate();
				panel.repaint();
			}
		}
	}
}
