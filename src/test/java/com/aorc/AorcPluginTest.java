package com.aorc;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Run this main() in IntelliJ to start RuneLite with the plugin loaded. */
public class AorcPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AorcPlugin.class);
		RuneLite.main(args);
	}
}
