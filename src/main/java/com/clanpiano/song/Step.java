package com.clanpiano.song;

import com.clanpiano.PianoKey;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** What to play on one tick: nothing (a rest), one key, or a chord. */
public final class Step
{
	public static final Step REST = new Step(EnumSet.noneOf(PianoKey.class));

	private final Set<PianoKey> keys;

	public Step(Set<PianoKey> keys)
	{
		this.keys = Collections.unmodifiableSet(keys.isEmpty() ? EnumSet.noneOf(PianoKey.class) : EnumSet.copyOf(keys));
	}

	public Set<PianoKey> getKeys()
	{
		return keys;
	}

	public boolean isRest()
	{
		return keys.isEmpty();
	}

	public String label()
	{
		if (keys.isEmpty())
		{
			return "-";
		}
		return keys.stream().map(PianoKey::displayName).collect(Collectors.joining("+"));
	}

	@Override
	public String toString()
	{
		return label();
	}
}
