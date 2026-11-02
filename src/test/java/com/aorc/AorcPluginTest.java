package com.aorc;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Run this main() in IntelliJ to start RuneLite with the plugin loaded. */
public class AorcPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AorcPlugin.class);
		// developer mode: shows RuneLite's Developer Tools plugin (object IDs, inspector)
		String[] all = java.util.Arrays.copyOf(args, args.length + 1);
		all[args.length] = "--developer-mode";
		RuneLite.main(java.util.Arrays.asList(args).contains("--developer-mode") ? args : all);
	}
}
