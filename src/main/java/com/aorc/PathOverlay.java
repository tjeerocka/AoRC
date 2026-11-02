package com.aorc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
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
 * does not go through walls), and an arrow at the edge of the screen when it is off screen.
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
		if (config.routeEdgeArrow())
		{
			drawEdgeArrow(g, at, target);
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

	/** The target is off screen: an arrow at the edge of the game view, pointing to it. */
	private void drawEdgeArrow(Graphics2D g, WorldPoint at, WorldPoint target)
	{
		int vx = client.getViewportXOffset();
		int vy = client.getViewportYOffset();
		int vw = client.getViewportWidth();
		int vh = client.getViewportHeight();
		net.runelite.api.Point t = toCanvas(target.getX(), target.getY(), target.getPlane());
		if (t != null && t.getX() > vx && t.getX() < vx + vw && t.getY() > vy && t.getY() < vy + vh)
		{
			return;   // on screen: the outline shows it
		}
		// direction on screen: from you to a point a few tiles towards the target
		net.runelite.api.Point a = toCanvas(at.getX(), at.getY(), at.getPlane());
		double dx = target.getX() - at.getX();
		double dy = target.getY() - at.getY();
		double len = Math.hypot(dx, dy);
		if (a == null || len < 1)
		{
			return;
		}
		int step = (int) Math.min(5, len);
		net.runelite.api.Point b = toCanvas(at.getX() + (int) Math.round(dx / len * step),
			at.getY() + (int) Math.round(dy / len * step), at.getPlane());
		if (b == null)
		{
			return;
		}
		double ang = Math.atan2(b.getY() - a.getY(), b.getX() - a.getX());
		// from the centre of the view to its edge, in that direction (with a margin)
		double cx = vx + vw / 2.0;
		double cy = vy + vh / 2.0;
		double rx = vw / 2.0 - 30;
		double ry = vh / 2.0 - 30;
		double cos = Math.cos(ang);
		double sin = Math.sin(ang);
		double k = Math.min(Math.abs(cos) < 1e-6 ? Double.MAX_VALUE : rx / Math.abs(cos),
			Math.abs(sin) < 1e-6 ? Double.MAX_VALUE : ry / Math.abs(sin));
		double px = cx + cos * k;
		double py = cy + sin * k;
		Polygon arrow = new Polygon();
		arrow.addPoint((int) (px + cos * 16), (int) (py + sin * 16));
		arrow.addPoint((int) (px - cos * 10 - sin * 11), (int) (py - sin * 10 + cos * 11));
		arrow.addPoint((int) (px - cos * 4), (int) (py - sin * 4));
		arrow.addPoint((int) (px - cos * 10 + sin * 11), (int) (py - sin * 10 - cos * 11));
		int alpha = 170 + (int) (70 * Math.abs(Math.sin(System.currentTimeMillis() / 400.0)));
		g.setColor(new Color(0, 0, 0, 140));
		g.setStroke(new BasicStroke(3f));
		g.drawPolygon(arrow);
		g.setColor(new Color(LINE.getRed(), LINE.getGreen(), LINE.getBlue(), alpha));
		g.fillPolygon(arrow);
	}
}
