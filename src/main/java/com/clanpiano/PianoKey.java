package com.clanpiano;

import lombok.Getter;
import net.runelite.api.gameval.InterfaceID;

/**
 * The 24 keys of the Clan Hall piano, C4 to B5, in pitch order.
 * The game names the octaves 1 and 2; musically they are octaves 4 and 5.
 */
@Getter
public enum PianoKey
{
	C4("C", 4, false, InterfaceID.ClanPiano.KEY_C1, InterfaceID.ClanPiano.LABEL_C1),
	CS4("C#", 4, true, InterfaceID.ClanPiano.KEY_CSHARP1, InterfaceID.ClanPiano.LABEL_CSHARP1),
	D4("D", 4, false, InterfaceID.ClanPiano.KEY_D1, InterfaceID.ClanPiano.LABEL_D1),
	DS4("D#", 4, true, InterfaceID.ClanPiano.KEY_DSHARP1, InterfaceID.ClanPiano.LABEL_DSHARP1),
	E4("E", 4, false, InterfaceID.ClanPiano.KEY_E1, InterfaceID.ClanPiano.LABEL_E1),
	F4("F", 4, false, InterfaceID.ClanPiano.KEY_F1, InterfaceID.ClanPiano.LABEL_F1),
	FS4("F#", 4, true, InterfaceID.ClanPiano.KEY_FSHARP1, InterfaceID.ClanPiano.LABEL_FSHARP1),
	G4("G", 4, false, InterfaceID.ClanPiano.KEY_G1, InterfaceID.ClanPiano.LABEL_G1),
	GS4("G#", 4, true, InterfaceID.ClanPiano.KEY_GSHARP1, InterfaceID.ClanPiano.LABEL_GSHARP1),
	A4("A", 4, false, InterfaceID.ClanPiano.KEY_A1, InterfaceID.ClanPiano.LABEL_A1),
	AS4("A#", 4, true, InterfaceID.ClanPiano.KEY_ASHARP1, InterfaceID.ClanPiano.LABEL_ASHARP1),
	B4("B", 4, false, InterfaceID.ClanPiano.KEY_B1, InterfaceID.ClanPiano.LABEL_B1),
	C5("C", 5, false, InterfaceID.ClanPiano.KEY_C2, InterfaceID.ClanPiano.LABEL_C2),
	CS5("C#", 5, true, InterfaceID.ClanPiano.KEY_CSHARP2, InterfaceID.ClanPiano.LABEL_CSHARP2),
	D5("D", 5, false, InterfaceID.ClanPiano.KEY_D2, InterfaceID.ClanPiano.LABEL_D2),
	DS5("D#", 5, true, InterfaceID.ClanPiano.KEY_DSHARP2, InterfaceID.ClanPiano.LABEL_DSHARP2),
	E5("E", 5, false, InterfaceID.ClanPiano.KEY_E2, InterfaceID.ClanPiano.LABEL_E2),
	F5("F", 5, false, InterfaceID.ClanPiano.KEY_F2, InterfaceID.ClanPiano.LABEL_F2),
	FS5("F#", 5, true, InterfaceID.ClanPiano.KEY_FSHARP2, InterfaceID.ClanPiano.LABEL_FSHARP2),
	G5("G", 5, false, InterfaceID.ClanPiano.KEY_G2, InterfaceID.ClanPiano.LABEL_G2),
	GS5("G#", 5, true, InterfaceID.ClanPiano.KEY_GSHARP2, InterfaceID.ClanPiano.LABEL_GSHARP2),
	A5("A", 5, false, InterfaceID.ClanPiano.KEY_A2, InterfaceID.ClanPiano.LABEL_A2),
	AS5("A#", 5, true, InterfaceID.ClanPiano.KEY_ASHARP2, InterfaceID.ClanPiano.LABEL_ASHARP2),
	B5("B", 5, false, InterfaceID.ClanPiano.KEY_B2, InterfaceID.ClanPiano.LABEL_B2);

	public static final int LOWEST_MIDI = 60; // C4
	private static final PianoKey[] VALUES = values();

	private final String pitchClass;
	private final int octave;
	private final boolean black;
	private final int widgetId;
	private final int labelWidgetId;

	PianoKey(String pitchClass, int octave, boolean black, int widgetId, int labelWidgetId)
	{
		this.pitchClass = pitchClass;
		this.octave = octave;
		this.black = black;
		this.widgetId = widgetId;
		this.labelWidgetId = labelWidgetId;
	}

	/** Position on the keyboard, 0 (C4) to 23 (B5). */
	public int index()
	{
		return ordinal();
	}

	/**
	 * The name as printed on the piano ("C", "F#"), with ' added for the right-hand
	 * (higher) octave, since the piano prints the same letters for both halves.
	 */
	public String displayName()
	{
		return octave == 5 ? pitchClass + "'" : pitchClass;
	}

	public static PianoKey fromIndex(int index)
	{
		return index >= 0 && index < VALUES.length ? VALUES[index] : null;
	}

	public static PianoKey fromMidi(int midi)
	{
		return fromIndex(midi - LOWEST_MIDI);
	}

	/**
	 * The key a clicked widget belongs to. In game the click lands on the key's letter
	 * label (menu option "Play-g" on LABEL_G1), which covers the key; the 3D key model
	 * itself is not clickable. The model id is accepted too, just in case.
	 */
	public static PianoKey fromWidgetId(int widgetId)
	{
		for (PianoKey key : VALUES)
		{
			if (key.labelWidgetId == widgetId || key.widgetId == widgetId)
			{
				return key;
			}
		}
		return null;
	}
}
