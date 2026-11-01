package com.aorc;

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
import net.runelite.client.util.Text;

/**
 * Talking to the Dark Mage to repair pouches: highlights what to click in the chat box,
 * "Click here to continue" and "Can you repair my pouches?".
 */
class RepairDialogOverlay extends Overlay
{
	// chat box dialogues: NPC talking, player talking, options
	static final int DIALOG_NPC = 231;
	static final int DIALOG_PLAYER = 217;
	static final int DIALOG_OPTIONS = 219;
	static final String REPAIR_OPTION = "Can you repair my pouches?";

	private final Client client;
	private final AorcPlugin plugin;

	@Inject
	RepairDialogOverlay(Client client, AorcPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.isRepairGuide())
		{
			return null;
		}
		Widget target = findOption(client, REPAIR_OPTION);
		if (target == null)
		{
			target = findContinue(client);
		}
		if (target != null && !target.isHidden())
		{
			box(g, target.getBounds());
		}
		return null;
	}

	/** The option line with this text in the "Select an option" box, or null. */
	static Widget findOption(Client client, String text)
	{
		Widget options = client.getWidget(DIALOG_OPTIONS, 1);
		if (options == null || options.isHidden())
		{
			return null;
		}
		Widget[] lines = options.getDynamicChildren();
		if (lines == null)
		{
			return null;
		}
		for (Widget w : lines)
		{
			if (w != null && w.getText() != null && Text.removeTags(w.getText()).trim().equalsIgnoreCase(text))
			{
				return w;
			}
		}
		return null;
	}

	/** "Click here to continue" of an NPC or player dialogue, or null. */
	static Widget findContinue(Client client)
	{
		for (int group : new int[]{DIALOG_NPC, DIALOG_PLAYER})
		{
			for (int child = 0; child < 12; child++)
			{
				Widget w = client.getWidget(group, child);
				if (w != null && !w.isHidden() && w.getText() != null
					&& Text.removeTags(w.getText()).toLowerCase().contains("click here to continue"))
				{
					return w;
				}
			}
		}
		return null;
	}

	private static void box(Graphics2D g, Rectangle b)
	{
		if (b == null || b.width <= 0)
		{
			return;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int a = 120 + (int) (80 * Math.abs(Math.sin(System.currentTimeMillis() / 400.0)));
		Color c = PouchOverlay.REPAIR;
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
		g.fillRoundRect(b.x - 3, b.y - 2, b.width + 6, b.height + 4, 6, 6);
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
		g.setStroke(new BasicStroke(2f));
		g.drawRoundRect(b.x - 3, b.y - 2, b.width + 6, b.height + 4, 6, 6);
	}
}
