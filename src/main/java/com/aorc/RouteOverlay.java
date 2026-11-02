package com.aorc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.GameObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * In the game world: the next thing to click on your way to the altar
 * (fairy ring, altar entrance, the altar itself). Learned from your own clicks.
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
		for (GameObject o : plugin.routeTargets())
		{
			Shape hull = o.getConvexHull();
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
		return null;
	}
}
