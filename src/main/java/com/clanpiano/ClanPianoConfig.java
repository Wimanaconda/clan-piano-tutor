package com.clanpiano;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(ClanPianoConfig.GROUP)
public interface ClanPianoConfig extends Config
{
	String GROUP = "clanpianotutor";

	@ConfigSection(
		name = "Playing",
		description = "How songs are played",
		position = 0
	)
	String playing = "playing";

	@ConfigSection(
		name = "Display",
		description = "What is drawn on the piano",
		position = 1
	)
	String display = "display";

	String MODE_KEY = "playMode";

	@ConfigItem(
		keyName = MODE_KEY,
		name = "Mode",
		description = "Timed moves one note every game tick (0.6s), the timing the rest of the hall hears. "
			+ "Practice waits for you.",
		section = playing,
		position = 0
	)
	default PlayMode mode()
	{
		return PlayMode.TEMPO;
	}

	@Range(max = 3)
	@ConfigItem(
		keyName = "greenLeadTicks",
		name = "Light keys early",
		description = "Timed mode: how many ticks before a note lands its key turns green, "
			+ "so there is time to click it. 0 = only on its own tick.",
		section = playing,
		position = 4
	)
	default int leadTicks()
	{
		return 1;
	}

	@Range(max = 16)
	@ConfigItem(
		keyName = "countIn",
		name = "Tempo count-in",
		description = "Ticks to wait before the first note in tempo mode",
		section = playing,
		position = 1
	)
	default int countIn()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "loop",
		name = "Loop song",
		description = "Start again from the top when the song ends",
		section = playing,
		position = 2
	)
	default boolean loop()
	{
		return false;
	}

	@ConfigItem(
		keyName = "matchKeyboardLetters",
		name = "Count keyboard presses",
		description = "Also count key presses that match the letters printed on the piano keys",
		section = playing,
		position = 3
	)
	default boolean keyboardInput()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showInfoBox",
		name = "Song info box",
		description = "Show the song, progress and upcoming notes in an overlay",
		section = display,
		position = 0
	)
	default boolean showInfoBox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "timingLane",
		name = "Falling notes",
		description = "Show notes falling towards the keys, landing on the tick they should be played",
		section = display,
		position = 7
	)
	default boolean showTimingLane()
	{
		return true;
	}

	@Range(min = 2, max = 8)
	@ConfigItem(
		keyName = "timingLaneTicks",
		name = "Falling notes look-ahead",
		description = "How many ticks ahead the falling notes show",
		section = display,
		position = 8
	)
	default int laneTicks()
	{
		return 4;
	}

	@Range(min = 1, max = 16)
	@ConfigItem(
		keyName = "upcomingCount",
		name = "Upcoming notes",
		description = "How many upcoming notes the info box lists",
		section = display,
		position = 1
	)
	default int upcomingCount()
	{
		return 6;
	}

	@ConfigItem(
		keyName = "outlineNextKey",
		name = "Outline the note after",
		description = "Outline the key that comes after the current one",
		section = display,
		position = 2
	)
	default boolean showNextKey()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showWrongKey",
		name = "Flash wrong keys",
		description = "Flash a key red when you press the wrong one",
		section = display,
		position = 6
	)
	default boolean showWrongKey()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "currentColor",
		name = "Play now",
		description = "Colour of the key(s) to play now",
		section = display,
		position = 3
	)
	default Color currentColor()
	{
		return new Color(0, 220, 90, 170);
	}

	@Alpha
	@ConfigItem(
		keyName = "nextColor",
		name = "Next note",
		description = "Outline colour of the key(s) after that",
		section = display,
		position = 4
	)
	default Color nextColor()
	{
		return new Color(255, 200, 0, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "wrongColor",
		name = "Wrong key",
		description = "Flash colour when the wrong key is pressed",
		section = display,
		position = 5
	)
	default Color wrongColor()
	{
		return new Color(230, 40, 40, 160);
	}
}
