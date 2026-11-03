package com.runecraftcompanion;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Spellbook: an orange box around Astral Contact (was "NPC Contact") while a pouch needs
 * a repair - cast it on the Dark Mage to repair your pouches.
 */
class RepairSpellOverlay extends Overlay
{
	/** Spellbook interface. */
	private static final int SPELLBOOK = 218;

	private final Client client;
	private final RunecraftCompanionPlugin plugin;
	private int spellChild = -1;   // found once, then reused
	private int searchedTick = Integer.MIN_VALUE;   // not found: search again next tick only

	@Inject
	RepairSpellOverlay(Client client, RunecraftCompanionPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.anyPouchNeedsRepair())
		{
			return null;
		}
		Widget spell = findSpell();
		if (spell == null || spell.isHidden())
		{
			return null;
		}
		Rectangle b = spell.getBounds();
		if (b == null || b.width <= 0)
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int a = 120 + (int) (80 * Math.abs(Math.sin(System.currentTimeMillis() / 400.0)));
		Color c = PouchOverlay.REPAIR;
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
		g.fillRoundRect(b.x - 2, b.y - 2, b.width + 4, b.height + 4, 6, 6);
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
		g.setStroke(new BasicStroke(2f));
		g.drawRoundRect(b.x - 2, b.y - 2, b.width + 4, b.height + 4, 6, 6);
		return null;
	}

	/** The Astral Contact spell button (looked up by name, so it works on any spellbook layout). */
	private Widget findSpell()
	{
		if (spellChild >= 0)
		{
			Widget w = client.getWidget(SPELLBOOK, spellChild);
			if (isContactSpell(w))
			{
				return w;
			}
			spellChild = -1;
		}
		if (searchedTick == plugin.getTick())
		{
			return null;
		}
		searchedTick = plugin.getTick();
		for (int i = 0; i < 250; i++)
		{
			Widget w = client.getWidget(SPELLBOOK, i);
			if (isContactSpell(w))
			{
				spellChild = i;
				return w;
			}
		}
		return null;
	}

	private static boolean isContactSpell(Widget w)
	{
		if (w == null || w.getName() == null)
		{
			return false;
		}
		String n = w.getName();
		return n.contains("Astral Contact") || n.contains("NPC Contact");
	}
}
