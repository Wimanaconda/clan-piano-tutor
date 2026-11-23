package com.clanpiano.song;

import com.clanpiano.PianoKey;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the plain-text song format. Every token is one game tick (0.6s):
 *
 * <pre>
 * title: Ode to Joy
 * artist: Beethoven
 * transpose: 0          // optional, in semitones
 *
 * E5 E5 F5 G5 | G5 F5 E5 D5     // one note per tick; '|' is ignored
 * C5*2                          // C5, then hold for one more tick
 * -  -*3                        // rest one tick, rest three ticks
 * C4+E4+G4                      // chord: keys pressed on the same tick
 * </pre>
 *
 * Notes are a letter A-G, an optional '#' or 'b', and an octave number.
 * After transposing, every note must land between C4 and B5.
 * Text after '//' on a line is a comment.
 */
public final class SongParser
{
	private static final Pattern HEADER = Pattern.compile("^\\s*([A-Za-z]+)\\s*:\\s*(.*)$");
	private static final Pattern NOTE = Pattern.compile("^([A-Ga-g])([#b]?)(-?\\d)$");
	private static final Pattern DURATION = Pattern.compile("^(.*?)\\*(\\d+)$");
	private static final int MAX_DURATION = 64;

	private SongParser()
	{
	}

	public static Song parse(String text, String fallbackTitle, String source)
	{
		String title = fallbackTitle;
		String artist = "";
		int transpose = 0;
		List<String[]> bodyTokens = new ArrayList<>(); // {token, line number}

		String[] lines = text.split("\\r?\\n", -1);
		for (int i = 0; i < lines.length; i++)
		{
			int lineNo = i + 1;
			String line = stripComment(lines[i]);
			if (line.trim().isEmpty())
			{
				continue;
			}

			Matcher header = HEADER.matcher(line);
			if (header.matches())
			{
				String name = header.group(1).toLowerCase(Locale.ROOT);
				String value = header.group(2).trim();
				switch (name)
				{
					case "title":
						title = value;
						break;
					case "artist":
					case "composer":
					case "by":
						artist = value;
						break;
					case "transpose":
						try
						{
							transpose = Integer.parseInt(value);
						}
						catch (NumberFormatException e)
						{
							throw error(lineNo, "transpose must be a whole number of semitones, got '" + value + "'");
						}
						break;
					default:
						// Unknown headers are kept for forward compatibility and ignored.
						break;
				}
				continue;
			}

			for (String token : line.trim().split("\\s+"))
			{
				if (!token.isEmpty() && !token.equals("|"))
				{
					bodyTokens.add(new String[]{token, String.valueOf(lineNo)});
				}
			}
		}

		List<Step> steps = new ArrayList<>();
		for (String[] entry : bodyTokens)
		{
			parseToken(entry[0], Integer.parseInt(entry[1]), transpose, steps);
		}

		// Trailing rests add nothing but dead time.
		while (!steps.isEmpty() && steps.get(steps.size() - 1).isRest())
		{
			steps.remove(steps.size() - 1);
		}
		if (steps.isEmpty())
		{
			throw new IllegalArgumentException("song '" + title + "' has no notes");
		}
		return new Song(title, artist, source, steps);
	}

	private static void parseToken(String token, int lineNo, int transpose, List<Step> steps)
	{
		String body = token;
		int duration = 1;
		Matcher dm = DURATION.matcher(token);
		if (dm.matches())
		{
			body = dm.group(1);
			duration = Integer.parseInt(dm.group(2));
			if (duration < 1 || duration > MAX_DURATION)
			{
				throw error(lineNo, "duration in '" + token + "' must be 1 to " + MAX_DURATION);
			}
		}

		Step step;
		if (body.equals("-") || body.equals("."))
		{
			step = Step.REST;
		}
		else
		{
			Set<PianoKey> keys = EnumSet.noneOf(PianoKey.class);
			for (String part : body.split("\\+"))
			{
				keys.add(parseNote(part, lineNo, transpose));
			}
			step = new Step(keys);
		}

		steps.add(step);
		for (int i = 1; i < duration; i++)
		{
			steps.add(Step.REST);
		}
	}

	static PianoKey parseNote(String text, int lineNo, int transpose)
	{
		Matcher m = NOTE.matcher(text);
		if (!m.matches())
		{
			throw error(lineNo, "'" + text + "' is not a note (expected something like C4, F#5 or Bb4)");
		}
		int pitchClass = "C D EF G A B".indexOf(Character.toUpperCase(m.group(1).charAt(0)));
		int accidental = m.group(2).equals("#") ? 1 : m.group(2).equals("b") ? -1 : 0;
		int octave = Integer.parseInt(m.group(3));
		int midi = (octave + 1) * 12 + pitchClass + accidental + transpose;
		PianoKey key = PianoKey.fromMidi(midi);
		if (key == null)
		{
			throw error(lineNo, "'" + text + "'" + (transpose != 0 ? " transposed by " + transpose : "")
				+ " is outside the piano's range (C4 to B5)");
		}
		return key;
	}

	private static String stripComment(String line)
	{
		int idx = line.indexOf("//");
		return idx >= 0 ? line.substring(0, idx) : line;
	}

	private static IllegalArgumentException error(int lineNo, String message)
	{
		return new IllegalArgumentException("line " + lineNo + ": " + message);
	}
}
