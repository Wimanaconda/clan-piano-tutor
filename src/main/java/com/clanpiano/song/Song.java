package com.clanpiano.song;

import com.clanpiano.PianoKey;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * A song quantised to game ticks: step i is played on tick i.
 * One tick is 0.6 seconds, so 100 steps last one minute.
 */
@Getter
public final class Song
{
	public enum Difficulty
	{
		EASY("Easy"),
		MEDIUM("Medium"),
		HARD("Hard");

		private final String label;

		Difficulty(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final String title;
	private final String artist;
	private final String source; // "built-in" or the file it came from
	private final List<Step> steps;
	/** The song selector folder this song is listed under. */
	private final String folder;
	/** For MIDI songs: which instrument part the melody was taken from, else empty. */
	private final String part;
	/** For MIDI songs: other parts that might be the tune, to switch to by ear. */
	private final List<Song> alternatives;

	public Song(String title, String artist, String source, List<Step> steps)
	{
		this(title, artist, source, steps, "", "", Collections.emptyList());
	}

	private Song(String title, String artist, String source, List<Step> steps, String folder, String part, List<Song> alternatives)
	{
		this.title = title;
		this.artist = artist;
		this.source = source;
		this.steps = Collections.unmodifiableList(steps);
		this.folder = folder;
		this.part = part;
		this.alternatives = Collections.unmodifiableList(alternatives);
	}

	public Song inFolder(String folder)
	{
		List<Song> alts = new java.util.ArrayList<>();
		alternatives.forEach(a -> alts.add(a.inFolder(folder)));
		return new Song(title, artist, source, steps, folder, part, alts);
	}

	public Song withPart(String part)
	{
		return new Song(title, artist, source, steps, folder, part, alternatives);
	}

	public Song withAlternatives(List<Song> alternatives)
	{
		return new Song(title, artist, source, steps, folder, part, alternatives);
	}

	/** This song and its alternatives: every melody version to choose from. */
	public List<Song> versions()
	{
		List<Song> all = new java.util.ArrayList<>();
		all.add(this);
		all.addAll(alternatives);
		return all;
	}

	/** Stable name for remembering favourites and the last song: folder and title. */
	public String id()
	{
		return folder + "/" + title;
	}

	public PianoKey lowestKey()
	{
		PianoKey low = null;
		for (Step step : steps)
		{
			for (PianoKey key : step.getKeys())
			{
				if (low == null || key.ordinal() < low.ordinal())
				{
					low = key;
				}
			}
		}
		return low;
	}

	public PianoKey highestKey()
	{
		PianoKey high = null;
		for (Step step : steps)
		{
			for (PianoKey key : step.getKeys())
			{
				if (high == null || key.ordinal() > high.ordinal())
				{
					high = key;
				}
			}
		}
		return high;
	}

	/**
	 * A rough guide from how busy the song is: notes on most ticks, sharps, big jumps
	 * across the keyboard and sheer length each make it harder.
	 */
	public Difficulty difficulty()
	{
		int notes = 0;
		int sharps = 0;
		int jumpTotal = 0;
		int jumps = 0;
		int previous = -1;
		for (Step step : steps)
		{
			if (step.isRest())
			{
				continue;
			}
			notes++;
			PianoKey first = step.getKeys().iterator().next();
			if (step.getKeys().stream().anyMatch(PianoKey::isBlack))
			{
				sharps++;
			}
			if (previous >= 0)
			{
				jumpTotal += Math.abs(first.ordinal() - previous);
				jumps++;
			}
			previous = first.ordinal();
		}
		if (notes == 0)
		{
			return Difficulty.EASY;
		}
		int score = 0;
		if (notes / (double) steps.size() > 0.75)
		{
			score++;
		}
		if (sharps / (double) notes > 0.12)
		{
			score++;
		}
		if (jumps > 0 && jumpTotal / (double) jumps > 2.6)
		{
			score++;
		}
		if (notes > 70)
		{
			score++;
		}
		return score <= 1 ? Difficulty.EASY : score == 2 ? Difficulty.MEDIUM : Difficulty.HARD;
	}

	/** Length as m:ss at one step per tick. */
	public String duration()
	{
		int total = (int) Math.round(seconds());
		return total / 60 + ":" + String.format("%02d", total % 60);
	}

	public int length()
	{
		return steps.size();
	}

	public int noteCount()
	{
		int count = 0;
		for (Step step : steps)
		{
			if (!step.isRest())
			{
				count++;
			}
		}
		return count;
	}

	/** Length in seconds when played at one step per tick. */
	public double seconds()
	{
		return steps.size() * 0.6;
	}

	@Override
	public String toString()
	{
		return artist == null || artist.isEmpty() ? title : title + " - " + artist;
	}
}
