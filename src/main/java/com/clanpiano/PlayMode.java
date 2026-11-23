package com.clanpiano;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum PlayMode
{
	FOLLOW("Practice (waits for you)"),
	TEMPO("Timed (one note per tick)");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
