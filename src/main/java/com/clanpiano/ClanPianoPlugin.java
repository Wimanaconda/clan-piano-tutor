package com.clanpiano;

import com.clanpiano.song.Song;
import com.clanpiano.song.SongLibrary;
import com.clanpiano.song.Step;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Clan Piano Tutor",
	description = "Highlights the next key to play on the Clan Hall piano, with a songbook of famous tunes",
	tags = {"piano", "clan", "hall", "music", "song", "notes"}
)
public class ClanPianoPlugin extends Plugin
{
	private static final String LAST_SONG_KEY = "lastSong";
	private static final String FAVOURITES_KEY = "favourites";
	private static final String MELODY_KEY_PREFIX = "melody_";
	private static final long WRONG_FLASH_MS = 400;
	private static final long HIT_BLINK_MS = 180;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClanPianoConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private KeyManager keyManager;

	@Inject
	private PianoKeyOverlay keyOverlay;

	@Inject
	private SongInfoOverlay infoOverlay;

	@Inject
	private TimingLaneOverlay laneOverlay;

	@Getter
	private final SongPlayer player = new SongPlayer();

	@Getter
	private final SongLibrary library = new SongLibrary(RuneLite.RUNELITE_DIR.toPath().resolve("clan-piano-songs"));

	@Getter
	private PianoKey wrongKey;
	private long wrongAt;
	private long hitAt;
	private long lastTickNanos;

	private final Set<String> favourites = Collections.synchronizedSet(new LinkedHashSet<>());
	private ClanPianoPanel panel;
	private NavigationButton navButton;

