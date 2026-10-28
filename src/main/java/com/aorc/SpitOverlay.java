package com.aorc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Mains only: a small Runey peeks in from the right side of the game screen and spits
 * on your character. Only drawn on your own screen.
 */
class SpitOverlay extends Overlay
{
	static final long PEEK_MILLIS = 300;     // Runey slides in
	static final long FLY_MILLIS = 700;      // the drop flies
	static final long SPLAT_MILLIS = 1600;   // splat on the character, drips, fades
	static final long TOTAL = PEEK_MILLIS + FLY_MILLIS + SPLAT_MILLIS;

	private static final Color SPIT = new Color(175, 215, 245);

	private final Client client;
	private final ItemManager itemManager;
	private volatile long spitAt = -1;
	private volatile int runeId = RuneIds.BLOOD_RUNE;

	@Inject
	SpitOverlay(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/** Start a spit (any thread). Ignored while one is still running. */
	void spit(int runeItemId)
	{
		long now = System.currentTimeMillis();
		if (spitAt > 0 && now - spitAt < TOTAL)
		{
			return;
		}
		runeId = runeItemId;
		spitAt = now;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		long t = System.currentTimeMillis() - spitAt;
		if (spitAt < 0 || t < 0 || t > TOTAL)
		{
			return null;
		}
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		// a point on the character's chest
		Point target = player.getCanvasTextLocation(g, "", player.getLogicalHeight() / 2);
		if (target == null)
		{
			return null;
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Runey peeks in at the right edge of the game view
		int rightX = client.getViewportXOffset() + client.getViewportWidth();
		int headY = Math.max(client.getViewportYOffset() + 30, target.getY() - 90);
		float slide = Math.min(1f, t / (float) PEEK_MILLIS);
		long leaveStart = PEEK_MILLIS + FLY_MILLIS + 500;
		if (t > leaveStart)
		{
			slide = Math.max(0f, 1f - (t - leaveStart) / 400f);
		}
		int size = 34;
		int runeyX = Math.round(rightX - size * slide);
		drawRuney(g, runeyX, headY, size, t >= PEEK_MILLIS - 80 && t < PEEK_MILLIS + 200);

		int mouthX = runeyX + 6;
		int mouthY = headY + size / 2 + 6;

		if (t >= PEEK_MILLIS - 100 && t < PEEK_MILLIS + 500)
		{
			g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
			drawText(g, "*ptooey*", mouthX - 50, mouthY - 36);
		}

		if (t >= PEEK_MILLIS && t < PEEK_MILLIS + FLY_MILLIS)
		{
			// the drop flies in an arc from Runey's mouth to the character
			float p = (t - PEEK_MILLIS) / (float) FLY_MILLIS;
			float x = mouthX + (target.getX() - mouthX) * p;
			float y = mouthY + (target.getY() - mouthY) * p - 60 * (float) Math.sin(Math.PI * p);
			g.setColor(new Color(SPIT.getRed(), SPIT.getGreen(), SPIT.getBlue(), 120));
			g.fill(new Ellipse2D.Float(x + 8, y - 2, 6, 6));
			g.fill(new Ellipse2D.Float(x + 15, y - 1, 4, 4));
			g.setColor(SPIT);
			g.fill(new Ellipse2D.Float(x - 7, y - 7, 14, 14));
			g.setColor(Color.WHITE);
			g.fill(new Ellipse2D.Float(x - 3, y - 4, 3, 3));
		}
		else if (t >= PEEK_MILLIS + FLY_MILLIS)
		{
			// splat on the character, drips running down, fading out
			float p = (t - PEEK_MILLIS - FLY_MILLIS) / (float) SPLAT_MILLIS;
			int alpha = Math.round(230 * (1f - p));
			float tx = target.getX();
			float ty = target.getY();
			g.setColor(new Color(SPIT.getRed(), SPIT.getGreen(), SPIT.getBlue(), alpha));
			g.fill(new Ellipse2D.Float(tx - 11, ty - 8, 22, 16));
			g.fill(new Ellipse2D.Float(tx - 18, ty - 13, 6, 6));
			g.fill(new Ellipse2D.Float(tx + 13, ty - 12, 5, 5));
			g.fill(new Ellipse2D.Float(tx + 9, ty + 9, 4, 4));
			g.fill(new Ellipse2D.Float(tx - 15, ty + 7, 4, 4));
			// drips
			float drip = 22 * p;
			g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(Math.round(tx - 5), Math.round(ty + 6), Math.round(tx - 5), Math.round(ty + 6 + drip));
			g.drawLine(Math.round(tx + 4), Math.round(ty + 6), Math.round(tx + 4), Math.round(ty + 6 + drip * 0.6f));
			g.setColor(new Color(255, 255, 255, alpha));
			g.fill(new Ellipse2D.Float(tx - 6, ty - 5, 4, 3));
		}
		return null;
	}

	private void drawRuney(Graphics2D g, int x, int y, int size, boolean mouthOpen)
	{
		BufferedImage img = itemManager.getImage(runeId);
		if (img != null)
		{
			Graphics2D gi = (Graphics2D) g.create();
			gi.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			int h = Math.round(size * img.getHeight() / (float) img.getWidth());
			gi.drawImage(img, x, y, size, h, null);
			gi.dispose();
		}
		// mouth
		g.setColor(new Color(30, 25, 25));
		int mx = x + size / 2 - 3;
		int my = y + size / 2 + 6;
		if (mouthOpen)
		{
			g.fill(new Ellipse2D.Float(mx, my - 2, 7, 6));
		}
		else
		{
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawArc(mx - 2, my - 4, 10, 6, 200, 140);
		}
	}

	private static void drawText(Graphics2D g, String text, int x, int y)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(Color.WHITE);
		g.drawString(text, x, y);
	}
}
