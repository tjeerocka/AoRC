package com.aorc;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.game.ItemManager;
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
	private final ItemManager itemManager;
	private final NumberFormat nf = NumberFormat.getIntegerInstance();

	@Inject
	AorcOverlay(AorcPlugin plugin, AorcConfig config, ItemManager itemManager)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
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

		if ((!s.isStarted() && t.getRunes().isEmpty()) || s.isIdle(now, timeout))
		{
			return null;
		}

		long elapsed = s.elapsedMillis(now, timeout);

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("AoRC")
			.color(BLOOD)
			.build());

		// Blood runes always first
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
			line("Blood runes", GpFormat.shortRate(t.getRunes(RuneIds.BLOOD_RUNE)), BLOOD);
			line("Blood / hr", rate(s.getRunes(RuneIds.BLOOD_RUNE), elapsed), BLOOD);
		}

		if (config.showAllRunes() && !plugin.isZmiMode())
		{
			java.util.Set<Integer> types = new java.util.LinkedHashSet<>(t.getRunes().keySet());
			types.addAll(s.getRunesCrafted().keySet());
			for (int id : types)
			{
				if (id == RuneIds.BLOOD_RUNE)
				{
					continue;
				}
				String name = RuneIds.RUNES.get(id);
				line(name + " runes", GpFormat.shortRate(t.getRunes(id)), Color.WHITE);
				line(name + " / hr", rate(s.getRunes(id), elapsed), Color.WHITE);
			}
			if (s.getRunesCrafted().size() > 1)
			{
				line("All runes / hr", rate(s.getTotalRunes(), elapsed), Color.WHITE);
			}
		}

		line("RC xp / hr", rate(s.getXpGained(), elapsed), Color.WHITE);

		if (s.getBlocksImbued() > 0)
		{
			line("Blocks imbued", fmt(s.getBlocksImbued()), Color.WHITE);
		}
		line("Crafts", fmt(s.getCrafts()), Color.WHITE);

		if (config.showProfit())
		{
			long gp = 0;
			for (Map.Entry<Integer, Integer> e : s.getRunesCrafted().entrySet())
			{
				gp += (long) itemManager.getItemPrice(e.getKey()) * e.getValue();
			}
			line("GP / hr", elapsed <= 0 ? "-" : GpFormat.shortGp(RuneSession.perHour(gp, elapsed)), Color.YELLOW);
			PanelData d = plugin.getLastData();
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

		return super.render(graphics);
	}

	private void line(String left, String right, Color color)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.rightColor(color)
			.build());
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
