package com.aorc;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/** "Runey", the little hype buddy at the bottom of the side panel: the rune you craft, with a face. */
class BuddyPanel extends JPanel
{
	private static final Color BUBBLE = new Color(235, 230, 215);
	private static final Color BUBBLE_TEXT = new Color(40, 30, 30);

	private final Sprite sprite = new Sprite();
	private final JLabel text = new JLabel();
	private final Timer timer;

	private final ItemManager itemManager;
	private int runeId = -1;

	BuddyPanel(ItemManager itemManager)
	{
		super(new BorderLayout(4, 0));
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

		// Animation: ~12 frames per second, only while the panel is on screen
		timer = new Timer(80, e ->
		{
			if (isShowing())
			{
				sprite.frame++;
				sprite.repaint();
			}
		});
		timer.start();
	}

	void setMessage(String message)
	{
		String html = "<html><div style='width:100px'>" + escape(message) + "</div></html>";
		if (!html.equals(text.getText()))
		{
			text.setText(html);
			sprite.waveUntil = sprite.frame + 20; // wave when he says something new
		}
	}

	/** Show this rune as the buddy (the rune you crafted last). */
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

	/** Runey: the rune you craft (real game icon, bigger) with a smile and arms. */
	private static class Sprite extends JComponent
	{
		int frame;
		int waveUntil;
		BufferedImage rune;

		Sprite()
		{
			setPreferredSize(new Dimension(64, 70));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			double bob = Math.sin(frame / 5.0) * 2.5;
			float cx = 32;
			float top = (float) (6 + bob);
			float size = 50;           // stone size on screen
			float cy = top + size / 2;

			// shadow
			g2.setColor(new Color(0, 0, 0, 70));
			float shadowW = (float) (30 - bob * 1.5);
			g2.fill(new Ellipse2D.Float(cx - shadowW / 2, 61, shadowW, 5));

			// the rune itself
			if (rune != null && rune.getWidth() > 1)
			{
				// game icons are pixel art: keep them sharp when scaling
				Graphics2D gi = (Graphics2D) g2.create();
				gi.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				float scale = size / 30f; // the stone is about 30 px inside the 36x32 icon
				int w = Math.round(rune.getWidth() * scale);
				int h = Math.round(rune.getHeight() * scale);
				gi.drawImage(rune, Math.round(cx - w / 2f), Math.round(cy - h / 2f), w, h, null);
				gi.dispose();
			}
			else
			{
				// no icon yet: plain grey rune stone
				g2.setColor(new Color(150, 150, 155));
				g2.fill(new Ellipse2D.Float(cx - size / 2, top, size, size));
				g2.setColor(new Color(80, 80, 85));
				g2.setStroke(new BasicStroke(2f));
				g2.draw(new Ellipse2D.Float(cx - size / 2, top, size, size));
			}

			// arms (from the side of the stone); the right one waves on a new message
			Color arm = new Color(175, 175, 180);
			g2.setColor(arm);
			g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.drawLine((int) (cx - 23), (int) (cy + 6), (int) (cx - 30), (int) (cy + 15));
			boolean waving = frame < waveUntil;
			double angle = waving ? Math.sin(frame / 1.5) * 0.6 - 1.0 : 0.8;
			g2.drawLine((int) (cx + 23), (int) (cy + 4),
				(int) (cx + 23 + Math.cos(angle) * 9), (int) (cy + 4 + Math.sin(angle) * 9));

			// smile in the bottom part of the stone
			g2.setColor(new Color(30, 30, 30));
			g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.drawArc((int) (cx - 6), (int) (top + 36), 12, 7, 200, 140);

			g2.dispose();
		}
	}
}
