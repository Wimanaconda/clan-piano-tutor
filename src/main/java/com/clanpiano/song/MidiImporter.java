package com.clanpiano.song;

import com.clanpiano.PianoKey;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a MIDI file into a playable song for the 24-key, one-note-per-tick piano.
 *
 * <ol>
 * <li><b>Melody.</b> Every instrument part (track + channel, drums skipped) is scored, and the
 * busiest, highest, least chord-heavy part is taken as the tune. Where it still plays
 * several notes at once, the top note is kept.</li>
 * <li><b>Rhythm.</b> The song keeps its rhythm exactly: the largest note length that the
 * melody's notes line up with (a beat, an eighth, a sixteenth, a triplet...) becomes one game
 * tick, so the tune plays slower or faster but every note keeps its place.</li>
 * <li><b>Range.</b> The tune is moved by whole octaves (or, failing that, semitones) to fit
 * C4 to B5. Any note that still doesn't fit is moved by an octave into range.</li>
 * </ol>
 */
@Slf4j
public final class MidiImporter
{
	private static final int DRUM_CHANNEL = 9;
	private static final int LOW = PianoKey.LOWEST_MIDI;           // C4
	private static final int HIGH = PianoKey.LOWEST_MIDI + 23;     // B5
	/** Grid sizes tried, in beats, largest first. */
	private static final double[] GRIDS = {2, 1.5, 1, 2 / 3.0, 0.5, 1 / 3.0, 0.25, 1 / 6.0, 0.125};
	/**
	 * Share of melody notes that must sit on the grid, and how many may be lost where two land
	 * on one tick. A few grace notes and fast runs are given up so the song keeps a playable
	 * tempo instead of crawling along one sixteenth per tick.
	 */
	private static final double ON_GRID = 0.85;
	private static final double MAX_DROPPED = 0.12;
	/** Songs are cut to about this many ticks (2.4 minutes); game tracks loop for much longer. */
	static final int MAX_STEPS = 240;
	/** How much of the start of the song is used to judge which part is the tune. */
	private static final double OPENING_BEATS = 96;
	/** Candidate melodies kept per file, so a wrong first guess can be switched by ear. */
	private static final int MAX_VERSIONS = 4;

	private MidiImporter()
	{
	}

	/** One note-on in the file, in MIDI ticks. */
	static final class Note
	{
		final long tick;
		final int pitch;

		Note(long tick, int pitch)
		{
			this.tick = tick;
			this.pitch = pitch;
		}
	}

	public static Song load(InputStream in, String title, String source) throws IOException
	{
		Sequence sequence;
		try
		{
			sequence = MidiSystem.getSequence(in);
		}
		catch (InvalidMidiDataException e)
		{
			throw new IllegalArgumentException("not a MIDI file we can read (" + e.getMessage() + ")");
		}
		return convert(sequence, title, source);
	}

	static Song convert(Sequence sequence, String title, String source)
	{
		double ticksPerBeat = sequence.getDivisionType() == Sequence.PPQ
			? sequence.getResolution()
			// SMPTE timing: count half a second as a beat.
			: sequence.getResolution() * sequence.getDivisionType() / 2.0;
		if (ticksPerBeat <= 0)
		{
			throw new IllegalArgumentException("MIDI file has no timing information");
		}

		// Collect notes per part (track + channel).
		Map<String, List<Note>> parts = new HashMap<>();
		Map<String, String> partNames = new HashMap<>();
		Map<String, Integer> programs = new HashMap<>();
		Track[] tracks = sequence.getTracks();
		for (int t = 0; t < tracks.length; t++)
		{
			String trackName = null;
			for (int i = 0; i < tracks[t].size(); i++)
			{
				MidiEvent event = tracks[t].get(i);
				MidiMessage msg = event.getMessage();
				if (msg instanceof MetaMessage && ((MetaMessage) msg).getType() == 0x03)
				{
					trackName = new String(((MetaMessage) msg).getData()).trim();
				}
				if (!(msg instanceof ShortMessage))
				{
					continue;
				}
				ShortMessage sm = (ShortMessage) msg;
				if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE)
				{
					programs.putIfAbsent(t + ":" + sm.getChannel(), sm.getData1());
				}
				if (sm.getCommand() != ShortMessage.NOTE_ON || sm.getData2() == 0 || sm.getChannel() == DRUM_CHANNEL)
				{
					continue;
				}
				String key = t + ":" + sm.getChannel();
				parts.computeIfAbsent(key, k -> new ArrayList<>()).add(new Note(event.getTick(), sm.getData1()));
				if (trackName != null && !trackName.isEmpty())
				{
					partNames.putIfAbsent(key, trackName);
				}
			}
		}
		if (parts.isEmpty())
		{
			throw new IllegalArgumentException("MIDI file has no notes (only drums or empty)");
		}

