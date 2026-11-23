package com.clanpiano;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.clanpiano.song.Song;
import com.clanpiano.song.SongParser;
import java.util.EnumSet;
import org.junit.Test;

public class SongPlayerTest
{
	private static final Song SONG = SongParser.parse("C4 - E4+G4 D4*2 F4", "t", "test");

	@Test
	public void followModeWaitsForKeysSkipsRestsAndMovesOnAfterAWrongKey()
	{
		SongPlayer p = new SongPlayer();
		p.load(SONG);
		p.start(PlayMode.FOLLOW, 4, false);

		assertEquals(SongPlayer.State.PLAYING, p.getState());
		assertEquals(EnumSet.of(PianoKey.C4), p.remaining());
		p.tick(); // the clock does not move follow mode
		assertEquals(0, p.getPosition());

		assertEquals(SongPlayer.PressResult.WRONG, p.press(PianoKey.D4));
		assertEquals(2, p.getPosition()); // moved on anyway, rest skipped
		assertEquals(SongPlayer.PressResult.PARTIAL, p.press(PianoKey.G4));
		assertEquals(EnumSet.of(PianoKey.E4), p.remaining());
		assertEquals(SongPlayer.PressResult.HIT, p.press(PianoKey.E4));
		assertEquals(SongPlayer.PressResult.HIT, p.press(PianoKey.D4));
		assertEquals(5, p.getPosition()); // D4's hold skipped
		assertEquals(SongPlayer.PressResult.HIT, p.press(PianoKey.F4));

		assertEquals(SongPlayer.State.FINISHED, p.getState());
		assertEquals(3, p.getHits());
		assertEquals(1, p.getWrong());
		assertEquals(1, p.getMisses());
		assertNull(p.current());
	}

	@Test
	public void tempoModeWrongKeyDarkensTheStepUntilTheNextTick()
	{
		SongPlayer p = new SongPlayer();
		p.load(SONG);
		p.start(PlayMode.TEMPO, 0, false);

		assertEquals(SongPlayer.PressResult.WRONG, p.press(PianoKey.D4));
		assertTrue(p.remaining().isEmpty()); // box goes dark, no second try this tick
		assertEquals(SongPlayer.PressResult.WRONG, p.press(PianoKey.C4));
		assertEquals(0, p.getPosition());
		p.tick();
		p.tick();
		assertEquals(EnumSet.of(PianoKey.E4, PianoKey.G4), p.remaining());
		assertEquals(1, p.getMisses());
		assertEquals(2, p.getWrong());
	}

	@Test
	public void playingTheFirstNoteStartsWithoutCountIn()
	{
		SongPlayer p = new SongPlayer();
		p.load(SongParser.parse("- - C4 D4", "t", "test"));
		assertEquals(2, p.firstNoteIndex());
		assertEquals(2, p.dueBase()); // idle: the first note sits on the line

		p.startAtFirstNote(PlayMode.TEMPO, false);
		assertEquals(2, p.getPosition());
		assertEquals(SongPlayer.PressResult.HIT, p.press(PianoKey.C4));
		assertTrue(p.remaining().isEmpty());
		p.tick();
		assertEquals(EnumSet.of(PianoKey.D4), p.remaining());

		p.start(PlayMode.TEMPO, 3, false);
		assertEquals(-3, p.dueBase()); // first step lands after the count-in
	}

	@Test
	public void tempoModeAdvancesEveryTickAndCountsMisses()
	{
		SongPlayer p = new SongPlayer();
		p.load(SONG);
		p.start(PlayMode.TEMPO, 2, false);

		assertEquals(SongPlayer.State.COUNT_IN, p.getState());
		assertEquals("C", p.upcoming(1).get(0).label());
		p.tick();
		p.tick();
		assertEquals(SongPlayer.State.PLAYING, p.getState());

		p.press(PianoKey.C4); // hit, stays on this step until the tick
		assertEquals(0, p.getPosition());
		p.tick(); // -> rest
		p.tick(); // -> chord
		p.press(PianoKey.E4); // only half the chord
		p.tick(); // miss -> D4
		p.tick(); // miss -> hold
		p.tick(); // -> F4
		p.press(PianoKey.F4);
		p.tick();

		assertEquals(SongPlayer.State.FINISHED, p.getState());
		assertEquals(2, p.getHits());
		assertEquals(2, p.getMisses());
	}

	@Test
	public void leadWindowLightsTheNextNoteEarly()
	{
		SongPlayer p = new SongPlayer();
		p.load(SongParser.parse("C4 D4 - E4", "t", "test"));
		p.startAtFirstNote(PlayMode.TEMPO, false, 1);

		assertEquals(EnumSet.of(PianoKey.C4), p.remaining());
		assertEquals(SongPlayer.PressResult.HIT, p.press(PianoKey.C4));
		assertEquals(EnumSet.of(PianoKey.D4), p.remaining()); // one tick early
		assertEquals(SongPlayer.PressResult.HIT, p.press(PianoKey.D4));
		assertTrue(p.remaining().isEmpty()); // E4 is three ticks away
		p.tick();
		p.tick();
		assertEquals(EnumSet.of(PianoKey.E4), p.remaining()); // lit during the rest
		p.tick();
		p.tick(); // E4's own tick passes unplayed
		assertEquals(SongPlayer.State.FINISHED, p.getState());
		assertEquals(2, p.getHits());
		assertEquals(1, p.getMisses());
	}

	@Test
	public void leadWindowOpensDuringTheCountIn()
	{
		SongPlayer p = new SongPlayer();
		p.load(SongParser.parse("C4 D4", "t", "test"));
		p.start(PlayMode.TEMPO, 3, false, 1);
		assertTrue(p.remaining().isEmpty());
		p.tick();
		p.tick();
		assertEquals(EnumSet.of(PianoKey.C4), p.remaining());
		assertEquals(SongPlayer.PressResult.HIT, p.press(PianoKey.C4));
	}

	@Test
	public void loopRestartsFromTheTop()
	{
		SongPlayer p = new SongPlayer();
		p.load(SongParser.parse("C4 D4", "t", "test"));
		p.start(PlayMode.FOLLOW, 0, true);
		p.press(PianoKey.C4);
		p.press(PianoKey.D4);
		assertEquals(SongPlayer.State.PLAYING, p.getState());
		assertEquals(0, p.getPosition());
	}
}
