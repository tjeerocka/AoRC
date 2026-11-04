package com.runecraftcompanion;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Run energy warning (while you runecraft): at 30% the run orb gets a yellow outline, at 20%
 * a red pulsing one with "Restore run energy before you go on", at 10% also a big red
 * "RESTORE STAMINA ASAP!" above your character.
 */
class EnergyOverlay extends Overlay
{
	private static final Color LOW = new Color(255, 215, 0);
	private static final Color VERY_LOW = new Color(255, 48, 48);

	private final Client client;
	private final RunecraftCompanionPlugin plugin;
	private final RunecraftCompanionConfig config;

	@Inject
	EnergyOverlay(Client client, RunecraftCompanionPlugin plugin, RunecraftCompanionConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
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

		Widget orb = client.getWidget(InterfaceID.Orbs.ORB_RUNENERGY);
		Rectangle b = orb == null || orb.isHidden() ? null : orb.getBounds();
		if (b != null)
		{
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), pulse));
			g.setStroke(new BasicStroke(urgent ? 3f : 2f));
			g.drawRoundRect(b.x - 2, b.y - 2, b.width + 4, b.height + 4, 12, 12);
			if (urgent)
			{
				drawBox(g, "Restore run energy before you go on", b.x + b.width / 2, b.y + b.height + 6, c);
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

	/** A text box centred under (x, y). */
	private static void drawBox(Graphics2D g, String text, int x, int y, Color c)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text) + 10;
		int h = fm.getHeight() + 4;
		// the run orb is near the right edge: keep the box on screen
		int right = g.getClipBounds() == null ? Integer.MAX_VALUE : g.getClipBounds().x + g.getClipBounds().width - 2;
		int left = Math.max(2, Math.min(x - w / 2, right - w));
		g.setColor(new Color(0, 0, 0, 170));
		g.fillRoundRect(left, y, w, h, 6, 6);
		g.setColor(c);
		g.drawRoundRect(left, y, w, h, 6, 6);
		g.drawString(text, left + 5, y + fm.getAscent() + 2);
	}
}
