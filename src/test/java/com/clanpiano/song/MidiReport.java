package com.clanpiano.song;

import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

/** Dev tool: shows how each MIDI in a folder is turned into a piano song. ./gradlew midiReport */
public final class MidiReport
{
	private static double score(List<MidiImporter.Note> notes, int program)
	{
		return MidiImporter.melodyScore(notes) * MidiImporter.programWeight(program);
	}

	public static void main(String[] args) throws Exception
	{
		Path dir = Paths.get(args[0]);
		List<Path> files;
		try (java.util.stream.Stream<Path> s = Files.list(dir))
		{
			files = s.filter(p -> p.toString().toLowerCase().endsWith(".mid")).sorted().collect(Collectors.toList());
		}
		for (Path f : files)
		{
			Sequence seq;
			try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(f)))
			{
				seq = MidiSystem.getSequence(in);
			}
			System.out.println("== " + f.getFileName() + "  (ppq " + seq.getResolution() + ", " + seq.getTracks().length + " tracks)");
			Map<String, List<MidiImporter.Note>> parts = new TreeMap<>();
			Map<String, Integer> programs = new TreeMap<>();
			Track[] tracks = seq.getTracks();
			for (int t = 0; t < tracks.length; t++)
			{
				for (int i = 0; i < tracks[t].size(); i++)
				{
					MidiEvent e = tracks[t].get(i);
					MidiMessage m = e.getMessage();
					if (!(m instanceof ShortMessage))
					{
						continue;
					}
					ShortMessage sm = (ShortMessage) m;
					String key = t + ":" + sm.getChannel();
					if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE)
					{
						programs.putIfAbsent(key, sm.getData1());
					}
					if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0 && sm.getChannel() != 9)
					{
						parts.computeIfAbsent(key, k -> new ArrayList<>()).add(new MidiImporter.Note(e.getTick(), sm.getData1()));
					}
				}
			}
			parts.entrySet().stream()
				.sorted((a, b) -> Double.compare(score(b.getValue(), programs.getOrDefault(b.getKey(), 0)), score(a.getValue(), programs.getOrDefault(a.getKey(), 0))))
				.limit(0)
				.forEach(e -> System.out.printf("   part %-5s prog %-3s notes %4d  mean pitch %5.1f  score %6.1f%n",
					e.getKey(), programs.getOrDefault(e.getKey(), -1), e.getValue().size(),
					e.getValue().stream().mapToInt(n -> n.pitch).average().orElse(0),
					score(e.getValue(), programs.getOrDefault(e.getKey(), 0))));
			try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(f)))
			{
				Song main = MidiImporter.load(in, f.getFileName().toString(), f.getFileName().toString());
				for (Song song : main.versions())
				{
					System.out.println("   -> [" + song.getPart() + "] " + song.noteCount() + " notes, " + song.duration()
						+ ", " + song.difficulty() + ", keys " + song.lowestKey().displayName() + " to " + song.highestKey().displayName());
					System.out.println("      " + song.getSteps().stream().limit(40).map(Step::label).collect(Collectors.joining(" ")));
				}
			}
			catch (RuntimeException ex)
			{
				System.out.println("   !! " + ex.getMessage());
			}
		}
	}
}