		// Rank the parts by how much they look like the tune during the song's opening, which is
		// the part that gets played. Keep the best few so the player can switch by ear.
		long songStart = Long.MAX_VALUE;
		long songEnd = 0;
		for (List<Note> notes : parts.values())
		{
			for (Note n : notes)
			{
				songStart = Math.min(songStart, n.tick);
				songEnd = Math.max(songEnd, n.tick);
			}
		}
		long windowEnd = Math.min(songEnd, songStart + (long) (OPENING_BEATS * ticksPerBeat));
		List<Map.Entry<String, Double>> ranked = new ArrayList<>();
		for (Map.Entry<String, List<Note>> e : parts.entrySet())
		{
			List<Note> opening = new ArrayList<>();
			for (Note n : e.getValue())
			{
				if (n.tick <= windowEnd)
				{
					opening.add(n);
				}
			}
			double score = melodyScore(opening)
				* coverage(opening, songStart, windowEnd, ticksPerBeat)
				* programWeight(programs.getOrDefault(e.getKey(), 0));
			if (score > 0)
			{
				ranked.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), score));
			}
		}
		if (ranked.isEmpty())
		{
			throw new IllegalArgumentException("MIDI file has no part that looks like a melody");
		}
		ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

		List<Song> versions = new ArrayList<>();
		Map<String, Integer> labelCount = new HashMap<>();
		for (Map.Entry<String, Double> e : ranked)
		{
			if (versions.size() >= MAX_VERSIONS)
			{
				break;
			}
			String part = e.getKey();
			List<Step> steps = arrange(parts.get(part), ticksPerBeat, source + " " + part);
			// Alternatives must be worth playing; the best guess is kept whatever its length.
			if (!versions.isEmpty() && steps.stream().filter(st -> !st.isRest()).count() < 8)
			{
				continue;
			}
			String instrument = programs.containsKey(part) ? instrumentName(programs.get(part)) : "piano";
			int n = labelCount.merge(instrument, 1, Integer::sum);
			String label = n == 1 ? instrument : instrument + " " + n;
			versions.add(new Song(title, "From MIDI, " + label, source, steps).withPart(label));
		}
		if (versions.isEmpty())
		{
			throw new IllegalArgumentException("MIDI file's parts are too short to play");
		}
		log.debug("MIDI {}: {} parts, melody candidates {}", source, parts.size(), ranked);
		return versions.get(0).withAlternatives(versions.subList(1, versions.size()));
	}

	/** One part as piano steps: top line, rhythm grid, cut to length, fitted to the keys. */
	private static List<Step> arrange(List<Note> notes, double ticksPerBeat, String what)
	{
		List<Note> melody = topLine(notes);
		double grid = pickGrid(melody, ticksPerBeat);
		double slotTicks = grid * ticksPerBeat;
		long first = melody.get(0).tick;
		TreeMap<Integer, Integer> slots = new TreeMap<>();
		Map<Integer, Double> slotError = new HashMap<>();
		int dropped = 0;
		for (Note n : melody)
		{
			double pos = (n.tick - first) / slotTicks;
			int slot = (int) Math.round(pos);
			if (slot >= MAX_STEPS)
			{
				break;
			}
			double error = Math.abs(pos - slot);
			Double had = slotError.get(slot);
			if (had != null)
			{
				// Two notes on one tick: keep the one that sits on the beat, not the grace note.
				dropped++;
				if (had <= error)
				{
					continue;
				}
			}
			slots.put(slot, n.pitch);
			slotError.put(slot, error);
		}

		int shift = pickShift(new ArrayList<>(slots.values()));
		int folded = 0;
		List<Step> steps = new ArrayList<>();
		for (int i = 0; i <= slots.lastKey(); i++)
		{
			Integer pitch = slots.get(i);
			if (pitch == null)
			{
				steps.add(Step.REST);
				continue;
			}
			int p = pitch + shift;
			if (p < LOW || p > HIGH)
			{
				folded++;
				while (p < LOW)
				{
					p += 12;
				}
				while (p > HIGH)
				{
					p -= 12;
				}
			}
			steps.add(new Step(EnumSet.of(PianoKey.fromMidi(p))));
		}
		log.debug("  {}: 1 tick = {} beats, shift {}, {} doubled notes dropped, {} folded", what, grid, shift, dropped, folded);
		return steps;
	}

	/** Share of two-beat windows in the opening where the part plays something. */
	static double coverage(List<Note> notes, long start, long end, double ticksPerBeat)
	{
		double window = 2 * ticksPerBeat;
		int windows = Math.max(1, (int) Math.ceil((end - start + 1) / window));
		java.util.Set<Integer> hit = new java.util.HashSet<>();
		for (Note n : notes)
		{
			hit.add((int) ((n.tick - start) / window));
		}
		return Math.min(1, hit.size() / (double) windows);
	}

	/** Busy, high and mostly one-note-at-a-time parts look like a melody. */
	static double melodyScore(List<Note> notes)
	{
		if (notes.size() < 4)
		{
			return 0;
		}
		Map<Long, Integer> perTick = new HashMap<>();
		double pitchSum = 0;
		for (Note n : notes)
		{
			perTick.merge(n.tick, 1, Integer::sum);
			pitchSum += n.pitch;
		}
		double chordiness = 1 - perTick.size() / (double) notes.size();
		double meanPitch = pitchSum / notes.size();
		// Tunes sit in the middle-high register; very high parts are usually sparkle or trills.
		double register = Math.max(0.2, 1 - Math.abs(meanPitch - 74) / 30.0);
		// Tunes mostly move by step; arpeggios and accompaniment jump around in thirds and fourths.
		// Repeating one note over and over is rhythm, not a tune, so repeats count for little.
		List<Note> line = topLine(notes);
		double steps = 0;
		java.util.Set<Integer> pitches = new java.util.HashSet<>();
		for (int i = 0; i < line.size(); i++)
		{
			pitches.add(line.get(i).pitch);
			if (i == 0)
			{
				continue;
			}
			int jump = Math.abs(line.get(i).pitch - line.get(i - 1).pitch);
			steps += jump == 0 ? 0.25 : jump <= 2 ? 1 : 0;
		}
		double stepwise = line.size() < 2 ? 0 : steps / (line.size() - 1);
		double variety = Math.min(1, pitches.size() / 6.0);
		return Math.sqrt(perTick.size()) * (1 - 0.8 * chordiness) * register * variety * (0.15 + stepwise);
	}

	/** General MIDI programs: lead instruments carry tunes, harps and pads accompany, percussion never. */
	static double programWeight(int program)
	{
		if (program >= 112 || program == 47 || program == 55)
		{
			return 0; // percussive, sound effects, timpani, orchestra hit
		}
		if ((program >= 40 && program <= 41) || (program >= 52 && program <= 54) || (program >= 56 && program <= 61)
			|| (program >= 64 && program <= 79) || program == 22 || program == 23 || program == 105 || program == 109
			|| program == 110 || program == 111 || (program >= 80 && program <= 81))
		{
			return 1.4; // violin, voices, brass, reeds, pipes, harmonica, bagpipe, fiddle, leads
		}
		if (program == 46 || (program >= 8 && program <= 15) || (program >= 24 && program <= 31) || program == 45
			|| program == 104 || program == 106 || program == 107)
		{
			return 0.6; // harp, mallets and bells, guitars, pizzicato, plucked
		}
		if ((program >= 32 && program <= 39) || (program >= 88 && program <= 95) || (program >= 48 && program <= 51)
			|| program == 42 || program == 43)
		{
			return 0.35; // basses, pads, string ensembles, cello, contrabass
		}
		return 1.0;
	}

	private static final String[] FAMILIES = {
		"piano", "chromatic percussion", "organ", "guitar", "bass", "strings", "ensemble", "brass",
		"reed", "pipe", "synth lead", "synth pad", "synth effects", "ethnic", "percussive", "sound effects",
	};

	private static String instrumentName(int program)
	{
		switch (program)
		{
			case 40: return "violin";
			case 46: return "harp";
			case 56: return "trumpet";
			case 60: return "french horn";
			case 68: return "oboe";
			case 69: return "english horn";
			case 71: return "clarinet";
			case 72: return "piccolo";
			case 73: return "flute";
			case 74: return "recorder";
			case 75: return "pan flute";
			case 79: return "ocarina";
			case 105: return "banjo";
			case 109: return "bagpipe";
			case 110: return "fiddle";
			default: return FAMILIES[Math.max(0, Math.min(127, program)) / 8];
		}
	}

	/** Keeps the highest note wherever several start together. */
	static List<Note> topLine(List<Note> notes)
	{
		TreeMap<Long, Integer> top = new TreeMap<>();
		for (Note n : notes)
		{
			top.merge(n.tick, n.pitch, Math::max);
		}
		List<Note> out = new ArrayList<>();
		top.forEach((tick, pitch) -> out.add(new Note(tick, pitch)));
		return out;
	}

	/** The largest grid (in beats) the melody fits, so the song plays as briskly as the rhythm allows. */
	static double pickGrid(List<Note> melody, double ticksPerBeat)
	{
		long first = melody.get(0).tick;
		for (double grid : GRIDS)
		{
			double slotTicks = grid * ticksPerBeat;
			int onGrid = 0;
			Map<Long, Integer> used = new HashMap<>();
			for (Note n : melody)
			{
				double pos = (n.tick - first) / slotTicks;
				long slot = Math.round(pos);
				if (Math.abs(pos - slot) <= 0.12)
				{
					onGrid++;
				}
				used.merge(slot, 1, Integer::sum);
			}
			int doubled = melody.size() - used.size();
			if (onGrid >= ON_GRID * melody.size() && doubled <= MAX_DROPPED * melody.size())
			{
				return grid;
			}
		}
		return GRIDS[GRIDS.length - 1];
	}

	/** Semitone shift that fits the most notes on the keyboard, preferring whole octaves and small moves. */
	static int pickShift(List<Integer> pitches)
	{
		int best = 0;
		long bestKey = Long.MIN_VALUE;
		for (int shift = -60; shift <= 60; shift++)
		{
			int fit = 0;
			for (int p : pitches)
			{
				if (p + shift >= LOW && p + shift <= HIGH)
				{
					fit++;
				}
			}
			// Rank: most notes fitting, then octave moves (keeps the key), then the smallest move.
			long key = (long) fit * 1_000_000 + (shift % 12 == 0 ? 10_000 : 0) - Math.abs(shift);
			if (key > bestKey)
			{
				bestKey = key;
				best = shift;
			}
		}
		return best;
	}
}