	private final KeyListener keyListener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			int code = e.getKeyCode();
			char ch = e.getKeyChar();
			clientThread.invoke(() ->
			{
				if (isPianoOpen())
				{
					log.debug("PIANO-DIAG key tick={} code={} ({}) char='{}'", client.getTickCount(), code, KeyEvent.getKeyText(code), ch);
				}
				if (config.keyboardInput() && ch != KeyEvent.CHAR_UNDEFINED)
				{
					onTyped(String.valueOf(ch));
				}
			});
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}
	};

	@Provides
	ClanPianoConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanPianoConfig.class);
	}

	@Override
	protected void startUp()
	{
		library.reload();
		favourites.clear();
		String favs = configManager.getConfiguration(ClanPianoConfig.GROUP, FAVOURITES_KEY);
		if (favs != null && !favs.isEmpty())
		{
			favourites.addAll(Arrays.asList(favs.split(Pattern.quote("|"))));
		}
		panel = new ClanPianoPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Clan Piano Tutor")
			.icon(createIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(keyOverlay);
		overlayManager.add(infoOverlay);
		overlayManager.add(laneOverlay);
		keyManager.registerKeyListener(keyListener);

		String last = configManager.getConfiguration(ClanPianoConfig.GROUP, LAST_SONG_KEY);
		Song initial = library.getSongs().stream()
			.filter(s -> s.id().equals(last) || s.getTitle().equals(last))
			.findFirst()
			.orElse(library.getSongs().isEmpty() ? null : library.getSongs().get(0));
		player.load(resolveVersion(initial));
		panel.setSongs(library.getSongs(), library.getFolders(), initial, library.getErrors());
		panel.setMode(config.mode());
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(keyListener);
		overlayManager.remove(keyOverlay);
		overlayManager.remove(infoOverlay);
		overlayManager.remove(laneOverlay);
		clientToolbar.removeNavigation(navButton);
		player.stop();
		panel = null;
		navButton = null;
	}

	// ---- Called from the side panel (Swing thread); state changes happen on the client thread ----

	void selectSong(Song song)
	{
		clientThread.invoke(() ->
		{
			boolean wasPlaying = player.isActive();
			player.load(resolveVersion(song));
			if (wasPlaying && song != null)
			{
				// Switching songs mid-play goes straight into the new one.
				player.start(config.mode(), config.countIn(), config.loop(), config.leadTicks());
			}
			if (song != null)
			{
				configManager.setConfiguration(ClanPianoConfig.GROUP, LAST_SONG_KEY, song.id());
			}
			refreshPanel();
		});
	}

	void setMode(PlayMode mode)
	{
		clientThread.invoke(() ->
		{
			configManager.setConfiguration(ClanPianoConfig.GROUP, ClanPianoConfig.MODE_KEY, mode);
			player.stop();
			refreshPanel();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ClanPianoConfig.GROUP.equals(event.getGroup()) && ClanPianoConfig.MODE_KEY.equals(event.getKey()))
		{
			PlayMode mode = config.mode();
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setMode(mode);
				}
			});
		}
	}

	void startSong()
	{
		clientThread.invoke(() ->
		{
			player.start(config.mode(), config.countIn(), config.loop(), config.leadTicks());
			refreshPanel();
		});
	}

	void stopSong()
	{
		clientThread.invoke(() ->
		{
			player.stop();
			refreshPanel();
		});
	}

	void reloadSongs()
	{
		clientThread.invoke(() ->
		{
			Song selected = player.getSong();
			library.reload();
			List<Song> songs = library.getSongs();
			Song match = selected == null ? null : songs.stream()
				.filter(s -> s.id().equals(selected.id()))
				.findFirst().orElse(null);
			if (match == null && !songs.isEmpty())
			{
				match = songs.get(0);
			}
			player.load(resolveVersion(match));
			Song finalMatch = match;
			List<String> folders = library.getFolders();
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setSongs(songs, folders, finalMatch, library.getErrors());
				}
			});
		});
	}

	// ---- Game events ----

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		lastTickNanos = System.nanoTime();
		if (player.getMode() == PlayMode.TEMPO && player.isActive())
		{
			player.tick();
			refreshPanel();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.CLAN_PIANO)
		{
			return;
		}
		// Log where the game puts each key and label once the interface has laid out,
		// so highlight positions can be checked against what is on screen.
		clientThread.invokeLater(() ->
		{
			StringBuilder sb = new StringBuilder("Clan piano layout:");
			for (PianoKey key : PianoKey.values())
			{
				sb.append("\n  ").append(key.name())
					.append(" key=").append(describe(client.getWidget(key.getWidgetId())))
					.append(" label=").append(describe(client.getWidget(key.getLabelWidgetId())))
					.append(" box=").append(keyOverlay.bounds(key));
			}
			log.debug(sb.toString());
		});
	}

	private static String describe(Widget w)
	{
		if (w == null)
		{
			return "null";
		}
		return "type" + w.getType() + (w.isHidden() ? " hidden " : " ") + w.getBounds()
			+ (w.getText() == null || w.getText().isEmpty() ? "" : " '" + w.getText() + "'");
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		if (isPianoOpen())
		{
			log.debug("PIANO-DIAG sound tick={} id={} delay={} source={}", client.getTickCount(), event.getSoundId(), event.getDelay(),
				event.getSource() == null ? "none" : event.getSource().getName());
		}
	}

	@Subscribe
	public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		if (isPianoOpen())
		{
			log.debug("PIANO-DIAG areasound tick={} id={} delay={} at={},{} source={}", client.getTickCount(), event.getSoundId(),
				event.getDelay(), event.getSceneX(), event.getSceneY(),
				event.getSource() == null ? "none" : event.getSource().getName());
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (isPianoOpen())
		{
			Widget w = event.getWidget();
			log.debug("PIANO-DIAG click tick={} option='{}' target='{}' action={} id={} p0={} p1={} widget={} item={}",
				client.getTickCount(), event.getMenuOption(), event.getMenuTarget(), event.getMenuAction(), event.getId(),
				event.getParam0(), event.getParam1(), w == null ? "null" : Integer.toHexString(w.getId()) + "[" + w.getIndex() + "]",
				event.getItemId());
		}
		PianoKey key = PianoKey.fromWidgetId(event.getParam1());
		if (key == null)
		{
			Widget widget = event.getWidget();
			key = widget == null ? null : PianoKey.fromWidgetId(widget.getId());
		}
		if (key != null)
		{
			onKeyPlayed(key);
		}
	}

	private void onTyped(String typed)
	{
		if (!isPianoOpen())
		{
			return;
		}
		for (PianoKey key : PianoKey.values())
		{
			Widget label = client.getWidget(key.getLabelWidgetId());
			if (label != null && !label.isHidden() && typed.equalsIgnoreCase(Text.removeTags(label.getText()).trim()))
			{
				onKeyPlayed(key);
				return;
			}
		}
	}

	private void onKeyPlayed(PianoKey key)
	{
		// No need to press Start: playing the song's first note starts it (after picking a
		// song, or after finishing one). Other keys are left alone so noodling doesn't.
		SongPlayer.State state = player.getState();
		if (state == SongPlayer.State.IDLE || state == SongPlayer.State.FINISHED)
		{
			Step first = player.firstNote();
			if (first == null || !first.getKeys().contains(key))
			{
				return;
			}
			player.startAtFirstNote(config.mode(), config.loop(), config.leadTicks());
		}
		SongPlayer.PressResult result = player.press(key);
		log.debug("PIANO-PRESS {} -> {} (step {}, now {})", key, result, player.getPosition(), player.current());
		if (result == SongPlayer.PressResult.HIT || result == SongPlayer.PressResult.WRONG)
		{
			hitAt = System.currentTimeMillis();
		}
		if (result == SongPlayer.PressResult.WRONG)
		{
			wrongKey = key;
			wrongAt = System.currentTimeMillis();
		}
		if (result != SongPlayer.PressResult.IGNORED)
		{
			refreshPanel();
		}
	}

	// ---- Melody versions of MIDI songs (called from the panel) ----

	/** The melody version the player picked for this song last time, else the best guess. */
	Song resolveVersion(Song song)
	{
		if (song == null || song.getAlternatives().isEmpty())
		{
			return song;
		}
		String part = configManager.getConfiguration(ClanPianoConfig.GROUP, melodyKey(song));
		return song.versions().stream().filter(v -> v.getPart().equals(part)).findFirst().orElse(song);
	}

	void selectVersion(Song song, Song version)
	{
		clientThread.invoke(() ->
		{
			configManager.setConfiguration(ClanPianoConfig.GROUP, melodyKey(song), version.getPart());
			player.load(version);
			refreshPanel();
		});
	}

	private static String melodyKey(Song song)
	{
		return MELODY_KEY_PREFIX + song.id().replaceAll("[^A-Za-z0-9]", "_");
	}

	// ---- Favourites (called from the panel) ----

	boolean isFavourite(Song song)
	{
		return song != null && favourites.contains(song.id());
	}

	void toggleFavourite(Song song)
	{
		if (song == null)
		{
			return;
		}
		if (!favourites.remove(song.id()))
		{
			favourites.add(song.id());
		}
		String joined;
		synchronized (favourites)
		{
			joined = String.join("|", favourites);
		}
		configManager.setConfiguration(ClanPianoConfig.GROUP, FAVOURITES_KEY, joined);
	}

	// ---- Helpers for overlays ----

	boolean isPianoOpen()
	{
		Widget keys = client.getWidget(InterfaceID.ClanPiano.KEYS);
		return keys != null && !keys.isHidden();
	}

	/**
	 * How far through the current game tick we are, 0 to 1, so the falling notes can move
	 * smoothly between ticks. 0 when the song is not running to the clock.
	 */
	double tickProgress()
	{
		if (player.getMode() != PlayMode.TEMPO || !player.isActive() || lastTickNanos == 0)
		{
			return 0;
		}
		double p = (System.nanoTime() - lastTickNanos) / 600_000_000.0;
		return Math.max(0, Math.min(1, p));
	}

	/** True just after a correct press, so the box can blink and a repeated note is visible. */
	boolean inHitBlink()
	{
		return System.currentTimeMillis() - hitAt < HIT_BLINK_MS;
	}

	PianoKey activeWrongKey()
	{
		if (wrongKey != null && System.currentTimeMillis() - wrongAt > WRONG_FLASH_MS)
		{
			wrongKey = null;
		}
		return wrongKey;
	}

	private void refreshPanel()
	{
		SongPlayer.State state = player.getState();
		Song song = player.getSong();
		int position = player.getPosition();
		int hits = player.getHits();
		int misses = player.getMisses();
		int wrong = player.getWrong();
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.updateStatus(state, song, position, hits, misses, wrong);
			}
		});
	}

	private static BufferedImage createIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(240, 240, 240));
		g.fillRect(1, 3, 14, 10);
		g.setColor(new Color(40, 40, 40));
		g.drawRect(1, 3, 14, 10);
		for (int x = 4; x < 15; x += 3)
		{
			g.drawLine(x, 3, x, 13);
		}
		g.setColor(new Color(20, 20, 20));
		for (int x : new int[]{3, 6, 12})
		{
			g.fillRect(x, 3, 2, 6);
		}
		g.dispose();
		return img;
	}
}
