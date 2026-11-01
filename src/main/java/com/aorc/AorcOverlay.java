package com.aorc;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class AorcOverlay extends OverlayPanel
{
	private static final Color BLOOD = new Color(200, 40, 40);
	private static final Color BONUS = new Color(120, 200, 120);

	private final AorcPlugin plugin;
	private final AorcConfig config;
	private final List<Line> lines = new ArrayList<>();
	private int linesTick = Integer.MIN_VALUE;
	private boolean linesPaused;
	private final NumberFormat nf = NumberFormat.getIntegerInstance();

	@Inject
	AorcOverlay(AorcPlugin plugin, AorcConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset per hour", "AoRC", e -> plugin.resetRates());
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset blood runes", "AoRC", e -> plugin.resetRuneCount());
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset bonus runes", "AoRC", e -> plugin.resetBonusRunes());
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset all", "AoRC", e -> plugin.resetAll());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		RuneSession s = plugin.getSession();
		RuneTotals t = plugin.getTotals();
		long now = System.currentTimeMillis();
		long timeout = plugin.idleTimeoutMillis();

		boolean paused = plugin.isPaused();
		if ((!s.isStarted() && t.getRunes().isEmpty()) || (s.isIdle(now, timeout) && !paused))
		{
			return null;
		}

		// The numbers only change once per game tick: work the lines out once per tick,
		// and only draw them on the other frames.
		int tick = plugin.getTick();
		if (tick != linesTick || paused != linesPaused)
		{
			linesTick = tick;
			linesPaused = paused;
			lines.clear();
			buildLines(s, t, s.elapsedMillis(now, timeout));
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text(paused ? "AoRC (paused)" : "AoRC")
			.color(BLOOD)
			.build());
		for (Line l : lines)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(l.left)
				.right(l.right)
				.rightColor(l.color)
				.build());
		}
		return super.render(graphics);
	}

	private void buildLines(RuneSession s, RuneTotals t, long elapsed)
	{

		// Count = own counter (own reset). Per hour = rate session (Reset per hour).
		if (plugin.isZmiMode())
		{
			// ZMI: many rune types, the full list is in the side panel
			int all = 0;
			for (int v : t.getRunes().values())
			{
				all += v;
			}
			line("All runes", GpFormat.shortRate(all), Color.WHITE);
			line("All runes / hr", rate(s.getTotalRunes(), elapsed), Color.WHITE);
		}
		else
		{
			// Rune types you craft now (this session); blood first.
			// Blood lines only show when you craft blood runes.
			// Nothing crafted this session yet: the types you have a count for.
			Map<Integer, Integer> source = s.getRunesCrafted().isEmpty() ? t.getRunes() : s.getRunesCrafted();
			java.util.Set<Integer> types = new java.util.LinkedHashSet<>();
			if (source.getOrDefault(RuneIds.BLOOD_RUNE, 0) > 0)
			{
				types.add(RuneIds.BLOOD_RUNE);
			}
			for (Map.Entry<Integer, Integer> e : source.entrySet())
			{
				if (e.getValue() > 0)
				{
					types.add(e.getKey());
				}
			}
			// "All rune types" off: only blood when you craft blood, otherwise every type you craft
			boolean onlyBlood = !config.showAllRunes() && types.contains(RuneIds.BLOOD_RUNE);
			for (int id : types)
			{
				if (onlyBlood && id != RuneIds.BLOOD_RUNE)
				{
					continue;
				}
				boolean blood = id == RuneIds.BLOOD_RUNE;
				String name = blood ? "Blood" : RuneIds.RUNES.get(id);
				Color c = blood ? BLOOD : Color.WHITE;
				line(name + " runes", GpFormat.shortRate(t.getRunes(id)), c);
				line(name + " / hr", rate(s.getRunes(id), elapsed), c);
			}
			if (!onlyBlood && s.getRunesCrafted().size() > 1)
			{
				line("All runes / hr", rate(s.getTotalRunes(), elapsed), Color.WHITE);
			}
		}

		line("RC xp / hr", rate(s.getXpGained(), elapsed), Color.WHITE);

		// only at Zeah (blood / soul altar there, or the Dark Altar)
		if (s.getBlocksImbued() > 0 && plugin.isAtZeah())
		{
			line("Blocks imbued", fmt(s.getBlocksImbued()), Color.WHITE);
		}
		line("Crafts", fmt(s.getCrafts()), Color.WHITE);

		if (config.showProfit())
		{
			// worked out once per tick by the plugin (GE prices)
			PanelData d = plugin.getLastData();
			line("GP / hr", elapsed <= 0 ? "-" : GpFormat.shortGp(d.gpPerHour), Color.YELLOW);
			if (d.essenceUsed > 0)
			{
				// GP minus the blood essence used
				line("Profit / hr", elapsed <= 0 ? "-" : GpFormat.shortGp(d.profitPerHour), Color.YELLOW);
			}
		}

		if (config.showBonuses())
		{
			int total = BonusCalculator.totalPercent(plugin.currentBonuses());
			line("Bonus multiplier", GpFormat.multiplier(total), BONUS);
			line("Bonus runes", GpFormat.shortRate(t.getBonusRunes()), BONUS);
		}

		PanelData d = plugin.getLastData();
		if (d.essenceCharges >= 0)
		{
			Color c = d.essenceCharges <= 100 ? Color.ORANGE : BONUS;
			line("Blood essence", (d.essenceEstimate ? "~" : "") + fmt(d.essenceCharges), c);
		}

		if (config.goalInOverlay() && d.currentLevel > 0 && d.currentLevel < d.goalLevel)
		{
			line("Lvl " + d.goalLevel + " in", d.goalLevelMillis < 0 ? "-" : PanelData.formatTime(d.goalLevelMillis), Color.CYAN);
		}
	}

	private void line(String left, String right, Color color)
	{
		lines.add(new Line(left, right, color));
	}

	/** One overlay line: text left, value right. */
	private static final class Line
	{
		final String left;
		final String right;
		final Color color;

		Line(String left, String right, Color color)
		{
			this.left = left;
			this.right = right;
			this.color = color;
		}
	}

	/** Per hour value, or "-" when no time has passed yet. */
	private String rate(long amount, long elapsed)
	{
		if (elapsed <= 0)
		{
			return "-";
		}
		return GpFormat.shortRate(RuneSession.perHour(amount, elapsed));
	}

	private String fmt(long value)
	{
		return nf.format(value);
	}
}
