package com.clanpiano.song;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A minimal reader for Standard MIDI Files (.mid): just enough to get the notes, program changes
 * and track names of each track. Plugins may not use javax.sound, so the file is parsed here.
 */
final class MidiFile
{
	static final int NOTE_ON = 0x90;
	static final int PROGRAM_CHANGE = 0xC0;
	static final int META_TRACK_NAME = 0x03;

	/** One event in a track, at an absolute tick. Channel messages have a command; meta events a metaType. */
	static final class Event
	{
		final long tick;
		/** Upper nibble of the status byte (0x80..0xE0), or -1 for meta events. */
		final int command;
		final int channel;
		final int data1;
		final int data2;
		/** Meta event type, or -1 for channel messages. */
		final int metaType;
		final byte[] metaData;

		private Event(long tick, int command, int channel, int data1, int data2, int metaType, byte[] metaData)
		{
			this.tick = tick;
			this.command = command;
			this.channel = channel;
			this.data1 = data1;
			this.data2 = data2;
			this.metaType = metaType;
			this.metaData = metaData;
		}

		String metaText()
		{
			return new String(metaData, StandardCharsets.ISO_8859_1);
		}
	}

	/** Ticks per quarter note; for SMPTE-timed files, the ticks in half a second. */
	final double ticksPerBeat;
	final List<List<Event>> tracks;

	private MidiFile(double ticksPerBeat, List<List<Event>> tracks)
	{
		this.ticksPerBeat = ticksPerBeat;
		this.tracks = Collections.unmodifiableList(tracks);
	}

	static MidiFile parse(byte[] data)
	{
		Reader r = new Reader(data);
		if (!"MThd".equals(r.chunkType()))
		{
			throw new IllegalArgumentException("not a MIDI file (no MThd header)");
		}
		long headerLength = r.u32();
		if (headerLength < 6 || headerLength > r.remaining())
		{
			throw new IllegalArgumentException("MIDI header is damaged");
		}
		int headerEnd = r.pos + (int) headerLength;
		r.u16(); // format: 0, 1 and 2 are all read track by track
		int trackCount = r.u16();
		int division = r.u16();
		r.pos = headerEnd;

		double ticksPerBeat;
		if ((division & 0x8000) == 0)
		{
			ticksPerBeat = division;
		}
		else
		{
			// SMPTE timing: frames per second times ticks per frame is ticks per second.
			int framesPerSecond = -(byte) (division >> 8);
			int ticksPerFrame = division & 0xFF;
			ticksPerBeat = framesPerSecond * ticksPerFrame / 2.0;
		}
		if (ticksPerBeat <= 0)
		{
			throw new IllegalArgumentException("MIDI file has no timing information");
		}

		List<List<Event>> tracks = new ArrayList<>();
		while (tracks.size() < trackCount && r.remaining() >= 8)
		{
			String type = r.chunkType();
			long length = r.u32();
			int end = (int) Math.min(r.pos + length, data.length);
			if ("MTrk".equals(type))
			{
				tracks.add(readTrack(r, end));
			}
			r.pos = end;
		}
		if (tracks.isEmpty())
		{
			throw new IllegalArgumentException("MIDI file has no tracks");
		}
		return new MidiFile(ticksPerBeat, tracks);
	}

	private static List<Event> readTrack(Reader r, int end)
	{
		List<Event> events = new ArrayList<>();
		long tick = 0;
		int running = 0;
		// A damaged track keeps the events read so far.
		try
		{
			while (r.pos < end)
			{
				tick += r.varLength();
				int status = r.u8();
				if (status == 0xFF)
				{
					int type = r.u8();
					int length = (int) r.varLength();
					events.add(new Event(tick, -1, 0, 0, 0, type, r.bytes(length, end)));
					if (type == 0x2F)
					{
						break; // end of track
					}
					continue;
				}
				if (status == 0xF0 || status == 0xF7)
				{
					r.bytes((int) r.varLength(), end); // system exclusive: skip
					continue;
				}
				int data1;
				if (status < 0x80)
				{
					if (running == 0)
					{
						break; // data byte with no status to repeat
					}
					data1 = status;
					status = running;
				}
				else if (status >= 0xF0)
				{
					break; // system messages don't belong in a file
				}
				else
				{
					running = status;
					data1 = r.u8();
				}
				int command = status & 0xF0;
				int data2 = command == 0xC0 || command == 0xD0 ? 0 : r.u8();
				events.add(new Event(tick, command, status & 0x0F, data1 & 0x7F, data2 & 0x7F, -1, null));
			}
		}
		catch (IndexOutOfBoundsException e)
		{
			// Track ran past its chunk or the file: keep what was read.
		}
		return events;
	}

	private static final class Reader
	{
		private final byte[] data;
		private int pos;

		Reader(byte[] data)
		{
			this.data = data;
		}

		int remaining()
		{
			return data.length - pos;
		}

		int u8()
		{
			if (pos >= data.length)
			{
				throw new IndexOutOfBoundsException();
			}
			return data[pos++] & 0xFF;
		}

		int u16()
		{
			return (u8() << 8) | u8();
		}

		long u32()
		{
			return ((long) u16() << 16) | u16();
		}

		String chunkType()
		{
			if (remaining() < 4)
			{
				return "";
			}
			String type = new String(data, pos, 4, StandardCharsets.ISO_8859_1);
			pos += 4;
			return type;
		}

		long varLength()
		{
			long value = 0;
			for (int i = 0; i < 4; i++)
			{
				int b = u8();
				value = (value << 7) | (b & 0x7F);
				if ((b & 0x80) == 0)
				{
					return value;
				}
			}
			throw new IndexOutOfBoundsException(); // longer than the 4 bytes MIDI allows
		}

		byte[] bytes(int length, int end)
		{
			if (length < 0 || pos + length > end)
			{
				throw new IndexOutOfBoundsException();
			}
			byte[] out = new byte[length];
			System.arraycopy(data, pos, out, 0, length);
			pos += length;
			return out;
		}
	}
}
