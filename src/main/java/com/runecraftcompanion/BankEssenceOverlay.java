package com.runecraftcompanion;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Bank: a box around the essence you used last, while you have no essence on you. */
class BankEssenceOverlay extends WidgetItemOverlay
{
	private final RunecraftCompanionPlugin plugin;

	@Inject
	BankEssenceOverlay(RunecraftCompanionPlugin plugin)
	{
		this.plugin = plugin;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem item)
	{
		if (!plugin.isBankEssence(itemId))
		{
			return;
		}
		Rectangle b = item.getCanvasBounds();
		if (b == null)
		{
			return;
		}
		Color c = RouteOverlay.ROUTE;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int a = 140 + (int) (70 * Math.abs(Math.sin(System.currentTimeMillis() / 400.0)));
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 55));
		g.fillRoundRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2, 6, 6);
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
		g.setStroke(new BasicStroke(2f));
		g.drawRoundRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2, 6, 6);
	}
}
