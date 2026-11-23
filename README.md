# Clan Piano Tutor

Learn songs on the Clan Hall piano. Pick a song in the side panel, open the piano, and the plugin
lights up the keys to press in green. Falling notes above the keys show what is coming and when to
press.

## Features

- **Green keys.** The keys to press now are filled green, a little before they are due, so you
  have time to click. The next note can be outlined too.
- **Falling notes.** Notes drop toward a line above the keys, one row per game tick, and turn green
  when it is time to press. Played notes go grey. A count-in starts each song.
- **Two modes.**
  - *Timed* (default): the song moves on one step per game tick (0.6s), the timing the rest of
    the hall hears. Every press moves on to the next note, right or wrong, and notes you let pass
    count as misses.
  - *Practice*: the song waits for you to press a key before moving on.
- **Start by playing.** Press the first (green) key of a song to start it.
- **Song selector.** Folders, search, favourites (star), difficulty, length and key range, and a
  small preview of the melody.
- **Your own songs.** Drop `.txt` songs or `.mid` / `.midi` files in your song folder (the panel's
  *Open song folder* button opens it). Each subfolder becomes a folder in the selector. For a MIDI
  file the plugin picks the part that looks most like the tune and fits it to the piano. If it
  picked the wrong part, choose another one in the card's *Melody* box.
- **Keyboard too.** Presses on your keyboard count when they match the letter printed on the key
  (setting: *Count keyboard presses*).

## The piano

The Clan Hall piano has 24 keys, C4 to B5. The game plays one step per game tick (0.6s), and keys
pressed in the same tick sound together as a chord. Songs are written one step per tick, so the
fastest a melody can move is 100 notes a minute. MIDI files are fitted to that grid and moved by
octaves to fit the keys.

## Built-in songs

All public domain:

- **Classical:** Für Elise, Ode to Joy, Canon in D, Rondo alla Turca, Spring, Morning Mood, Eine
  kleine Nachtmusik, In the Hall of the Mountain King, The Entertainer
- **Folk & Shanties:** Korobeiniki, Drunken Sailor, Bella Ciao, Scarborough Fair, Greensleeves,
  Amazing Grace, When the Saints
- **Holidays & Parties:** Happy Birthday, Jingle Bells, Carol of the Bells, Auld Lang Syne
- **Nursery Rhymes:** Twinkle Twinkle, Mary Had a Little Lamb, Frère Jacques, Row Row Row Your Boat,
  Brahms' Lullaby

## Writing a song

Put a `.txt` file in `%USERPROFILE%\.runelite\clan-piano-songs` (or a subfolder) and press
*Reload*. Songs that fail to load show the line and the reason in the panel.

```
title: My Song
artist: Someone
transpose: 0           // optional, semitones; everything must end up between C4 and B5

E5 E5 F5 G5 | G5 F5 E5 D5   // one token = one tick; '|' is just for readability
C5*2                        // C5 then hold one more tick
- -*3                       // rest 1 tick, rest 3 ticks
C4+E4+G4                    // chord: press together in the same tick
F#4 Bb4                     // sharps and flats
```

## Building

Needs JDK 11 or newer.

```
./gradlew test     # unit tests (parser, player, MIDI import, every built-in song)
./gradlew run      # launches a dev RuneLite client with the plugin loaded
```

---

Created using intellectual property belonging to Jagex Limited under the terms of Jagex's Fan
Content Policy. This content is not endorsed by or affiliated with Jagex.
