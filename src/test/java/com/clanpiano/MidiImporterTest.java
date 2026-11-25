package com.clanpiano;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.clanpiano.song.MidiImporter;
import com.clanpiano.song.Song;
import com.clanpiano.song.SongLibrary;
import com.clanpiano.song.Step;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import org.junit.Test;

public class MidiImporterTest
{
	private static final int PPQ = 480;

	@Test
	public void picksTheMelodyOverBassAndDrumsAndKeepsItsRhythm() throws Exception
	{
		Sequence seq = new Sequence(Sequence.PPQ, PPQ);
		Track melody = named(seq, "Flute");
		// C5 D5 E5 (quarters), rest a beat, G5 (quarter): one beat = one tick
		note(melody, 0, 0, 72);
		note(melody, 0, 1, 74);
		note(melody, 0, 2, 76);
		note(melody, 0, 4, 79);
		note(melody, 0, 5, 76);
		Track bass = named(seq, "Bass");
		for (int beat = 0; beat < 6; beat += 2)
		{
			note(bass, 1, beat, 36); // long low notes
		}
		Track drums = named(seq, "Drums");
		for (int beat = 0; beat < 6; beat++)
		{
			note(drums, 9, beat, 38);
			note(drums, 9, beat + 0.5, 42);
		}

		Song song = MidiImporter.load(bytes(seq), "Test", "test.mid");
		assertEquals("C' D' E' - G' E'", labels(song));
		assertEquals("piano", song.getPart());
		assertEquals(1, song.versions().size()); // bass too short, drums never
	}

	@Test
	public void otherMelodicPartsAreKeptAsVersionsToSwitchTo() throws Exception
	{
		Sequence seq = sequence();
		Track flute = named(seq, "Flute");
		flute.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, 73, 0), 0));
		Track violin = named(seq, "Violin");
		violin.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 1, 40, 0), 0));
		int[] tune = {72, 74, 76, 77, 79, 77, 76, 74, 72, 71, 72, 74};
		int[] counter = {60, 62, 64, 65, 67, 65, 64, 62, 60, 59, 60, 62};
		for (int i = 0; i < tune.length; i++)
		{
			note(flute, 0, i, tune[i]);
			note(violin, 1, i, counter[i]);
		}
		Song song = MidiImporter.load(bytes(seq), "Duet", "duet.mid");
		assertEquals(2, song.versions().size());
		assertEquals("flute", song.getPart()); // higher register wins
		assertEquals("violin", song.getAlternatives().get(0).getPart());
		assertEquals("RuneScape", song.inFolder("RuneScape").getAlternatives().get(0).getFolder());
	}

	@Test
	public void eighthNotesBecomeOneTickEach()
	{
		Sequence seq = sequence();
		Track t = named(seq, "Lead");
		double[] beats = {0, 0.5, 1, 2, 2.5, 3};
		int[] pitches = {67, 69, 71, 72, 71, 69};
		for (int i = 0; i < beats.length; i++)
		{
			note(t, 0, beats[i], pitches[i]);
		}
		Song song = convert(seq);
		assertEquals("G A B - C' B A", labels(song));
	}

	@Test
	public void lowTunesMoveUpByOctavesToFitTheKeys()
	{
		Sequence seq = sequence();
		Track t = named(seq, "Cello");
		int[] pitches = {48, 52, 55, 60}; // C3 E3 G3 C4
		for (int i = 0; i < pitches.length; i++)
		{
			note(t, 0, i, pitches[i]);
		}
		assertEquals("C E G C'", labels(convert(seq)));
	}

	@Test
	public void midiFilesInSongFoldersLoad() throws Exception
	{
		Sequence seq = sequence();
		Track t = named(seq, "Lead");
		for (int i = 0; i < 8; i++)
		{
			note(t, 0, i, 60 + i);
		}
		Path dir = Files.createTempDirectory("piano-midi");
		Files.createDirectories(dir.resolve("RuneScape"));
		MidiSystem.write(seq, 1, dir.resolve("RuneScape").resolve("Scale Tune.mid").toFile());

		SongLibrary library = new SongLibrary(net.runelite.client.util.Filepath.Unchecked.getRooted(dir));
		library.reload();
		assertEquals("errors: " + library.getErrors(), 0, library.getErrors().size());
		Song song = library.getSongs().stream().filter(s -> s.getTitle().equals("Scale Tune")).findFirst().get();
		assertEquals("RuneScape", song.getFolder());
		assertEquals(8, song.noteCount());
	}

	// ---- helpers ----

	private static Sequence sequence()
	{
		try
		{
			return new Sequence(Sequence.PPQ, PPQ);
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static Song convert(Sequence seq)
	{
		try
		{
			return MidiImporter.load(bytes(seq), "t", "t.mid");
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static Track named(Sequence seq, String name)
	{
		try
		{
			Track t = seq.createTrack();
			byte[] data = name.getBytes();
			t.add(new MidiEvent(new MetaMessage(0x03, data, data.length), 0));
			return t;
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static void note(Track t, int channel, double beat, int pitch)
	{
		try
		{
			long on = Math.round(beat * PPQ);
			t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, channel, pitch, 90), on));
			t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0), on + PPQ / 4));
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static ByteArrayInputStream bytes(Sequence seq) throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		MidiSystem.write(seq, 1, out);
		return new ByteArrayInputStream(out.toByteArray());
	}

	private static String labels(Song song)
	{
		List<Step> steps = song.getSteps();
		return steps.stream().map(Step::label).collect(Collectors.joining(" "));
	}
}
