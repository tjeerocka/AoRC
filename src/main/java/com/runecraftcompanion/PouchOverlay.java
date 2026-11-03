package com.runecraftcompanion;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Inventory: a box around an essence pouch: cyan = empty it now (at the altar), orange = needs a repair. */
class PouchOverlay extends WidgetItemOverlay
{

	/** Box colour around the pouch and Astral Contact (always orange). */
	static final Color REPAIR = new Color(255, 140, 0);

	private final RunecraftCompanionPlugin plugin;

	@Inject
	PouchOverlay(RunecraftCompanionPlugin plugin)
	{
		this.plugin = plugin;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem item)
	{
		// at the altar: the pouch to empty next (cyan) beats the repair box (orange)
		boolean empty = plugin.pouchToEmpty(itemId);
		if (!empty && !plugin.pouchNeedsRepair(itemId))
		{
			return;
		}
		Rectangle b = item.getCanvasBounds();
		if (b == null)
		{
			return;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// pulse a little so it catches the eye
		int a = 120 + (int) (80 * Math.abs(Math.sin(System.currentTimeMillis() / 400.0)));
		Color c = empty ? RouteOverlay.ROUTE : PouchOverlay.REPAIR;
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
		g.fillRoundRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2, 6, 6);
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
		g.setStroke(new BasicStroke(2f));
		g.drawRoundRect(b.x - 1, b.y - 1, b.width + 2, b.height + 2, 6, 6);
	}
}
