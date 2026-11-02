package com.aorc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * In the game world, the next thing to click: on your way to the altar the fairy ring, the next
 * cave and the altar (built-in routes), at Zeah the next object of the cycle; back at the bank
 * the bank chests, booths and bankers. With Route debug: a label above each route object.
 */
class RouteOverlay extends Overlay
{
	static final Color ROUTE = new Color(0, 220, 255);   // cyan, like the altar teleport

	private final AorcPlugin plugin;

	@Inject
	RouteOverlay(AorcPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		java.util.List<Shape> shapes = new java.util.ArrayList<>(plugin.bankTargets());
		for (TileObject o : plugin.routeTargets())
		{
			shapes.add(AorcPlugin.hull(o));
		}
		for (Shape hull : shapes)
		{
			if (hull == null)
			{
				continue;
			}
			int a = 140 + (int) (70 * Math.abs(Math.sin(System.currentTimeMillis() / 400.0)));
			g.setColor(new Color(ROUTE.getRed(), ROUTE.getGreen(), ROUTE.getBlue(), 45));
			g.fill(hull);
			g.setColor(new Color(ROUTE.getRed(), ROUTE.getGreen(), ROUTE.getBlue(), a));
			g.setStroke(new BasicStroke(2f));
			g.draw(hull);
		}
		if (plugin.routeDebug())
		{
			// route debug: step number, object ID and passed / next above every route object
			for (java.util.Map.Entry<TileObject, String> e : plugin.routeDebugLabels().entrySet())
			{
				net.runelite.api.Point p = e.getKey().getCanvasTextLocation(g, e.getValue(), 150);
				if (p != null)
				{
					net.runelite.client.ui.overlay.OverlayUtil.renderTextLocation(g, p, e.getValue(), Color.YELLOW);
				}
			}
		}
		return null;
	}
}
