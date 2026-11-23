package com.clanpiano.song;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Built-in songs live in resources under /songs, listed in /songs/index.txt, where a line
 * like {@code [Classical]} starts a folder. Players can add their own .txt songs to a folder
 * in the RuneLite directory: loose files go under "My songs", and each subfolder becomes a
 * folder of its own in the song selector. Song files are .txt in the note format, or MIDI
 * (.mid/.midi), which {@link MidiImporter} turns into a playable melody.
 */
@Slf4j
public final class SongLibrary
{
	private static final String INDEX = "/songs/index.txt";
	public static final String DEFAULT_FOLDER = "Songs";
	public static final String MY_SONGS = "My songs";

	@Getter
	private final Path customDir;
	@Getter
	private List<Song> songs = Collections.emptyList();
	/** Folder names in display order, including custom folders that are still empty. */
	@Getter
	private List<String> folders = Collections.emptyList();
	/** Custom folders that exist on disk, so the selector can offer to open them. */
	@Getter
	private Set<String> customFolders = Collections.emptySet();
	@Getter
	private List<String> errors = Collections.emptyList();

	public SongLibrary(Path customDir)
	{
		this.customDir = customDir;
	}

	public void reload()
	{
		List<Song> loaded = new ArrayList<>();
		Set<String> folderOrder = new LinkedHashSet<>();
		Set<String> custom = new LinkedHashSet<>();
		List<String> problems = new ArrayList<>();

		String folder = DEFAULT_FOLDER;
		for (String line : readResource(INDEX).split("\\r?\\n"))
		{
			line = line.trim();
			if (line.isEmpty() || line.startsWith("//"))
			{
				continue;
			}
			if (line.startsWith("[") && line.endsWith("]"))
			{
				folder = line.substring(1, line.length() - 1).trim();
				continue;
			}
			try
			{
				loaded.add(SongParser.parse(readResource("/songs/" + line), stripExtension(line), "built-in").inFolder(folder));
				folderOrder.add(folder);
			}
			catch (RuntimeException e)
			{
				log.warn("Built-in song {} failed to load", line, e);
				problems.add(line + ": " + e.getMessage());
			}
		}

		if (customDir != null)
		{
			try
			{
				Files.createDirectories(customDir);
				List<Song> mine = loadDir(customDir, MY_SONGS, problems);
				if (!mine.isEmpty())
				{
					loaded.addAll(mine);
					folderOrder.add(MY_SONGS);
					custom.add(MY_SONGS);
				}
				List<Path> subdirs = new ArrayList<>();
				try (DirectoryStream<Path> dirs = Files.newDirectoryStream(customDir, Files::isDirectory))
				{
					dirs.forEach(subdirs::add);
				}
				subdirs.sort(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
				for (Path dir : subdirs)
				{
					String name = dir.getFileName().toString();
					loaded.addAll(loadDir(dir, name, problems));
					folderOrder.add(name);
					custom.add(name);
				}
			}
			catch (IOException e)
			{
				log.warn("Could not read custom song folder {}", customDir, e);
				problems.add("custom folder: " + e.getMessage());
			}
		}

		songs = Collections.unmodifiableList(loaded);
		folders = Collections.unmodifiableList(new ArrayList<>(folderOrder));
		customFolders = Collections.unmodifiableSet(custom);
		errors = Collections.unmodifiableList(problems);
	}

	/** Where a folder lives on disk, for custom folders. */
	public Path pathOf(String folder)
	{
		if (customDir == null || folder == null || !customFolders.contains(folder) || MY_SONGS.equals(folder))
		{
			return customDir;
		}
		return customDir.resolve(folder);
	}

	private static List<Song> loadDir(Path dir, String folder, List<String> problems) throws IOException
	{
		List<Song> out = new ArrayList<>();
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.{txt,TXT,mid,MID,midi,MIDI}"))
		{
			for (Path path : files)
			{
				String name = path.getFileName().toString();
				try
				{
					Song song;
					if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".txt"))
					{
						String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
						song = SongParser.parse(text, stripExtension(name), name);
					}
					else
					{
						try (InputStream in = new java.io.BufferedInputStream(Files.newInputStream(path)))
						{
							song = MidiImporter.load(in, stripExtension(name), name);
						}
					}
					out.add(song.inFolder(folder));
				}
				catch (IOException | RuntimeException e)
				{
					problems.add((MY_SONGS.equals(folder) ? "" : folder + "/") + name + ": " + e.getMessage());
				}
			}
		}
		out.sort(Comparator.comparing(Song::getTitle, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private static String readResource(String path)
	{
		InputStream in = SongLibrary.class.getResourceAsStream(path);
		if (in == null)
		{
			throw new IllegalStateException("missing resource " + path);
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			return reader.lines().collect(Collectors.joining("\n"));
		}
		catch (IOException e)
		{
			throw new IllegalStateException("could not read " + path, e);
		}
	}

	private static String stripExtension(String file)
	{
		int dot = file.lastIndexOf('.');
		return dot > 0 ? file.substring(0, dot) : file;
	}
}
