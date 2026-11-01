package com.aorc;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Talking to the Dark Mage to repair pouches: what to click in the chat box
 * ("Click here to continue" and "Can you repair my pouches?") is coloured orange.
 */
class RepairDialogOverlay extends Overlay
{
	// chat box dialogues: NPC talking, player talking, options
	static final int DIALOG_NPC = 231;
	static final int DIALOG_PLAYER = 217;
	static final int DIALOG_OPTIONS = 219;
	static final String REPAIR_OPTION = "Can you repair my pouches?";
	/** Orange that reads well on the chat box background. */
	static final int ORANGE = 0xE06A00;

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
		// nothing to draw: the plugin colours the text itself (see colourTarget)
		return null;
	}

	/** Colour what to click orange: "Can you repair my pouches?", else "Click here to continue". */
	static void colourTarget(Client client)
	{
		Widget target = findOption(client, REPAIR_OPTION);
		if (target == null)
		{
			target = findContinue(client);
		}
		if (target != null && !target.isHidden() && target.getTextColor() != ORANGE)
		{
			target.setTextColor(ORANGE);
		}
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
}
