package com.aorc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Inventory and worn equipment: a box around the teleport to use next
 * (bank teleport after your last craft, altar teleport when you are full of essence).
 */
class TeleportOverlay extends WidgetItemOverlay
{
	static final Color BANK = RouteOverlay.ROUTE;         // cyan, like the rest of the route
	static final Color ALTAR = RouteOverlay.ROUTE;        // cyan: teleport to the altar

	private final AorcPlugin plugin;

	@Inject
	TeleportOverlay(AorcPlugin plugin)
	{
		this.plugin = plugin;
		showOnInventory();
		showOnEquipment();
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem item)
	{
		Color c = plugin.teleportHighlight(itemId, item.getWidget() == null ? -1 : item.getWidget().getIndex());
		Rectangle b = c == null ? null : item.getCanvasBounds();
		if (b == null)
		{
			return;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int a = 120 + (int) (80 * Math.abs(Math.sin(System.currentTimeMillis() / 400.0)));
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 55));
		g.fillRoundRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2, 6, 6);
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
		g.setStroke(new BasicStroke(2f));
		g.drawRoundRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2, 6, 6);
	}
}
