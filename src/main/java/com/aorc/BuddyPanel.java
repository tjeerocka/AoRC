package com.aorc;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * "Runey", the cheerleader at the bottom of the side panel:
 * the rune you craft, with legs, arms and pom-poms, hopping around.
 * Pom-pom colours per account type, a partyhat for mains.
 */
class BuddyPanel extends JPanel
{
	private static final Color BUBBLE = new Color(235, 230, 215);
	private static final Color BUBBLE_TEXT = new Color(40, 30, 30);

	private final Sprite sprite = new Sprite();
	private final JLabel text = new JLabel();
	private final Timer timer;
	// a message that must stay for a while (e.g. "*ptooey*"), normal messages wait
	private long pinnedUntil;
	private final ItemManager itemManager;
	private int runeId = -1;

	/** Partyhats (red, yellow, blue, green, purple, white): mains get a random one each client start. */
	static final int[] PARTYHATS = {1038, 1040, 1042, 1044, 1046, 1048};

	BuddyPanel(ItemManager itemManager)
	{
		super(new BorderLayout(2, 0));
		this.itemManager = itemManager;
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

		add(sprite, BorderLayout.WEST);

		JPanel bubble = new JPanel(new BorderLayout())
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(BUBBLE);
				g2.fill(new RoundRectangle2D.Float(6, 0, getWidth() - 7, getHeight() - 1, 12, 12));
				// little tail pointing to Runey
				Path2D tail = new Path2D.Float();
				tail.moveTo(0, getHeight() * 0.62);
				tail.lineTo(8, getHeight() * 0.48);
				tail.lineTo(8, getHeight() * 0.76);
				tail.closePath();
				g2.fill(tail);
				g2.dispose();
			}
		};
		bubble.setOpaque(false);
		bubble.setBorder(BorderFactory.createEmptyBorder(6, 13, 6, 6));
		text.setForeground(BUBBLE_TEXT);
		bubble.add(text, BorderLayout.CENTER);
		add(bubble, BorderLayout.CENTER);

		setMessage("Hi! I'm Runey. Let's craft some runes!");

		// the real partyhat icon from the game, for mains
		AsyncBufferedImage hat = itemManager.getImage(PARTYHATS[new java.util.Random().nextInt(PARTYHATS.length)]);
		sprite.partyhat = hat;
		hat.onLoaded(sprite::repaint);

		// Animation: ~12 frames per second, only while the panel is on screen
		timer = new Timer(80, e ->
		{
			if (isShowing())
			{
				sprite.frame++;
				// mains: Runey spits on his own about every 8 seconds
				if (sprite.type == AccountType.MAIN && sprite.frame % 100 == 50)
				{
					sprite.spitStart = sprite.frame;
				}
				sprite.repaint();
			}
		});
		timer.start();
	}

	void setMessage(String message)
	{
		if (System.currentTimeMillis() < pinnedUntil)
		{
			return;
		}
		String html = "<html><div style='width:85px'>" + escape(message) + "</div></html>";
		if (!html.equals(text.getText()))
		{
			text.setText(html);
			sprite.cheerUntil = sprite.frame + 25; // jump and shake the pom-poms on a new message
			if (message.contains("*spits*"))
			{
				sprite.spitStart = sprite.frame;
			}
		}
	}

	/** Show a message for a fixed time (other messages wait). */
	void say(String message, long millis)
	{
		pinnedUntil = 0;
		setMessage(message);
		pinnedUntil = System.currentTimeMillis() + millis;
	}

	/**
	 * Spit to the left, out of the panel towards the game screen (the game overlay
	 * continues the drop from the right edge of the game view). Ironmen get an apology.
	 */
	void spitAcross(boolean iron)
	{
		say("*ptooey*", 1500);
		sprite.spitLeftStart = sprite.frame;
		if (iron)
		{
			Timer sorry = new Timer(1900, e -> say("Sooorrry boss, my bad!", 2800));
			sorry.setRepeats(false);
			sorry.start();
		}
	}

	void setAccountType(AccountType type)
	{
		if (type != null && type != sprite.type)
		{
			sprite.type = type;
			sprite.repaint();
		}
	}

	/** Show this rune as Runey (the rune you crafted last). */
	void setRune(int itemId)
	{
		if (itemId == runeId || itemId <= 0)
		{
			return;
		}
		runeId = itemId;
		AsyncBufferedImage img = itemManager.getImage(itemId);
		sprite.rune = img;
		img.onLoaded(sprite::repaint);
		sprite.repaint();
	}

	void stop()
	{
		timer.stop();
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Pom-pom colour per account type. */
	static Color pomColor(AccountType t)
	{
		switch (t)
		{
			case IRONMAN:
				return new Color(150, 150, 160);
			case ULTIMATE:
				return new Color(235, 235, 240);
			case HARDCORE:
				return new Color(200, 30, 30);
			case GROUP:
				return new Color(50, 110, 220);   // blue (ranked group)
			case UNRANKED_GROUP:
				return new Color(40, 170, 70);    // green
			case HARDCORE_GROUP:
				return new Color(50, 110, 220);   // blue
			default:
				return new Color(240, 190, 30); // main: gold
		}
	}

	/** Runey the cheerleader. */
	private static class Sprite extends JComponent
	{
		int frame;
		int cheerUntil;
		int spitStart = -100;
		int spitLeftStart = -100;
		BufferedImage rune;
		BufferedImage partyhat;
		AccountType type = AccountType.MAIN;

		Sprite()
		{
			setPreferredSize(new Dimension(76, 88));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			boolean cheering = frame < cheerUntil;
			// hop: small hops all the time, big jumps when cheering
			double hop = Math.abs(Math.sin(frame / (cheering ? 2.5 : 4.0))) * (cheering ? 8 : 3);

			float cx = 38;
			float stone = 40;                 // stone diameter on screen
			float r = stone / 2;
			float groundY = 82;
			float cy = (float) (groundY - 14 - r - hop);   // legs are 14 px

			// shadow (smaller when higher)
			g2.setColor(new Color(0, 0, 0, 70));
			float sw = (float) (30 - hop * 1.5);
			g2.fill(new Ellipse2D.Float(cx - sw / 2, groundY - 2, sw, 5));

			// legs and feet
			Color limb = new Color(185, 185, 190);
			g2.setColor(limb);
			g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			float hipY = cy + r - 3;
			float footY = (float) (groundY - 3 - hop * 0.2);
			float spread = (float) (hop > 2 ? 9 : 6);
			g2.drawLine((int) (cx - 6), (int) hipY, (int) (cx - spread), (int) footY);
			g2.drawLine((int) (cx + 6), (int) hipY, (int) (cx + spread), (int) footY);
			g2.setColor(new Color(110, 70, 50));
			g2.fill(new Ellipse2D.Float(cx - spread - 6, footY - 2, 8, 5));
			g2.fill(new Ellipse2D.Float(cx + spread - 2, footY - 2, 8, 5));

			// arms with pom-poms (behind the stone, hands outside)
			double wave = Math.sin(frame / (cheering ? 1.6 : 3.5));
			double leftAngle = Math.toRadians(cheering ? 215 + wave * 30 : 200 + wave * 10);
			double rightAngle = Math.toRadians(cheering ? -35 - wave * 30 : -20 + wave * 10);
			drawArm(g2, cx - r * 0.85f, cy + 3, leftAngle);
			drawArm(g2, cx + r * 0.85f, cy + 3, rightAngle);

			// the rune itself
			drawRune(g2, cx, cy, stone);

			// outfit: mains wear a partyhat (ironmen: no helm, just their pom-pom colours)
			if (type == AccountType.MAIN)
			{
				drawPartyHat(g2, cx, cy, r, partyhat);
			}

			// mouth: open when spitting, smile otherwise
			int spitT = frame - spitStart;
			float mouthY = cy + r * 0.55f;
			g2.setColor(new Color(30, 25, 25));
			if (spitT >= 0 && spitT < 6)
			{
				g2.fill(new Ellipse2D.Float(cx - 3, mouthY - 1, 6, 5));
			}
			else
			{
				g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.drawArc((int) (cx - 6), (int) (mouthY - 4), 12, 7, 200, 140);
			}

			// spit across: a big drop flies out to the left, out of the panel (towards the game)
			int leftT = frame - spitLeftStart;
			if (leftT >= 0 && leftT < 4)
			{
				g2.setColor(new Color(30, 25, 25));
				g2.fill(new Ellipse2D.Float(cx - 4, mouthY - 2, 8, 7));
			}
			if (leftT >= 1 && leftT < 7)
			{
				float t = (leftT - 1) / 5f;
				float sx = (cx - 6) + (-14 - (cx - 6)) * t;
				float sy = mouthY - 26 * (float) Math.sin(Math.PI * t * 0.8f);
				g2.setColor(new Color(175, 215, 245, 230));
				g2.fill(new Ellipse2D.Float(sx - 7, sy - 6, 14, 12));
				g2.setColor(new Color(175, 215, 245, 140));
				g2.fill(new Ellipse2D.Float(sx + 8, sy - 2, 6, 6));
			}

			// spit: a drop flies out in an arc and splats on the ground to the right
			if (spitT >= 2 && spitT < 16)
			{
				float t = (spitT - 2) / 13f;                 // 0..1 over the flight
				float startX = cx + 4;
				float startY = mouthY + 1;
				float endX = cx + 34;
				float endY = groundY - 2;
				float sx = startX + (endX - startX) * t;
				float sy = startY + (endY - startY) * t - 16 * (float) Math.sin(Math.PI * t);
				g2.setColor(new Color(175, 215, 245, 230));
				g2.fill(new Ellipse2D.Float(sx - 4, sy - 4, 8, 8));
				// little trail
				g2.setColor(new Color(175, 215, 245, 140));
				g2.fill(new Ellipse2D.Float(sx - 9, sy - 1, 4, 4));
				g2.fill(new Ellipse2D.Float(sx - 13, sy + 1, 3, 3));
			}
			else if (spitT >= 16 && spitT < 26)
			{
				// splat on the ground
				float fade = 1f - (spitT - 16) / 10f;
				g2.setColor(new Color(175, 215, 245, Math.round(200 * fade)));
				float x = cx + 34;
				float y = groundY - 1;
				g2.fill(new Ellipse2D.Float(x - 8, y - 2, 16, 4));
				g2.fill(new Ellipse2D.Float(x - 9, y - 4, 3, 3));
				g2.fill(new Ellipse2D.Float(x + 6, y - 5, 3, 3));
				g2.fill(new Ellipse2D.Float(x - 1, y - 6, 2, 2));
			}

			g2.dispose();
		}

		private void drawArm(Graphics2D g2, float sx, float sy, double angle)
		{
			float len = 13;
			float hx = (float) (sx + Math.cos(angle) * len);
			float hy = (float) (sy + Math.sin(angle) * len);
			g2.setColor(new Color(185, 185, 190));
			g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.drawLine((int) sx, (int) sy, (int) hx, (int) hy);

			// pom-pom: a fluffy ball of small circles
			Color pom = pomColor(type);
			Color light = pom.brighter();
			for (int i = 0; i < 7; i++)
			{
				double a = i * Math.PI * 2 / 7 + frame * 0.4;
				float px = (float) (hx + Math.cos(a) * 4);
				float py = (float) (hy + Math.sin(a) * 4);
				g2.setColor(i % 2 == 0 ? pom : light);
				g2.fill(new Ellipse2D.Float(px - 3.5f, py - 3.5f, 7, 7));
			}
			g2.setColor(light);
			g2.fill(new Ellipse2D.Float(hx - 3, hy - 3, 6, 6));
		}

		private void drawRune(Graphics2D g2, float cx, float cy, float stone)
		{
			Rectangle b = rune == null ? null : opaqueBounds(rune);
			if (b != null)
			{
				// scale so the stone part of the icon is 'stone' px; keep pixel art sharp
				float scale = stone / Math.max(b.width, b.height);
				int w = Math.round(rune.getWidth() * scale);
				int h = Math.round(rune.getHeight() * scale);
				float bx = (b.x + b.width / 2f) * scale;
				float by = (b.y + b.height / 2f) * scale;
				Graphics2D gi = (Graphics2D) g2.create();
				gi.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				gi.drawImage(rune, Math.round(cx - bx), Math.round(cy - by), w, h, null);
				gi.dispose();
			}
			else
			{
				// icon not loaded yet: plain grey rune stone
				g2.setColor(new Color(150, 150, 155));
				g2.fill(new Ellipse2D.Float(cx - stone / 2, cy - stone / 2, stone, stone));
				g2.setColor(new Color(70, 70, 75));
				g2.setStroke(new BasicStroke(2f));
				g2.draw(new Ellipse2D.Float(cx - stone / 2, cy - stone / 2, stone, stone));
			}
		}

		/** Mains wear a real partyhat (game icon), tilted a bit. */
		private static void drawPartyHat(Graphics2D g2, float cx, float cy, float r, BufferedImage hat)
		{
			drawItemHat(g2, cx + 2, cy - r * 0.62f, hat, 28f, -12);
		}

		/**
		 * Draw an item icon (helm / partyhat) with the bottom-centre of its visible pixels at (baseX, baseY).
		 * Returns false when the icon is not loaded yet.
		 */
		private static boolean drawItemHat(Graphics2D g2, float baseX, float baseY, BufferedImage img, float width, double tiltDeg)
		{
			Rectangle b = img == null ? null : opaqueBounds(img);
			if (b == null)
			{
				return false;
			}
			float scale = width / b.width;
			int w = Math.round(img.getWidth() * scale);
			int h = Math.round(img.getHeight() * scale);
			float ax = (b.x + b.width / 2f) * scale;
			float ay = (b.y + b.height) * scale;

			Graphics2D gh = (Graphics2D) g2.create();
			gh.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			if (tiltDeg != 0)
			{
				gh.rotate(Math.toRadians(tiltDeg), baseX, baseY);
			}
			gh.drawImage(img, Math.round(baseX - ax), Math.round(baseY - ay), w, h, null);
			gh.dispose();
			return true;
		}

		/** Bounds of the visible pixels (the stone inside the icon), or null if empty. */
		private static Rectangle opaqueBounds(BufferedImage img)
		{
			int minX = Integer.MAX_VALUE;
			int minY = Integer.MAX_VALUE;
			int maxX = -1;
			int maxY = -1;
			for (int y = 0; y < img.getHeight(); y++)
			{
				for (int x = 0; x < img.getWidth(); x++)
				{
					if ((img.getRGB(x, y) >>> 24) > 40)
					{
						minX = Math.min(minX, x);
						minY = Math.min(minY, y);
						maxX = Math.max(maxX, x);
						maxY = Math.max(maxY, y);
					}
				}
			}
			return maxX < 0 ? null : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
		}
	}
}
