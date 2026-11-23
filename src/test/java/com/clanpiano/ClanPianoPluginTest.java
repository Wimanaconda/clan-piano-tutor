package com.clanpiano;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Starts a development RuneLite client with this plugin loaded. Run with ./gradlew run */
public class ClanPianoPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ClanPianoPlugin.class);
		RuneLite.main(args);
	}
}
