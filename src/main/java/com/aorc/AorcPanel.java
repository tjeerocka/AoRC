package com.aorc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.HierarchyEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Sidebar panel: all rune types with count and per hour, plus totals and resets. */
class AorcPanel extends PluginPanel
{
	private static final Color BLOOD = new Color(200, 40, 40);
	private static final Color BONUS = new Color(120, 200, 120);

	private final ItemManager itemManager;
	private final NumberFormat nf = NumberFormat.getIntegerInstance();

	private final JLabel timeLabel = new JLabel();
	private final JButton pauseButton = new JButton("Pause");
	private final JPanel statsPanel = new JPanel(new GridLayout(0, 2, 4, 3));
	private final JPanel historyPanel = new JPanel(new GridLayout(0, 2, 4, 3));
	private final JPanel tripsPanel = new JPanel(new GridLayout(0, 2, 4, 3));
	private final JPanel goalsPanel = new JPanel(new GridLayout(0, 2, 4, 3));
	private final JPanel runesPanel = new JPanel();
	private final JLabel emptyLabel = new JLabel("Craft some runes to start.");
	private final Grid stats = new Grid(statsPanel);
	private final Grid tripsGrid = new Grid(tripsPanel);
	private final Grid goalsGrid = new Grid(goalsPanel);
	private final Grid historyGrid = new Grid(historyPanel);
	private final JPanel typesPanel = new JPanel(new GridLayout(0, 2, 4, 3));
	private final Grid typesGrid = new Grid(typesPanel);
	// History filter: "All runes" or one rune type you crafted
	private final JComboBox<String> historyFilterBox = new JComboBox<>();
	private final List<Integer> filterIds = new ArrayList<>();
	private volatile int historyFilter;
	private boolean fillingFilter;
	// rune rows are kept and only get new text; rebuilt only when the rune types change
	private final List<Integer> rowIds = new ArrayList<>();
	private final List<JLabel[]> rowLabels = new ArrayList<>();
	private volatile PanelData latest = new PanelData();
	private final BuddyPanel buddy;
	private final HypeBuddy hype = new HypeBuddy();


