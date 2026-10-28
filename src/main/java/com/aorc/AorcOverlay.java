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
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset", "AoRC", e -> plugin.resetSession());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		RuneSession s = plugin.getSession();
		long now = System.currentTimeMillis();
		long timeout = plugin.idleTimeoutMillis();

		if (!s.isStarted() || s.isIdle(now, timeout))
		{
			return null;
		}

		long elapsed = s.elapsedMillis(now, timeout);

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("AoRC")
			.color(BLOOD)
			.build());

		// Blood runes always first
		int blood = s.getRunes(RuneIds.BLOOD_RUNE);
		if (blood > 0 || !config.showAllRunes())
		{
			line("Blood runes", fmt(blood), BLOOD);
			line("Blood / hr", fmt(RuneSession.perHour(blood, elapsed)), BLOOD);
		}

		if (config.showAllRunes())
		{
			for (Map.Entry<Integer, Integer> e : s.getRunesCrafted().entrySet())
			{
				if (e.getKey() == RuneIds.BLOOD_RUNE)
				{
					continue;
				}
				String name = RuneIds.RUNES.get(e.getKey());
				line(name + " runes", fmt(e.getValue()), Color.WHITE);
				line(name + " / hr", fmt(RuneSession.perHour(e.getValue(), elapsed)), Color.WHITE);
			}
			if (s.getRunesCrafted().size() > 1)
			{
				line("All runes / hr", fmt(RuneSession.perHour(s.getTotalRunes(), elapsed)), Color.WHITE);
			}
		}

		line("RC xp / hr", fmt(RuneSession.perHour(s.getXpGained(), elapsed)), Color.WHITE);

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
			line("GP / hr", fmt(RuneSession.perHour(gp, elapsed)), Color.YELLOW);
		}

		if (config.showBonuses())
		{
			List<BonusCalculator.Bonus> bonuses = plugin.currentBonuses();
			int total = BonusCalculator.totalPercent(bonuses);
			line("Bonus", "+" + total + "%", BONUS);
			for (BonusCalculator.Bonus b : bonuses)
			{
				line("  " + b.name, "+" + b.percent + "%", BONUS);
			}
			int crafted = s.getRunes(plugin.getLastCraftedRune());
			line("Bonus runes", fmt(BonusCalculator.estimatedBonusRunes(crafted, total)), BONUS);
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

	private String fmt(long value)
	{
		return nf.format(value);
	}
}
