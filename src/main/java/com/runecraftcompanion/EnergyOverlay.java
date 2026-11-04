package com.runecraftcompanion;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ImageUtil;

/**
 * Run energy warning (while you runecraft): at 30% the boot on the run orb flashes yellow, at 20% red with
 * "Restore run energy before you go on" next to it, at 10% also a big red "RESTORE STAMINA ASAP!"
 * above your character.
 */
class EnergyOverlay extends Overlay
{
	private static final Color LOW = new Color(255, 215, 0);
	private static final Color VERY_LOW = new Color(255, 48, 48);

	private final Client client;
	private final RunecraftCompanionPlugin plugin;
	private final RunecraftCompanionConfig config;
	private final SpriteManager spriteManager;
	private final Map<String, BufferedImage> tinted = new HashMap<>();

	@Inject
	EnergyOverlay(Client client, RunecraftCompanionPlugin plugin, RunecraftCompanionConfig config,
		SpriteManager spriteManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.warnEnergy() || !plugin.helpersOn())
		{
			return null;
		}
		int level = EnergyWarning.level(client.getEnergy() / 100);
		if (level == EnergyWarning.NONE)
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		boolean urgent = level >= EnergyWarning.VERY_LOW;
		Color c = urgent ? VERY_LOW : LOW;
		int pulse = urgent ? 120 + (int) (135 * Math.abs(Math.sin(System.currentTimeMillis() / 250.0))) : 220;

		// the boot icon itself flashes in the warning colour, the whole orb gets an outline
		float flash = (float) Math.abs(Math.sin(System.currentTimeMillis() / (urgent ? 200.0 : 400.0)));
		Widget icon = client.getWidget(InterfaceID.Orbs.RUNENERGY_ICON);
		BufferedImage boot = icon == null || icon.isHidden() ? null : tintedBoot(icon.getSpriteId(), c);
		if (boot != null)
		{
			Rectangle r = icon.getBounds();
			Composite before = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f + 0.75f * flash));
			g.drawImage(boot, r.x + (r.width - boot.getWidth()) / 2, r.y + (r.height - boot.getHeight()) / 2, null);
			g.setComposite(before);
		}
		Widget orb = client.getWidget(InterfaceID.Orbs.ORB_RUNENERGY);
		Rectangle b = orb == null || orb.isHidden() ? null : orb.getBounds();
		if (b != null)
		{
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), pulse));
			g.setStroke(new BasicStroke(urgent ? 3f : 2f));
			g.drawRoundRect(b.x - 2, b.y - 2, b.width + 4, b.height + 4, 12, 12);
			if (urgent)
			{
				drawBoxLeftOf(g, "Restore run energy before you go on", b.x - 8, b.y + b.height / 2, c);
			}
		}
		if (level >= EnergyWarning.CRITICAL)
		{
			Player me = client.getLocalPlayer();
			String text = "RESTORE STAMINA ASAP!";
			g.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 18f));
			Point p = me == null ? null : me.getCanvasTextLocation(g, text, me.getLogicalHeight() + 60);
			if (p != null)
			{
				OverlayUtil.renderTextLocation(g, p, text, new Color(255, 48, 48, pulse));
			}
		}
		return null;
	}

	/** The boot sprite with all its pixels in this colour (kept: made once per sprite and colour). */
	private BufferedImage tintedBoot(int spriteId, Color c)
	{
		if (spriteId < 0)
		{
			return null;
		}
		String key = spriteId + ":" + c.getRGB();
		BufferedImage done = tinted.get(key);
		if (done == null)
		{
			BufferedImage sprite = spriteManager.getSprite(spriteId, 0);
			if (sprite == null)
			{
				return null;   // not loaded yet: try again next frame
			}
			done = ImageUtil.fillImage(sprite, c);
			tinted.put(key, done);
		}
		return done;
	}

	/** A text box ending at x (its right side), centred on y: next to the run orb. */
	private static void drawBoxLeftOf(Graphics2D g, String text, int x, int y, Color c)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text) + 10;
		int h = fm.getHeight() + 4;
		int left = Math.max(2, x - w);
		int top = y - h / 2;
		g.setColor(new Color(0, 0, 0, 170));
		g.fillRoundRect(left, top, w, h, 6, 6);
		g.setColor(c);
		g.drawRoundRect(left, top, w, h, 6, 6);
		g.drawString(text, left + 5, top + fm.getAscent() + 2);
	}
}
