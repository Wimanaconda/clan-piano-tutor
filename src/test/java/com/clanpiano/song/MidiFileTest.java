package com.clanpiano.song;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.Test;

public class MidiFileTest
{
	@Test
	public void readsRunningStatusMetaAndProgramChanges()
	{
		ByteArrayOutputStream track = new ByteArrayOutputStream();
		write(track, 0x00, 0xFF, 0x03, 0x04, 'L', 'e', 'a', 'd'); // track name
		write(track, 0x00, 0xC1, 73);                         // program change: flute, channel 1
		write(track, 0x00, 0x91, 60, 90);                     // note on C4
		write(track, 0x83, 0x60, 62, 90);                     // 480 ticks later, running status: D4
		write(track, 0x00, 60, 0);                            // running status, velocity 0 = off
		write(track, 0x00, 0xF0, 0x02, 0x01, 0xF7);           // sysex, skipped
		write(track, 0x00, 0xFF, 0x2F, 0x00);                 // end of track

		ByteArrayOutputStream file = new ByteArrayOutputStream();
		write(file, 'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0x01, 0xE0); // format 0, 1 track, 480 ppq
		write(file, 'M', 'T', 'r', 'k', 0, 0, 0, track.size());
		file.write(track.toByteArray(), 0, track.size());

		MidiFile midi = MidiFile.parse(file.toByteArray());
		assertEquals(480.0, midi.ticksPerBeat, 0);
		List<MidiFile.Event> events = midi.tracks.get(0);
		assertEquals("Lead", events.get(0).metaText());
		assertEquals(MidiFile.PROGRAM_CHANGE, events.get(1).command);
		assertEquals(73, events.get(1).data1);
		assertEquals(1, events.get(2).channel);
		MidiFile.Event d4 = events.get(3);
		assertEquals(480, d4.tick);
		assertEquals(MidiFile.NOTE_ON, d4.command);
		assertEquals(62, d4.data1);
		assertEquals(0, events.get(4).data2);
		assertEquals(6, events.size()); // name, program, 3 notes, end of track
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsFilesThatAreNotMidi()
	{
		MidiFile.parse("title: not midi".getBytes());
	}

	private static void write(ByteArrayOutputStream out, int... bytes)
	{
		for (int b : bytes)
		{
			out.write(b);
		}
	}
}
