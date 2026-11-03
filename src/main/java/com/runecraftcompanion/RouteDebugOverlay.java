package com.runecraftcompanion;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Route debug (Developer setting): a box with the route of this altar (or the Zeah cycle), your
 * position and what happened last. The labels above the objects are drawn by RouteOverlay.
 */
class RouteDebugOverlay extends OverlayPanel
{
	private final RunecraftCompanionPlugin plugin;

	@Inject
	RouteDebugOverlay(RunecraftCompanionPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.routeDebug())
		{
			return null;
		}
		// the box
		panelComponent.getChildren().add(TitleComponent.builder().text("Route debug").color(Color.YELLOW).build());
		panelComponent.setPreferredSize(new Dimension(330, 0));
		for (String line : plugin.routeDebugLines())
		{
			panelComponent.getChildren().add(LineComponent.builder().left(line).build());
		}
		return super.render(g);
	}
}
