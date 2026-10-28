package com.aorc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.NumberFormat;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Sidebar panel: all rune types with count and per hour, plus totals and resets. */
class AorcPanel extends PluginPanel
{
	private static final Color BLOOD = new Color(200, 40, 40);
	private static final Color BONUS = new Color(120, 200, 120);

	private final ItemManager itemManager;
	private final NumberFormat nf = NumberFormat.getIntegerInstance();

	private final JLabel timeLabel = new JLabel();
	private final JPanel statsPanel = new JPanel(new GridLayout(0, 2, 4, 3));
	private final JPanel runesPanel = new JPanel();
	private final JLabel emptyLabel = new JLabel("Craft some runes to start.");

	@Inject
	AorcPanel(AorcPlugin plugin, ItemManager itemManager, ClientThread clientThread)
	{
		super(false);
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Amount of Runes Created");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(BLOOD);
		title.setAlignmentX(LEFT_ALIGNMENT);
		content.add(title);

		timeLabel.setForeground(Color.LIGHT_GRAY);
		timeLabel.setAlignmentX(LEFT_ALIGNMENT);
		content.add(timeLabel);
		content.add(spacer(8));

		statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		statsPanel.setAlignmentX(LEFT_ALIGNMENT);
		content.add(statsPanel);
		content.add(spacer(8));

		JLabel runesTitle = new JLabel("Runes crafted");
		runesTitle.setFont(FontManager.getRunescapeBoldFont());
		runesTitle.setForeground(Color.WHITE);
		runesTitle.setAlignmentX(LEFT_ALIGNMENT);
		content.add(runesTitle);

		JLabel zmiNote = new JLabel("<html>Crafting runes at ZMI? Every rune type you get will appear here.</html>");
		zmiNote.setForeground(Color.GRAY);
		zmiNote.setFont(FontManager.getRunescapeSmallFont());
		zmiNote.setAlignmentX(LEFT_ALIGNMENT);
		content.add(zmiNote);
		content.add(spacer(4));

		runesPanel.setLayout(new BoxLayout(runesPanel, BoxLayout.Y_AXIS));
		runesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		runesPanel.setAlignmentX(LEFT_ALIGNMENT);
		content.add(runesPanel);
		content.add(spacer(10));

		JPanel buttons = new JPanel(new GridLayout(0, 2, 4, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.add(button("Reset per hour", () -> clientThread.invokeLater(plugin::resetRates)));
		buttons.add(button("Reset runes", () -> clientThread.invokeLater(plugin::resetRuneCount)));
		buttons.add(button("Reset bonus", () -> clientThread.invokeLater(plugin::resetBonusRunes)));
		buttons.add(button("Reset all", () -> clientThread.invokeLater(plugin::resetAll)));
		content.add(buttons);

		add(content, BorderLayout.NORTH);

		emptyLabel.setForeground(Color.GRAY);
		update(new PanelData());
	}

	/** Call from any thread; the panel updates on the Swing thread. */
	void post(PanelData data)
	{
		SwingUtilities.invokeLater(() -> update(data));
	}

	private void update(PanelData d)
	{
		timeLabel.setText("Active time: " + PanelData.formatTime(d.activeMillis));

		statsPanel.removeAll();
		stat("RC xp / hr", fmt(d.xpPerHour), Color.WHITE);
		stat("GP / hr", GpFormat.shortGp(d.gpPerHour), Color.YELLOW);
		stat("All runes / hr", fmt(d.allRunesPerHour), Color.WHITE);
		stat("Crafts", fmt(d.crafts), Color.WHITE);
		stat("Blocks imbued", fmt(d.blocksImbued), Color.WHITE);
		stat("Bonus", "+" + d.bonusPercent + "%", BONUS);
		stat("Bonus runes", fmt(d.bonusRunes), BONUS);

		runesPanel.removeAll();
		if (d.rows.isEmpty())
		{
			runesPanel.add(emptyLabel);
		}
		for (PanelData.Row r : d.rows)
		{
			runesPanel.add(runeRow(r));
			runesPanel.add(spacer(3));
		}

		revalidate();
		repaint();
	}

	private JPanel runeRow(PanelData.Row r)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		itemManager.getImage(r.itemId).addTo(icon);
		row.add(icon, BorderLayout.WEST);

		Color c = r.itemId == RuneIds.BLOOD_RUNE ? BLOOD : Color.WHITE;

		JLabel name = new JLabel(r.name);
		name.setForeground(c);
		row.add(name, BorderLayout.CENTER);

		JLabel right = new JLabel("<html><div style='text-align:right'>" + fmt(r.count)
			+ "<br><span style='color:#aaaaaa'>" + fmt(r.perHour) + " / hr</span></div></html>");
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		right.setForeground(c);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private void stat(String left, String right, Color color)
	{
		JLabel l = new JLabel(left);
		l.setForeground(Color.LIGHT_GRAY);
		JLabel r = new JLabel(right);
		r.setForeground(color);
		r.setHorizontalAlignment(SwingConstants.RIGHT);
		statsPanel.add(l);
		statsPanel.add(r);
	}

	private static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.addActionListener(e -> action.run());
		return b;
	}

	private static JPanel spacer(int height)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(1, height));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	private String fmt(long v)
	{
		return nf.format(v);
	}
}