	@Inject
	AorcPanel(AorcPlugin plugin, ItemManager itemManager, ClientThread clientThread)
	{
		// wrapped in a scroll pane: a long panel scrolls instead of making the client window taller
		super(true);
		this.itemManager = itemManager;
		this.buddy = new BuddyPanel(itemManager);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Amount of Runes Crafted");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(BLOOD);
		title.setAlignmentX(LEFT_ALIGNMENT);
		content.add(title);

		// active time on the left, Pause / Resume on the right
		JPanel timeRow = new JPanel(new BorderLayout(4, 0));
		timeRow.setOpaque(false);
		timeRow.setAlignmentX(LEFT_ALIGNMENT);
		timeLabel.setForeground(Color.LIGHT_GRAY);
		timeRow.add(timeLabel, BorderLayout.CENTER);
		pauseButton.setFocusPainted(false);
		pauseButton.setToolTipText("Pause: the timers stop and nothing is counted. Resumes by itself when you gain Runecraft xp.");
		pauseButton.addActionListener(e -> clientThread.invokeLater(plugin::togglePause));
		timeRow.add(pauseButton, BorderLayout.EAST);
		timeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		content.add(timeRow);
		content.add(spacer(8));

		statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		statsPanel.setAlignmentX(LEFT_ALIGNMENT);
		content.add(statsPanel);
		content.add(spacer(8));

		content.add(box("Trips", tripsPanel));
		content.add(spacer(8));
		content.add(box("Goals", goalsPanel));
		content.add(spacer(4));
		JButton clearGoal = button("Clear goal progress", () -> clientThread.invokeLater(plugin::clearGoalProgress));
		clearGoal.setToolTipText("Count the rune goal from 0 again. The reset buttons never touch your goals.");
		clearGoal.setAlignmentX(LEFT_ALIGNMENT);
		clearGoal.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		content.add(clearGoal);
		content.add(spacer(8));

		// "History" title with the rune type dropdown next to it
		JPanel historyHead = new JPanel(new BorderLayout(6, 0));
		historyHead.setOpaque(false);
		historyHead.setAlignmentX(LEFT_ALIGNMENT);
		historyHead.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		JLabel historyTitle = new JLabel("History");
		historyTitle.setFont(FontManager.getRunescapeBoldFont());
		historyTitle.setForeground(Color.WHITE);
		historyHead.add(historyTitle, BorderLayout.WEST);
		historyFilterBox.setToolTipText("Show the history of all runes, or of one rune type");
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
			clientThread.invokeLater(plugin::refreshPanel);
		});
		historyHead.add(historyFilterBox, BorderLayout.CENTER);
		content.add(historyHead);
		content.add(spacer(4));

		historyPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		historyPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		historyPanel.setAlignmentX(LEFT_ALIGNMENT);
		content.add(historyPanel);
		content.add(spacer(4));
		// all-time runes per type (today in grey)
		typesPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		typesPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		typesPanel.setAlignmentX(LEFT_ALIGNMENT);
		typesPanel.setToolTipText("All runes crafted per type (all time). In grey: today.");
		content.add(typesPanel);
		content.add(spacer(8));

		JLabel runesTitle = new JLabel("Runes crafted");
		runesTitle.setFont(FontManager.getRunescapeBoldFont());
		runesTitle.setForeground(Color.WHITE);
		runesTitle.setAlignmentX(LEFT_ALIGNMENT);
		content.add(runesTitle);

		JLabel zmiNote = new JLabel("<html><div style='width:170px'>Every rune type you craft appears here (also ZMI, Guardians of the Rift, sunfire and aether).</div></html>");
		zmiNote.setForeground(Color.GRAY);
		zmiNote.setFont(FontManager.getRunescapeSmallFont());
		zmiNote.setAlignmentX(LEFT_ALIGNMENT);
		content.add(zmiNote);
		content.add(spacer(4));

		runesPanel.setLayout(new BoxLayout(runesPanel, BoxLayout.Y_AXIS));
		runesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		runesPanel.setAlignmentX(LEFT_ALIGNMENT);
		content.add(runesPanel);
		content.add(spacer(10));

		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.add(button("Reset per hour", () -> clientThread.invokeLater(plugin::resetRates)));
		buttons.add(button("Reset rune count", () -> clientThread.invokeLater(plugin::resetRuneCount)));
		buttons.add(button("Reset bonus runes", () -> clientThread.invokeLater(plugin::resetBonusRunes)));
		buttons.add(button("Reset all", () -> clientThread.invokeLater(plugin::resetAll)));
		content.add(buttons);
		content.add(spacer(10));

		buddy.setAlignmentX(LEFT_ALIGNMENT);
		content.add(buddy);


		add(content, BorderLayout.NORTH);

		emptyLabel.setForeground(Color.GRAY);
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

	private void update(PanelData d)
	{
		if (d.paused)
		{
			timeLabel.setText("Paused - " + PanelData.formatTime(d.activeMillis));
			timeLabel.setForeground(Color.ORANGE);
			pauseButton.setText("Resume");
		}
		else
		{
			timeLabel.setText("Active time: " + PanelData.formatTime(d.activeMillis));
			timeLabel.setForeground(Color.LIGHT_GRAY);
			pauseButton.setText("Pause");
		}

		stats.begin();
		stats.cell("RC xp / hr", GpFormat.shortRate(d.xpPerHour), Color.WHITE);
		stats.cell("GP / hr", GpFormat.shortGp(d.gpPerHour), Color.YELLOW);
		if (d.essenceUsed > 0)
		{
			stats.cell("Profit / hr", GpFormat.shortGp(d.profitPerHour), Color.YELLOW);
		}
		stats.cell("All runes / hr", GpFormat.shortRate(d.allRunesPerHour), Color.WHITE);
		stats.cell("Crafts", fmt(d.crafts), Color.WHITE);
		stats.cell("Blocks imbued", fmt(d.blocksImbued), Color.WHITE);
		stats.cell("Bonus multiplier", GpFormat.multiplier(d.bonusPercent), BONUS);
		stats.cell("Bonus runes", GpFormat.shortRate(d.bonusRunes), BONUS);
		if (d.essenceCharges >= 0)
		{
			stats.cell("Blood essence", (d.essenceEstimate ? "~" : "") + fmt(d.essenceCharges),
				d.essenceCharges <= 100 ? Color.ORANGE : BONUS);
		}
		stats.end();

		tripsGrid.begin();
		tripsGrid.cell("Current trip", time(d.currentTripMillis), Color.CYAN);
		if (d.currentTripRunes > 0)
		{
			tripsGrid.cell("Runes this trip", fmt(d.currentTripRunes), Color.LIGHT_GRAY);
		}
		tripsGrid.cell("Trips", fmt(d.trips), Color.WHITE);
		tripsGrid.cell("Avg runes / trip", fmt(d.avgTripRunes), Color.WHITE);
		tripsGrid.cell("Avg trip time", time(d.avgTripMillis), Color.WHITE);
		tripsGrid.cell("Fastest trip", time(d.fastestTripMillis), BONUS);
		tripsGrid.cell("Last trip", time(d.lastTripMillis), Color.LIGHT_GRAY);
		tripsGrid.end();

		goalsGrid.begin();
		if (d.currentLevel >= d.goalLevel && d.currentLevel > 0)
		{
			goalsGrid.cell("Level " + d.goalLevel, "Reached!", BONUS);
		}
		else
		{
			goalsGrid.cell("Level " + d.goalLevel + " in", time(d.goalLevelMillis), Color.CYAN);
			goalsGrid.cell("XP left", GpFormat.shortGp(d.xpLeft), Color.LIGHT_GRAY);
		}
		if (d.goalRunes > 0)
		{
			if (d.runesLeft <= 0)
			{
				goalsGrid.cell(fmt(d.goalRunes) + " " + d.goalRuneName, "Reached!", BONUS);
			}
			else
			{
				goalsGrid.cell(fmt(d.goalRunes) + " " + d.goalRuneName + " in", time(d.goalRunesMillis), Color.CYAN);
				goalsGrid.cell("Runes left", fmt(d.runesLeft), Color.LIGHT_GRAY);
			}
		}
		goalsGrid.end();

		updateFilterChoices(d.historyTypes);
		// time is only kept per day, so it shows for "All runes" only (-1 = none)
		historyGrid.begin();
		historyGrid.cell("Today", GpFormat.shortRate(d.todayRunes) + " runes", Color.WHITE);
		historyGrid.cell("", d.todayMillis < 0 ? "" : PanelData.formatTime(d.todayMillis), Color.LIGHT_GRAY);
		historyGrid.cell("Best day", d.bestDate == null ? "-" : GpFormat.shortRate(d.bestRunes) + " runes", BLOOD);
		historyGrid.cell(PanelData.formatDate(d.bestDate), d.bestDate == null || d.bestMillis < 0 ? "" : PanelData.formatTime(d.bestMillis), Color.LIGHT_GRAY);
		historyGrid.cell("All time", GpFormat.shortRate(d.allRunes) + " runes", Color.WHITE);
		historyGrid.cell(d.days + (d.days == 1 ? " day" : " days"), d.allMillis < 0 ? "" : PanelData.formatTime(d.allMillis), Color.LIGHT_GRAY);
		historyGrid.end();
		// the list per rune type only with "All runes"
		typesPanel.setVisible(d.historyFilter == 0);

		typesGrid.begin();
		if (d.historyTypes.isEmpty())
		{
			typesGrid.cell("Per rune type", "-", Color.LIGHT_GRAY);
		}
		for (PanelData.TypeRow r : d.historyTypes)
		{
			String right = r.today > 0
				? "<html>" + GpFormat.shortRate(r.allTime) + " <span style='color:#999999'>+" + GpFormat.shortRate(r.today) + "</span></html>"
				: GpFormat.shortRate(r.allTime);
			typesGrid.cell(r.name, right, r.itemId == RuneIds.BLOOD_RUNE ? BLOOD : Color.WHITE);
		}
		typesGrid.end();

		updateRuneRows(d.rows);

		buddy.setVisible(d.showBuddy);
		if (d.showBuddy)
		{
			buddy.setAccountType(d.accountType);
			buddy.setMessage(hype.message(d, System.currentTimeMillis()));
		}

	}

	/** History filter chosen in the dropdown: 0 = all runes, else a rune item ID (any thread). */
	int getHistoryFilter()
	{
		return historyFilter;
	}

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

	/** Rune rows: same rune types = only new text; other types = build the rows again. */
	private void updateRuneRows(List<PanelData.Row> rows)
	{
		List<Integer> ids = new ArrayList<>();
		for (PanelData.Row r : rows)
		{
			ids.add(r.itemId);
		}
		if (!ids.equals(rowIds) || runesPanel.getComponentCount() == 0)
		{
			rowIds.clear();
			rowIds.addAll(ids);
			rowLabels.clear();
			runesPanel.removeAll();
			if (rows.isEmpty())
			{
				runesPanel.add(emptyLabel);
			}
			for (PanelData.Row r : rows)
			{
				runesPanel.add(runeRow(r));
				runesPanel.add(spacer(3));
			}
			runesPanel.revalidate();
			runesPanel.repaint();
		}
		for (int i = 0; i < rows.size(); i++)
		{
			PanelData.Row r = rows.get(i);
			rowLabels.get(i)[0].setText(r.name);
			rowLabels.get(i)[1].setText("<html><div style='text-align:right'>" + GpFormat.shortRate(r.count)
				+ "<br><span style='color:#aaaaaa'>" + GpFormat.shortRate(r.perHour) + " / hr</span></div></html>");
		}
	}

	private JPanel runeRow(PanelData.Row r)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		itemManager.getImage(r.itemId).addTo(icon);
		row.add(icon, BorderLayout.WEST);

		Color c = r.itemId == RuneIds.BLOOD_RUNE ? BLOOD : Color.WHITE;

		JLabel name = new JLabel();
		name.setForeground(c);
		row.add(name, BorderLayout.CENTER);

		JLabel right = new JLabel();
		rowLabels.add(new JLabel[]{name, right});
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		right.setForeground(c);
		row.add(right, BorderLayout.EAST);
		return row;
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

	/** Time text, or "-" when unknown. */
	private static String time(long millis)
	{
		return millis < 0 ? "-" : PanelData.formatTime(millis);
	}

	/** A titled box with a grid inside. */
	private static JPanel box(String title, JPanel grid)
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setOpaque(false);
		wrap.setAlignmentX(LEFT_ALIGNMENT);

		JLabel t = new JLabel(title);
		t.setFont(FontManager.getRunescapeBoldFont());
		t.setForeground(Color.WHITE);
		t.setAlignmentX(LEFT_ALIGNMENT);
		wrap.add(t);
		wrap.add(spacer(4));

		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		grid.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		grid.setAlignmentX(LEFT_ALIGNMENT);
		wrap.add(grid);
		return wrap;
	}

	private static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.addActionListener(e -> action.run());
		return b;
	}

	private static JPanel spacer(int height)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(1, height));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	private String fmt(long v)
	{
		return nf.format(v);
	}
}
