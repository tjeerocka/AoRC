package com.runecraftcompanion;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * Talking to the Dark Mage to repair pouches: what to click in the chat box
 * ("Click here to continue" and "Can you repair my pouches?") gets the colour from the settings.
 */
final class RepairDialog
{
	// chat box dialogues: NPC talking, player talking, options
	static final int DIALOG_NPC = 231;
	static final int DIALOG_NPC_NAME = 4;
	static final int DIALOG_NPC_TEXT = 6;
	static final int DIALOG_PLAYER = 217;
	static final int DIALOG_OPTIONS = 219;
	static final String REPAIR_OPTION = "Can you repair my pouches?";

	private RepairDialog()
	{
	}

	/** Colour what to click: "Can you repair my pouches?", else "Click here to continue". */
	static void colourTarget(Client client, int rgb)
	{
		Widget target = findOption(client, REPAIR_OPTION);
		if (target == null)
		{
			target = findContinue(client);
		}
		if (target != null && !target.isHidden() && target.getTextColor() != rgb)
		{
			target.setTextColor(rgb);
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
