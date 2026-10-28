package com.aorc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
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
 * "Runey", the cheerleader at the bottom of the side panel (pre-rendered 3D frames).
 * Headband colour per account type, a partyhat for mains.
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

	/** Partyhats (red, yellow, blue, green, purple, white): mains get a random one each client start. */
	static final int[] PARTYHATS = {1038, 1040, 1042, 1044, 1046, 1048};

	BuddyPanel(ItemManager itemManager)
	{
		super(new BorderLayout(2, 0));
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
		String html = "<html><div style='width:72px'>" + escape(message) + "</div></html>";
		if (!html.equals(text.getText()))
		{
			text.setText(html);
			sprite.cheerUntil = sprite.frame + 38; // cheer for 3 s on a new message
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

	void stop()
	{
		timer.stop();
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Runey the cheerleader: pre-rendered frames, hopping, cheering and spitting. */
	private static class Sprite extends JComponent
	{
		private static final Color SPIT = new Color(175, 215, 245);
		private static final int W = 86;
		private static final int SHIFT = -5;   // the frame is 96 wide, Runey fits in the middle 86
		private static final int H = 100;
		private static final int GROUND = 95;

		int frame;
		int cheerUntil;
		int spitStart = -100;
		int spitLeftStart = -100;
		BufferedImage partyhat;
		AccountType type = AccountType.MAIN;

		Sprite()
		{
			setPreferredSize(new Dimension(W, H));
		}

		// idle cycle (12.5 frames per second): 10 s facing you, then 10 s turned and slowly looking around
		private static final int FACE_TICKS = 125;
		private static final int TURN_TICKS = 18;
		private static final int LOOK_TICKS = 125;
		private static final double LOOK_YAW = -22;

		/** Idle yaw in degrees: 0 = facing you, negative = turned to the side. */
		static double idleYaw(int frame)
		{
			int t = Math.floorMod(frame, FACE_TICKS + LOOK_TICKS);
			if (t < FACE_TICKS)
			{
				return 0;
			}
			t -= FACE_TICKS;
			if (t < TURN_TICKS)
			{
				return LOOK_YAW * ease(t / (double) TURN_TICKS);           // turn away
			}
			if (t >= LOOK_TICKS - TURN_TICKS)
			{
				return LOOK_YAW * ease((LOOK_TICKS - t) / (double) TURN_TICKS);   // turn back to you
			}
			// look around slowly: one slow swing left and right
			double p = (t - TURN_TICKS) / (double) (LOOK_TICKS - 2 * TURN_TICKS);
			return LOOK_YAW + 12 * Math.sin(2 * Math.PI * p);
		}

		private static double ease(double x)
		{
			return x * x * (3 - 2 * x);
		}

		/** Spit pose: lean back, snap forward, hold, back up (index 0-3), or -1 when done. */
		private static int spitPose(int t)
		{
			if (t < 0 || t >= 15)
			{
				return -1;
			}
			return t < 4 ? 0 : t < 7 ? 1 : t < 11 ? 2 : 3;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

			boolean cheering = frame < cheerUntil;
			int leftT = frame - spitLeftStart;
			int spitT = frame - spitStart;
			int idx;
			double hop;
			if (spitPose(leftT) >= 0)
			{
				idx = RuneyFrames.SPIT_LEFT + spitPose(leftT);
				hop = 0;
			}
			else if (spitPose(spitT) >= 0)
			{
				idx = RuneyFrames.SPIT_RIGHT + spitPose(spitT);
				hop = 0;
			}
			else if (cheering)
			{
				idx = RuneyFrames.CHEER + (frame / 2) % 8;
				hop = Math.abs(Math.sin(frame / 4.0)) * 7;
			}
			else
			{
				idx = RuneyFrames.turnFrame(idleYaw(frame));
				hop = Math.abs(Math.sin(frame / 9.0)) * 1.5;
			}

			// shadow (smaller when higher)
			float cx = W / 2f;
			float sw = (float) (52 - hop * 2.5);
			g2.setColor(new Color(0, 0, 0, 80));
			g2.fill(new Ellipse2D.Float(cx - sw / 2, GROUND - 3, sw, 6));

			int top = (int) Math.round(GROUND - 91 - hop);   // feet are at y=91 in the frame
			BufferedImage[] frames = RuneyFrames.get(type);
			if (frames != null)
			{
				g2.drawImage(frames[idx], SHIFT, top, null);
			}
			else
			{
				g2.setColor(new Color(150, 150, 155));
				g2.fill(new Ellipse2D.Float(cx - 22, top + 30, 44, 44));
			}

			// mains wear a real partyhat on top of the headband
			if (type == AccountType.MAIN)
			{
				Point h = RuneyFrames.head(idx);
				drawItemHat(g2, h.x + SHIFT + 2, top + h.y + 5, partyhat, 30f, -10);
			}

			Point nose = RuneyFrames.nose(idx);
			float nx = nose.x + SHIFT;
			float ny = top + nose.y + 6;

			// spit across: a big drop flies out to the left, out of the panel (towards the game)
			if (leftT >= 5 && leftT < 13)
			{
				float t = (leftT - 5) / 7f;
				float sx = nx + (-14 - nx) * t;
				float sy = ny - 26 * (float) Math.sin(Math.PI * t * 0.8f);
				g2.setColor(new Color(SPIT.getRed(), SPIT.getGreen(), SPIT.getBlue(), 230));
				g2.fill(new Ellipse2D.Float(sx - 7, sy - 6, 14, 12));
				g2.setColor(new Color(SPIT.getRed(), SPIT.getGreen(), SPIT.getBlue(), 140));
				g2.fill(new Ellipse2D.Float(sx + 8, sy - 2, 6, 6));
			}

			// spit: a drop flies out in an arc and splats on the ground to the right
			float endX = W - 6;
			if (spitT >= 5 && spitT < 21)
			{
				float t = (spitT - 5) / 15f;
				float sx = nx + (endX - nx) * t;
				float sy = ny + (GROUND - 2 - ny) * t - 16 * (float) Math.sin(Math.PI * t);
				g2.setColor(new Color(SPIT.getRed(), SPIT.getGreen(), SPIT.getBlue(), 230));
				g2.fill(new Ellipse2D.Float(sx - 4, sy - 4, 8, 8));
				g2.setColor(new Color(SPIT.getRed(), SPIT.getGreen(), SPIT.getBlue(), 140));
				g2.fill(new Ellipse2D.Float(sx - 9, sy - 1, 4, 4));
				g2.fill(new Ellipse2D.Float(sx - 13, sy + 1, 3, 3));
			}
			else if (spitT >= 21 && spitT < 33)
			{
				float fade = 1f - (spitT - 21) / 12f;
				g2.setColor(new Color(SPIT.getRed(), SPIT.getGreen(), SPIT.getBlue(), Math.round(200 * fade)));
				float y = GROUND - 1;
				g2.fill(new Ellipse2D.Float(endX - 8, y - 2, 16, 4));
				g2.fill(new Ellipse2D.Float(endX - 9, y - 4, 3, 3));
				g2.fill(new Ellipse2D.Float(endX + 5, y - 5, 3, 3));
				g2.fill(new Ellipse2D.Float(endX - 1, y - 6, 2, 2));
			}

			g2.dispose();
		}

		/**
		 * Draw an item icon (partyhat) with the bottom-centre of its visible pixels at (baseX, baseY).
		 */
		private static void drawItemHat(Graphics2D g2, float baseX, float baseY, BufferedImage img, float width, double tiltDeg)
		{
			Rectangle b = img == null ? null : opaqueBounds(img);
			if (b == null)
			{
				return;
			}
			float scale = width / b.width;
			int w = Math.round(img.getWidth() * scale);
			int h = Math.round(img.getHeight() * scale);
			float ax = (b.x + b.width / 2f) * scale;
			float ay = (b.y + b.height) * scale;

			Graphics2D gh = (Graphics2D) g2.create();
			gh.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			gh.rotate(Math.toRadians(tiltDeg), baseX, baseY);
			gh.drawImage(img, Math.round(baseX - ax), Math.round(baseY - ay), w, h, null);
			gh.dispose();
		}

		/** Bounds of the visible pixels, or null if empty. */
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
