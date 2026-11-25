package com.clanpiano;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.clanpiano.song.Song;
import com.clanpiano.song.SongLibrary;
import com.clanpiano.song.SongParser;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

public class SongParserTest
{
	@Test
	public void parsesHeadersNotesRestsDurationsAndChords()
	{
		Song song = SongParser.parse(String.join("\n",
			"title: Test Song",
			"artist: Someone",
			"// a comment line",
			"C4 D#4 | Eb5*3 - // trailing comment",
			"C4+E4+G4 -*2 B5"), "fallback", "test");

		assertEquals("Test Song", song.getTitle());
		assertEquals("Someone", song.getArtist());
		// C4, D#4, Eb5 + 2 holds, rest, chord, 2 rests, B5
		assertEquals(10, song.length());
		assertEquals(5, song.noteCount());
		assertEquals(EnumSet.of(PianoKey.C4), song.getSteps().get(0).getKeys());
		assertEquals(EnumSet.of(PianoKey.DS4), song.getSteps().get(1).getKeys());
		assertEquals(EnumSet.of(PianoKey.DS5), song.getSteps().get(2).getKeys());
		assertTrue(song.getSteps().get(3).isRest());
		assertTrue(song.getSteps().get(5).isRest());
		assertEquals(EnumSet.of(PianoKey.C4, PianoKey.E4, PianoKey.G4), song.getSteps().get(6).getKeys());
		assertEquals(EnumSet.of(PianoKey.B5), song.getSteps().get(9).getKeys());
	}

	@Test
	public void transposeShiftsNotes()
	{
		Song song = SongParser.parse("transpose: 12\nC3 B4", "t", "test");
		assertEquals(PianoKey.C4, song.getSteps().get(0).getKeys().iterator().next());
		assertEquals(PianoKey.B5, song.getSteps().get(1).getKeys().iterator().next());
	}

	@Test
	public void fallbackTitleAndTrailingRestsTrimmed()
	{
		Song song = SongParser.parse("C4 - - -", "My File", "test");
		assertEquals("My File", song.getTitle());
		assertEquals(1, song.length());
	}

	@Test
	public void rejectsNotesOutsideTheKeyboard()
	{
		expectError("C4 B3", "line 1");
		expectError("C6", "outside");
		expectError("H4", "not a note");
		expectError("C4*0", "duration");
		expectError("- -", "no notes");
	}

	@Test
	public void everyBuiltInSongLoads()
	{
		SongLibrary library = new SongLibrary(null);
		library.reload();
		assertEquals("errors: " + library.getErrors(), 0, library.getErrors().size());
		assertTrue(library.getSongs().size() >= 10);
		for (Song song : library.getSongs())
		{
			assertFalse(song.getTitle(), song.getTitle().isEmpty());
			assertTrue(song.getTitle(), song.noteCount() > 8);
		}
	}

	@Test
	public void builtInSongsAreFiledInFolders()
	{
		SongLibrary library = new SongLibrary(null);
		library.reload();
		assertEquals(java.util.Arrays.asList("Classical", "Folk & Shanties", "Holidays & Parties", "Nursery Rhymes"),
			library.getFolders());
		for (Song song : library.getSongs())
		{
			assertTrue(song.getTitle(), library.getFolders().contains(song.getFolder()));
			assertTrue(song.getTitle(), song.duration().matches("[0-9]+:[0-9][0-9]"));
			assertTrue(song.getTitle(), song.lowestKey().ordinal() <= song.highestKey().ordinal());
		}
	}

	@Test
	public void customSubfoldersBecomeFoldersEvenWhenEmpty() throws Exception
	{
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("piano-songs");
		java.nio.file.Files.createDirectories(dir.resolve("RuneScape"));
		java.nio.file.Files.createDirectories(dir.resolve("Clan"));
		java.nio.file.Files.write(dir.resolve("Clan").resolve("anthem.txt"), "title: Anthem\nC4 D4 E4".getBytes());
		java.nio.file.Files.write(dir.resolve("loose.txt"), "C4 E4 G4".getBytes());

		SongLibrary library = new SongLibrary(net.runelite.client.util.Filepath.Unchecked.getRooted(dir));
		library.reload();
		List<String> folders = library.getFolders();
		assertEquals(java.util.Arrays.asList(SongLibrary.MY_SONGS, "Clan", "RuneScape"),
			folders.subList(folders.size() - 3, folders.size()));
		assertTrue(library.getCustomFolders().contains("RuneScape"));
		assertEquals(dir.resolve("RuneScape").toString(), library.pathOf("RuneScape").toString());
		Song anthem = library.getSongs().stream().filter(x -> x.getTitle().equals("Anthem")).findFirst().get();
		assertEquals("Clan", anthem.getFolder());
		assertEquals("Clan/Anthem", anthem.id());
	}

	@Test
	public void difficultyGrowsWithBusySongs()
	{
		assertEquals(Song.Difficulty.EASY, SongParser.parse("C4*2 D4*2 E4*2 C4*2", "t", "test").difficulty());
		assertEquals(Song.Difficulty.HARD, SongParser.parse("C4 B5 C#4 A#5 D4 G#5 F#4 B5 C4 A#5", "t", "test").difficulty());
	}

	@Test
	public void keyMappingCoversAllWidgets()
	{
		assertEquals(24, PianoKey.values().length);
		assertEquals(PianoKey.C4, PianoKey.fromMidi(60));
		assertEquals(PianoKey.B5, PianoKey.fromMidi(83));
		long distinct = java.util.Arrays.stream(PianoKey.values()).mapToInt(PianoKey::getWidgetId).distinct().count();
		assertEquals(24, distinct);
		for (PianoKey key : PianoKey.values())
		{
			assertEquals(key, PianoKey.fromWidgetId(key.getWidgetId()));
			assertEquals(key, PianoKey.fromWidgetId(key.getLabelWidgetId()));
		}
	}

	private static void expectError(String body, String fragment)
	{
		try
		{
			SongParser.parse(body, "t", "test");
			fail("expected an error for: " + body);
		}
		catch (IllegalArgumentException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains(fragment));
		}
	}
}
