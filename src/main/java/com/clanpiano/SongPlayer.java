package com.clanpiano;

import com.clanpiano.song.Song;
import com.clanpiano.song.Step;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;

/**
 * Tracks where the player is in a song. Holds no RuneLite state so it can be unit tested.
 *
 * <ul>
 * <li>{@link PlayMode#FOLLOW}: waits for a press, then moves on, even after a wrong key.
 * Rests are skipped.</li>
 * <li>{@link PlayMode#TEMPO}: the song moves one step every game tick, the timing the hall
 * hears. {@code position} is the step whose tick it is now; {@code cursor} is the next note
 * still to be played. A note opens for pressing {@code lead} ticks before its own tick, so its
 * key can light up early and there is time to click it. Once pressed (right or wrong) the
 * next note opens as soon as it is inside its lead window. A note whose tick ends before it
 * was played is a miss.</li>
 * </ul>
 */
public class SongPlayer
{
	public enum State
	{
		IDLE,
		COUNT_IN,
		PLAYING,
		FINISHED
	}

	public enum PressResult
	{
		/** Nothing is playing, or nothing is open yet. */
		IGNORED,
		/** Right key; the note is complete. */
		HIT,
		/** Right key, but other keys of the chord are still needed. */
		PARTIAL,
		/** A key that is not the note to play. */
		WRONG
	}

	@Getter
	private Song song;
	@Getter
	private PlayMode mode = PlayMode.FOLLOW;
	@Getter
	private State state = State.IDLE;
	/** The step that is due on this tick (tempo), or the step being waited for (follow). */
	@Getter
	private int position;
	/** The next note still to be played. */
	@Getter
	private int cursor;
	@Getter
	private int countIn;
	@Getter
	private int lead;
	@Getter
	private int hits;
	@Getter
	private int misses;
	@Getter
	private int wrong;

	private boolean loop;
	private final Set<PianoKey> pressed = EnumSet.noneOf(PianoKey.class);

	public void load(Song song)
	{
		this.song = song;
		stop();
	}

	public void start(PlayMode mode, int countInTicks, boolean loop)
	{
		start(mode, countInTicks, loop, 0);
	}

	public void start(PlayMode mode, int countInTicks, boolean loop, int leadTicks)
	{
		if (song == null)
		{
			return;
		}
		reset(mode, loop, leadTicks);
		if (mode == PlayMode.TEMPO && countInTicks > 0)
		{
			countIn = countInTicks;
			state = State.COUNT_IN;
			cursor = firstNoteFrom(0);
		}
		else
		{
			state = State.PLAYING;
			if (mode == PlayMode.FOLLOW)
			{
				position = firstNoteFrom(0);
			}
			cursor = firstNoteFrom(position);
		}
	}

	public void startAtFirstNote(PlayMode mode, boolean loop)
	{
		startAtFirstNote(mode, loop, 0);
	}

	/** Starts right on the first note, with no count-in: used when the player just plays it. */
	public void startAtFirstNote(PlayMode mode, boolean loop, int leadTicks)
	{
		if (song == null)
		{
			return;
		}
		reset(mode, loop, leadTicks);
		state = State.PLAYING;
		position = firstNoteFrom(0);
		cursor = position;
	}

	private void reset(PlayMode mode, boolean loop, int leadTicks)
	{
		this.mode = mode;
		this.loop = loop;
		this.lead = mode == PlayMode.TEMPO ? Math.max(0, leadTicks) : 0;
		position = 0;
		cursor = 0;
		countIn = 0;
		hits = misses = wrong = 0;
		pressed.clear();
	}

	public void stop()
	{
		state = State.IDLE;
		position = 0;
		cursor = 0;
		countIn = 0;
		pressed.clear();
	}

	public boolean isActive()
	{
		return state == State.COUNT_IN || state == State.PLAYING;
	}

	/** True when the note at {@link #getCursor()} can be played now (its key is lit). */
	public boolean isTargetOpen()
	{
		if (!isActive() || song == null || cursor >= song.length())
		{
			return false;
		}
		if (mode == PlayMode.FOLLOW)
		{
			return state == State.PLAYING;
		}
		return cursor - dueBase() <= lead;
	}

