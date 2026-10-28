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
import net.runelite.client.ui.FontManager;
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
	static final long SPLAT_MILLIS = 3200;   // HUGE splat on the character, drips, fades
	static final long TOTAL = PEEK_MILLIS + FLY_MILLIS + SPLAT_MILLIS;

	private static final Color SPIT = new Color(175, 215, 245);

	private final Client client;
	private final ItemManager itemManager;
	private volatile long spitAt = -1;
	private volatile int runeId = RuneIds.BLOOD_RUNE;
	// true: the drop comes from Runey in the side panel (enters at the right edge of the game view)
	private volatile boolean fromPanel;

	@Inject
	SpitOverlay(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/** Start a spit (any thread). Ignored while one is still running. */
	void spit(int runeItemId, boolean fromSidePanel)
	{
		long now = System.currentTimeMillis();
		if (spitAt > 0 && now - spitAt < TOTAL)
		{
			return;
		}
		runeId = runeItemId;
		fromPanel = fromSidePanel;
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
		boolean iron = isIron();
		float slide = Math.min(1f, t / (float) PEEK_MILLIS);
		// on an ironman Runey stays to apologise, then leaves at the end
		long leaveStart = iron ? TOTAL - 400 : PEEK_MILLIS + FLY_MILLIS + 500;
		if (t > leaveStart)
		{
			slide = Math.max(0f, 1f - (t - leaveStart) / 400f);
		}
		int size = 34;
		int runeyX = Math.round(rightX - size * slide);
		boolean panel = fromPanel;
		int mouthX;
		int mouthY;
		if (panel)
		{
			// the drop comes in from the side panel: start just outside the right edge, low (Runey sits at the bottom)
			mouthX = rightX + 10;
			mouthY = client.getViewportYOffset() + Math.round(client.getViewportHeight() * 0.78f);
		}
		else
		{
			drawRuney(g, runeyX, headY, size, t >= PEEK_MILLIS - 80 && t < PEEK_MILLIS + 200);
			mouthX = runeyX + 6;
			mouthY = headY + size / 2 + 6;
		}

		if (!panel && t >= PEEK_MILLIS - 100 && t < PEEK_MILLIS + 500)
		{
			g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
			drawText(g, "*ptooey*", mouthX - 50, mouthY - 36);
		}

		// ironman complained: Runey says sorry (right-aligned next to Runey)
		long sorryStart = PEEK_MILLIS + FLY_MILLIS + 900;
		if (!panel && iron && t >= sorryStart && t < TOTAL - 300)
		{
			String sorry = "Sooorrry boss, my bad!";
			g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
			int w = g.getFontMetrics().stringWidth(sorry);
			drawText(g, sorry, runeyX - w - 6, headY + size / 2 + 5);
		}

		if (t >= PEEK_MILLIS && t < PEEK_MILLIS + FLY_MILLIS)
		{
			// the drop flies in an arc from Runey's mouth to the character
			float p = (t - PEEK_MILLIS) / (float) FLY_MILLIS;
			float x = mouthX + (target.getX() - mouthX) * p;
			float y = mouthY + (target.getY() - mouthY) * p - 60 * (float) Math.sin(Math.PI * p);
			g.setColor(new Color(SPIT.getRed(), SPIT.getGreen(), SPIT.getBlue(), 130));
			g.fill(new Ellipse2D.Float(x + 16, y - 6, 14, 14));
			g.fill(new Ellipse2D.Float(x + 32, y - 3, 9, 9));
			g.fill(new Ellipse2D.Float(x + 44, y - 1, 6, 6));
			g.setColor(SPIT);
			g.fill(new Ellipse2D.Float(x - 18, y - 16, 36, 32));
			g.setColor(Color.WHITE);
			g.fill(new Ellipse2D.Float(x - 9, y - 10, 9, 7));
		}
		else if (t >= PEEK_MILLIS + FLY_MILLIS)
		{
			// HUGE splat on the character: big blob, flying droplets, streaks, long drips
			float p = (t - PEEK_MILLIS - FLY_MILLIS) / (float) SPLAT_MILLIS;
			float fadeP = Math.max(0f, (p - 0.55f) / 0.45f);            // stays full for a while, then fades
			int alpha = Math.round(235 * (1f - fadeP));
			float burst = Math.min(1f, p * 6f);                          // splash flies out fast
			float tx = target.getX();
			float ty = target.getY();
			Color spit = new Color(SPIT.getRed(), SPIT.getGreen(), SPIT.getBlue(), alpha);
			g.setColor(spit);

			// main blob
			g.fill(new Ellipse2D.Float(tx - 38, ty - 28, 76, 56));
			g.fill(new Ellipse2D.Float(tx - 52, ty - 12, 34, 30));
			g.fill(new Ellipse2D.Float(tx + 20, ty - 18, 36, 32));
			g.fill(new Ellipse2D.Float(tx - 18, ty - 46, 40, 30));

			// droplets flying out in all directions
			for (int i = 0; i < 14; i++)
			{
				double ang = i * Math.PI * 2 / 14 + (i % 3) * 0.2;
				float dist = (55 + (i * 17) % 35) * burst;
				float drop = 6 + (i * 7) % 9;
				float dx = tx + (float) Math.cos(ang) * dist;
				float dy = ty + (float) Math.sin(ang) * dist + 30 * p * p;   // they fall a bit
				g.fill(new Ellipse2D.Float(dx - drop / 2, dy - drop / 2, drop, drop));
			}

			// splash streaks
			g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			for (int i = 0; i < 8; i++)
			{
				double ang = i * Math.PI / 4 + 0.35;
				float r1 = 34 * burst;
				float r2 = 62 * burst;
				g.drawLine(Math.round(tx + (float) Math.cos(ang) * r1), Math.round(ty + (float) Math.sin(ang) * r1),
					Math.round(tx + (float) Math.cos(ang) * r2), Math.round(ty + (float) Math.sin(ang) * r2));
			}

			// long drips running down
			float drip = 70 * Math.min(1f, p * 1.4f);
			g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(Math.round(tx - 22), Math.round(ty + 20), Math.round(tx - 22), Math.round(ty + 20 + drip));
			g.drawLine(Math.round(tx - 4), Math.round(ty + 24), Math.round(tx - 4), Math.round(ty + 24 + drip * 0.7f));
			g.drawLine(Math.round(tx + 14), Math.round(ty + 22), Math.round(tx + 14), Math.round(ty + 22 + drip * 1.15f));
			g.drawLine(Math.round(tx + 30), Math.round(ty + 10), Math.round(tx + 30), Math.round(ty + 10 + drip * 0.5f));
			g.fill(new Ellipse2D.Float(tx - 27, ty + 16 + drip, 10, 12));
			g.fill(new Ellipse2D.Float(tx + 9, ty + 18 + drip * 1.15f, 10, 12));

			// shine
			g.setColor(new Color(255, 255, 255, alpha));
			g.fill(new Ellipse2D.Float(tx - 22, ty - 18, 16, 10));
			g.fill(new Ellipse2D.Float(tx + 26, ty - 10, 8, 6));

			// the character complains (OSRS-style yellow overhead text)
			if (p < 0.92f)
			{
				String line = iron
					? "WTF Runey?! I'm an ironman, you dum fåk!"
					: "YUCK! I should have created an ironman instead!";
				Point head = player.getCanvasTextLocation(g, line, player.getLogicalHeight() + 40);
				if (head != null)
				{
					g.setFont(FontManager.getRunescapeBoldFont());
					g.setColor(Color.BLACK);
					g.drawString(line, head.getX() + 1, head.getY() + 1);
					g.setColor(Color.YELLOW);
					g.drawString(line, head.getX(), head.getY());
				}
			}
		}
		return null;
	}

	private boolean isIron()
	{
		return AccountType.fromVarbit(client.getVarbitValue(AccountType.VARBIT)) != AccountType.MAIN;
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
