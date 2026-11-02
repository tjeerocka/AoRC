package com.aorc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The way to the next step: a line on the ground from you to it (along a recorded path, so it
 * does not go through walls).
 * While "Record path" is on, your recorded trail is drawn in yellow.
 */
class PathOverlay extends Overlay
{
	private static final Color LINE = RouteOverlay.ROUTE;
	private static final Color TRAIL = new Color(255, 215, 0);

	private final Client client;
	private final AorcPlugin plugin;
	private final AorcConfig config;

	@Inject
	PathOverlay(Client client, AorcPlugin plugin, AorcConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Player me = client.getLocalPlayer();
		if (me == null || me.getWorldLocation() == null)
		{
			return null;
		}
		WorldPoint at = me.getWorldLocation();

		if (config.recordPath())
		{
			drawLine(g, plugin.recordedWalk(), TRAIL, null);
		}

		WorldPoint target = plugin.nextTargetPoint();
		if (target == null || target.getPlane() != at.getPlane())
		{
			return null;
		}
		if (config.routeLine())
		{
			List<int[]> pts = AorcPaths.line(at.getX(), at.getY(), at.getPlane(),
				target.getX(), target.getY(), target.getPlane());
			pts.add(0, new int[]{at.getX(), at.getY(), at.getPlane()});
			drawLine(g, pts, LINE, target);
		}
		return null;
	}

	/** A line through these tiles (centre of each tile), with a dot at the end. */
	private void drawLine(Graphics2D g, List<int[]> pts, Color c, WorldPoint end)
	{
		Path2D.Double path = new Path2D.Double();
		boolean started = false;
		net.runelite.api.Point last = null;
		for (int[] p : pts)
		{
			net.runelite.api.Point s = toCanvas(p[0], p[1], p[2]);
			if (s == null)
			{
				started = false;   // off screen: pick up again at the next point
				continue;
			}
			if (!started)
			{
				path.moveTo(s.getX(), s.getY());
				started = true;
			}
			else
			{
				path.lineTo(s.getX(), s.getY());
			}
			last = s;
		}
		g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0, 0, 0, 120));
		g.draw(path);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(c);
		g.draw(path);
		if (end != null && last != null)
		{
			g.fillOval(last.getX() - 4, last.getY() - 4, 8, 8);
		}
	}

	private net.runelite.api.Point toCanvas(int x, int y, int plane)
	{
		LocalPoint lp = LocalPoint.fromWorld(client, new WorldPoint(x, y, plane));
		return lp == null ? null : Perspective.localToCanvas(client, lp, plane);
	}
}