	/** The note to play now, or null when nothing is open. */
	public Step current()
	{
		return isTargetOpen() ? song.getSteps().get(cursor) : null;
	}

	/** Index of the song's first note (leading rests skipped), or -1 if there is no song. */
	public int firstNoteIndex()
	{
		if (song == null)
		{
			return -1;
		}
		int i = firstNoteFrom(0);
		return i < song.length() ? i : -1;
	}

	/** The song's first note, which also starts the song when played. */
	public Step firstNote()
	{
		int i = firstNoteIndex();
		return i < 0 ? null : song.getSteps().get(i);
	}

	/**
	 * The step index that is "due now" for drawing the timeline: step {@code i} is due
	 * {@code i - dueBase()} ticks from now. Before a start it is the first note; during a
	 * count-in it lies in the future.
	 */
	public int dueBase()
	{
		switch (state)
		{
			case COUNT_IN:
				return -countIn;
			case PLAYING:
				return position;
			default:
				return Math.max(0, firstNoteIndex());
		}
	}

	/** Keys of the open note that still need pressing. */
	public Set<PianoKey> remaining()
	{
		Step step = current();
		if (step == null)
		{
			return Collections.emptySet();
		}
		Set<PianoKey> left = EnumSet.noneOf(PianoKey.class);
		left.addAll(step.getKeys());
		left.removeAll(pressed);
		return left;
	}

	/**
	 * The next {@code count} notes after the one to play now. In follow mode rests are
	 * left out; in tempo mode they are kept so the timing is visible.
	 */
	public List<Step> upcoming(int count)
	{
		List<Step> out = new ArrayList<>();
		if (song == null || !isActive())
		{
			return out;
		}
		int start = isTargetOpen() ? cursor + 1 : cursor;
		for (int i = start; i < song.length() && out.size() < count; i++)
		{
			Step step = song.getSteps().get(i);
			if (mode == PlayMode.FOLLOW && step.isRest())
			{
				continue;
			}
			out.add(step);
		}
		return out;
	}

	public PressResult press(PianoKey key)
	{
		Step step = current();
		if (step == null)
		{
			if (state == State.PLAYING && mode == PlayMode.TEMPO)
			{
				// Nothing lit: a stray or early key.
				wrong++;
				return PressResult.WRONG;
			}
			return PressResult.IGNORED;
		}
		if (!step.getKeys().contains(key))
		{
			// Don't get stuck on a note: count it and show the next one anyway.
			wrong++;
			misses++;
			nextNote();
			return PressResult.WRONG;
		}
		if (!pressed.add(key) || !remaining().isEmpty())
		{
			return PressResult.PARTIAL;
		}
		hits++;
		nextNote();
		return PressResult.HIT;
	}

	/** Called once per game tick. Only tempo mode moves with the clock. */
	public void tick()
	{
		if (mode != PlayMode.TEMPO)
		{
			return;
		}
		if (state == State.COUNT_IN)
		{
			if (--countIn <= 0)
			{
				state = State.PLAYING;
			}
			return;
		}
		if (state != State.PLAYING)
		{
			return;
		}
		position++;
		if (cursor < position)
		{
			// The note's own tick is over and it was not (fully) played.
			if (cursor < song.length() && !song.getSteps().get(cursor).isRest())
			{
				misses++;
			}
			pressed.clear();
			cursor = firstNoteFrom(position);
		}
		if (position >= song.length())
		{
			endOfSong();
		}
	}

	private void nextNote()
	{
		pressed.clear();
		cursor = firstNoteFrom(cursor + 1);
		if (mode == PlayMode.FOLLOW)
		{
			position = cursor;
			if (position >= song.length())
			{
				endOfSong();
			}
		}
	}

	private void endOfSong()
	{
		if (loop)
		{
			position = mode == PlayMode.FOLLOW ? firstNoteFrom(0) : 0;
			cursor = firstNoteFrom(position);
			pressed.clear();
		}
		else
		{
			state = State.FINISHED;
		}
	}

	private int firstNoteFrom(int index)
	{
		int i = index;
		while (song != null && i < song.length() && song.getSteps().get(i).isRest())
		{
			i++;
		}
		return i;
	}
}
