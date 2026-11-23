package com.clanpiano;

import com.clanpiano.song.Song;
import com.clanpiano.song.SongLibrary;
import com.clanpiano.song.Step;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.LinkBrowser;

/**
 * Side panel: a song selector with folders, search and favourites, and a "now playing"
 * card with the song's details, a melody preview and the start/stop controls.
 */
class ClanPianoPanel extends PluginPanel
{
	static final String FAVOURITES = "Favourites";

	private static final Color EASY = new Color(95, 200, 105);
	private static final Color MEDIUM = new Color(235, 175, 60);
	private static final Color HARD = new Color(235, 95, 90);
	private static final Color MUTED = new Color(150, 150, 150);
	private static final Color STAR_ON = new Color(255, 205, 60);
	private static final Color STAR_OFF = new Color(95, 95, 95);
	private static final Font SYMBOL_FONT = new Font("Dialog", Font.PLAIN, 13);

	private final ClanPianoPlugin plugin;

	private final JComboBox<PlayMode> modeBox = new JComboBox<>(PlayMode.values());
	private boolean updatingMode;
	private final IconTextField search = new IconTextField();
	private final ListPanel list = new ListPanel();
	private final JScrollPane listScroll = new JScrollPane(list);

	private final JLabel cardTitle = new JLabel();
	private final JLabel cardArtist = new JLabel();
	private final JLabel cardStats = new JLabel();
	private final JComboBox<Song> melodyBox = new JComboBox<>();
	private final JPanel melodyRow = new JPanel(new BorderLayout(6, 0));
	private boolean updatingMelody;
	private Song cardSong;
	private final MelodyPreview preview = new MelodyPreview();
	private final JLabel status = new JLabel();
	private final JLabel errors = new JLabel();

	private List<Song> songs = Collections.emptyList();
	private List<String> folders = Collections.emptyList();
	private final Set<String> collapsed = new HashSet<>();
	private final List<SongRow> rows = new ArrayList<>();
	private Song selected;
	private boolean collapseSet;

	ClanPianoPanel(ClanPianoPlugin plugin)
	{
		super(false);
		this.plugin = plugin;
		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildHeader(), BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listScroll.setBorder(BorderFactory.createEmptyBorder());
		listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		listScroll.getVerticalScrollBar().setUnitIncrement(16);
		listScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(listScroll, BorderLayout.CENTER);

		add(buildCard(), BorderLayout.SOUTH);
	}

	// ---- Layout ----

	private JPanel buildHeader()
	{
		JLabel title = new JLabel("Clan Piano Tutor");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		JLabel hint = new JLabel("Pick a song, then click the green key");
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(MUTED);

		modeBox.setFocusable(false);
		modeBox.setToolTipText("Timed follows the 0.6s game tick, like the hall hears it. Practice waits for you.");
		modeBox.addActionListener(e ->
		{
			if (!updatingMode && modeBox.getSelectedItem() != null)
			{
				plugin.setMode((PlayMode) modeBox.getSelectedItem());
			}
		});

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 30));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				rebuildList();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				rebuildList();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				rebuildList();
			}
		});
		search.addClearListener(this::rebuildList);

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);
		for (JComponent c : new JComponent[]{title, hint, modeBox, search})
		{
			c.setAlignmentX(LEFT_ALIGNMENT);
		}
		header.add(title);
		header.add(hint);
		header.add(Box.createVerticalStrut(8));
		header.add(modeBox);
		header.add(Box.createVerticalStrut(6));
		header.add(search);
		return header;
	}

	private JPanel buildCard()
	{
		cardTitle.setFont(FontManager.getRunescapeBoldFont());
		cardTitle.setForeground(ColorScheme.BRAND_ORANGE);
		cardArtist.setFont(FontManager.getRunescapeSmallFont());
		cardArtist.setForeground(MUTED);
		cardStats.setFont(FontManager.getRunescapeSmallFont());
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(Color.WHITE);
		errors.setFont(FontManager.getRunescapeSmallFont());
		errors.setForeground(HARD);
		JLabel melodyLabel = new JLabel("Melody");
		melodyLabel.setFont(FontManager.getRunescapeSmallFont());
		melodyLabel.setForeground(MUTED);
		melodyBox.setFocusable(false);
		melodyBox.setFont(FontManager.getRunescapeSmallFont());
		melodyBox.setToolTipText("<html>Which instrument's part is used as the tune.<br>"
			+ "The first is the best guess; if it doesn't sound right, try the next.</html>");
		melodyBox.setRenderer(new javax.swing.DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean sel, boolean focus)
			{
				super.getListCellRendererComponent(list, value, index, sel, focus);
				if (value instanceof Song)
				{
					Song v = (Song) value;
					boolean best = cardSong != null && v == cardSong;
					setText(v.getPart() + (best ? " (best guess)" : "") + "  ·  " + v.noteCount() + " notes");
				}
				return this;
			}
		});
		melodyBox.addActionListener(e ->
		{
			Song chosen = (Song) melodyBox.getSelectedItem();
			if (!updatingMelody && chosen != null && cardSong != null)
			{
				showStats(chosen);
				plugin.selectVersion(cardSong, chosen);
			}
		});
		melodyRow.setOpaque(false);
		melodyRow.add(melodyLabel, BorderLayout.WEST);
		melodyRow.add(melodyBox, BorderLayout.CENTER);
		melodyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		preview.setPreferredSize(new Dimension(PANEL_WIDTH - 40, 46));
		preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

		JButton start = new JButton("Start");
		start.setToolTipText("Start from the top (Timed mode counts you in). Or just click the green key.");
		start.addActionListener(e -> plugin.startSong());
		JButton stop = new JButton("Stop");
		stop.addActionListener(e -> plugin.stopSong());
		JPanel controls = new JPanel(new GridLayout(1, 2, 4, 0));
		controls.setOpaque(false);
		controls.add(start);
		controls.add(stop);

		JButton reload = smallButton("Reload", "Load songs again after adding or editing files");
		reload.addActionListener(e -> plugin.reloadSongs());
		JButton folder = smallButton("Open song folder", "Add your own .txt songs here; each subfolder becomes a folder in the list");
		folder.addActionListener(e -> openFolder(null));
		JPanel extras = new JPanel(new GridLayout(1, 2, 4, 0));
		extras.setOpaque(false);
		extras.add(reload);
		extras.add(folder);

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(2, 0, 0, 0, ColorScheme.BRAND_ORANGE),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		for (JComponent c : new JComponent[]{cardTitle, cardArtist, cardStats, melodyRow, preview, status, controls, extras, errors})
		{
			c.setAlignmentX(LEFT_ALIGNMENT);
		}
		card.add(cardTitle);
		card.add(cardArtist);
		card.add(Box.createVerticalStrut(4));
		card.add(cardStats);
		card.add(Box.createVerticalStrut(4));
		card.add(melodyRow);
		card.add(Box.createVerticalStrut(6));
		card.add(preview);
		card.add(Box.createVerticalStrut(6));
		card.add(status);
		card.add(Box.createVerticalStrut(6));
		card.add(controls);
		card.add(Box.createVerticalStrut(4));
		card.add(extras);
		card.add(errors);
		return card;
	}

	private static JButton smallButton(String text, String tip)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setToolTipText(tip);
		return b;
	}

	// ---- Called by the plugin (Swing thread) ----

	void setSongs(List<Song> songs, List<String> folders, Song selected, List<String> loadErrors)
	{
		this.songs = songs;
		this.folders = folders;
		this.selected = selected;
		if (!collapseSet)
		{
			// Start tidy: only the folder with the current song (and favourites) open.
			collapseSet = true;
			for (String f : folders)
			{
				if (selected == null || !f.equals(selected.getFolder()))
				{
					collapsed.add(f);
				}
			}
		}
		rebuildList();
		showCard(selected);
		status.setText(selected == null ? "" : "Ready: click the green key to start.");
		if (loadErrors.isEmpty())
		{
			errors.setText("");
		}
		else
		{
			StringBuilder sb = new StringBuilder("<html>Could not load:");
			loadErrors.forEach(err -> sb.append("<br>").append(escape(err)));
			errors.setText(sb.append("</html>").toString());
		}
	}

	void setMode(PlayMode mode)
	{
		updatingMode = true;
		try
		{
			modeBox.setSelectedItem(mode);
		}
		finally
		{
			updatingMode = false;
		}
	}

	void updateStatus(SongPlayer.State state, Song song, int position, int hits, int misses, int wrong)
	{
		if (song == null)
		{
			status.setText("");
			preview.setPosition(-1);
			return;
		}
		switch (state)
		{
			case IDLE:
				status.setText("Ready: click the green key to start.");
				preview.setPosition(-1);
				break;
			case COUNT_IN:
				status.setText("Get ready...");
				preview.setPosition(0);
				break;
			case PLAYING:
				status.setText("Playing: " + hits + " hit" + (misses > 0 ? ", " + misses + " missed" : ""));
				preview.setPosition(position);
				break;
			case FINISHED:
				int pct = song.noteCount() == 0 ? 0 : Math.round(100f * hits / song.noteCount());
				status.setText("<html>Finished! " + hits + " / " + song.noteCount() + " notes (" + pct + "%)"
					+ (wrong > 0 ? ", " + wrong + " wrong" : "") + "</html>");
				preview.setPosition(song.length());
				break;
		}
	}

	// ---- Song list ----

	private void rebuildList()
	{
		String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		int scroll = listScroll.getVerticalScrollBar().getValue();
		list.removeAll();
		rows.clear();

		List<String> order = new ArrayList<>();
		order.add(FAVOURITES);
		order.addAll(folders);
		boolean any = false;
		for (String folder : order)
		{
			boolean fav = FAVOURITES.equals(folder);
			List<Song> inFolder = songs.stream()
				.filter(s -> fav ? plugin.isFavourite(s) : folder.equals(s.getFolder()))
				.filter(s -> matches(s, query))
				.collect(Collectors.toList());
			boolean customEmpty = !fav && inFolder.isEmpty() && query.isEmpty()
				&& plugin.getLibrary().getCustomFolders().contains(folder);
			if (inFolder.isEmpty() && !customEmpty)
			{
				continue;
			}
			any = true;
			boolean open = !query.isEmpty() || !collapsed.contains(folder);
			list.add(new FolderHeader(folder, inFolder.size(), open, fav));
			if (!open)
			{
				continue;
			}
			if (customEmpty)
			{
				list.add(new EmptyFolderRow(folder));
			}
			for (Song song : inFolder)
			{
				SongRow row = new SongRow(song);
				rows.add(row);
				list.add(row);
			}
			list.add(Box.createVerticalStrut(4));
		}
		if (!any)
		{
			JLabel none = new JLabel(query.isEmpty() ? "No songs loaded." : "No songs match \"" + query + "\".");
			none.setFont(FontManager.getRunescapeSmallFont());
			none.setForeground(MUTED);
			none.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
			list.add(none);
		}
		list.add(Box.createVerticalGlue());
		list.revalidate();
		list.repaint();
		listScroll.getVerticalScrollBar().setValue(scroll);
	}

	private static boolean matches(Song s, String query)
	{
		if (query.isEmpty())
		{
			return true;
		}
		return s.getTitle().toLowerCase(Locale.ROOT).contains(query)
			|| s.getArtist().toLowerCase(Locale.ROOT).contains(query)
			|| s.getFolder().toLowerCase(Locale.ROOT).contains(query);
	}

	private void choose(Song song, boolean andStart)
	{
		selected = song;
		rows.forEach(SongRow::refresh);
		showCard(song);
		plugin.selectSong(song);
		if (andStart)
		{
			plugin.startSong();
		}
	}

	private void showCard(Song song)
	{
		cardSong = song;
		if (song == null)
		{
			preview.setSong(null);
			melodyRow.setVisible(false);
			cardTitle.setText("No song picked");
			cardArtist.setText(" ");
			cardStats.setText(" ");
			return;
		}
		cardTitle.setText(song.getTitle());
		Song version = plugin.resolveVersion(song);
		updatingMelody = true;
		try
		{
			melodyBox.removeAllItems();
			song.versions().forEach(melodyBox::addItem);
			melodyBox.setSelectedItem(version);
		}
		finally
		{
			updatingMelody = false;
		}
		melodyRow.setVisible(song.versions().size() > 1);
		showStats(version);
	}

	/** Artist line, stats and preview for the version being played. */
	private void showStats(Song song)
	{
		preview.setSong(song);
		cardArtist.setText(song.getArtist().isEmpty() ? song.getFolder() : song.getArtist());
		PianoKey low = song.lowestKey();
		PianoKey high = song.highestKey();
		String range = low == null ? "" : " · " + low.displayName() + " to " + high.displayName();
		cardStats.setText("<html><font color='" + hex(difficultyColor(song)) + "'>" + song.difficulty() + "</font>"
			+ "<font color='#b4b4b4'> · " + song.noteCount() + " notes · " + song.duration() + range + "</font></html>");
	}

	private void openFolder(String folder)
	{
		Path dir = plugin.getLibrary().pathOf(folder);
		if (dir == null)
		{
			return;
		}
		try
		{
			Files.createDirectories(dir);
			LinkBrowser.open(dir.toString());
		}
		catch (IOException ex)
		{
			errors.setText("<html>Song folder: " + escape(dir.toString()) + "</html>");
		}
	}

	private static Color difficultyColor(Song song)
	{
		switch (song.difficulty())
		{
			case HARD:
				return HARD;
			case MEDIUM:
				return MEDIUM;
			default:
				return EASY;
		}
	}

	private static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	// ---- List pieces ----

	/** Folder title bar: click to open or close it. */
	private class FolderHeader extends JPanel
	{
		FolderHeader(String folder, int count, boolean open, boolean fav)
		{
			super(new BorderLayout(6, 0));
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 8));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setAlignmentX(LEFT_ALIGNMENT);

			JLabel arrow = new JLabel(open ? "▾" : "▸");
			arrow.setFont(SYMBOL_FONT);
			arrow.setForeground(MUTED);
			JLabel name = new JLabel(folder);
			name.setFont(FontManager.getRunescapeBoldFont());
			name.setForeground(fav ? STAR_ON : Color.WHITE);
			JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			left.setOpaque(false);
			left.add(arrow);
			if (fav)
			{
				JLabel star = new JLabel("★");
				star.setFont(SYMBOL_FONT);
				star.setForeground(STAR_ON);
				left.add(star);
			}
			left.add(name);
			JLabel countLabel = new JLabel(String.valueOf(count));
			countLabel.setFont(FontManager.getRunescapeSmallFont());
			countLabel.setForeground(MUTED);
			add(left, BorderLayout.CENTER);
			add(countLabel, BorderLayout.EAST);

			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (!collapsed.remove(folder))
					{
						collapsed.add(folder);
					}
					rebuildList();
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					setBackground(ColorScheme.DARKER_GRAY_COLOR);
				}
			});
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	/** One song: title and artist, a difficulty stripe, its length and a favourite star. */
	private class SongRow extends JPanel
	{
		private final Song song;
		private final JLabel title = new JLabel();
		private final JLabel star = new JLabel();
		private boolean hover;

		SongRow(Song song)
		{
			super(new BorderLayout(6, 0));
			this.song = song;
			setAlignmentX(LEFT_ALIGNMENT);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("<html><b>" + escape(song.getTitle()) + "</b><br>" + song.difficulty() + " · "
				+ song.noteCount() + " notes · " + song.duration() + "<br>Double-click to start</html>");

			title.setText(song.getTitle());
			title.setFont(FontManager.getRunescapeFont());
			JLabel artist = new JLabel(song.getArtist().isEmpty() ? " " : song.getArtist());
			artist.setFont(FontManager.getRunescapeSmallFont());
			artist.setForeground(MUTED);
			JPanel text = new JPanel();
			text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
			text.setOpaque(false);
			text.add(title);
			text.add(artist);

			star.setFont(SYMBOL_FONT);
			star.setHorizontalAlignment(SwingConstants.RIGHT);
			star.setToolTipText("Favourite");
			JLabel length = new JLabel(song.duration());
			length.setFont(FontManager.getRunescapeSmallFont());
			length.setForeground(MUTED);
			length.setHorizontalAlignment(SwingConstants.RIGHT);
			JPanel meta = new JPanel(new GridLayout(2, 1));
			meta.setOpaque(false);
			meta.add(star);
			meta.add(length);

			add(text, BorderLayout.CENTER);
			add(meta, BorderLayout.EAST);

			star.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					plugin.toggleFavourite(song);
					rebuildList();
				}
			});
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					choose(song, e.getClickCount() >= 2);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					refresh();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					refresh();
				}
			});
			refresh();
		}

		void refresh()
		{
			boolean isSelected = selected != null && selected.id().equals(song.id());
			setBackground(isSelected ? ColorScheme.MEDIUM_GRAY_COLOR
				: hover ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 3, 0, 0, difficultyColor(song)),
				BorderFactory.createEmptyBorder(5, 7, 5, 6)));
			title.setForeground(isSelected ? ColorScheme.BRAND_ORANGE : Color.WHITE);
			boolean fav = plugin.isFavourite(song);
			star.setText(fav ? "★" : "☆");
			star.setForeground(fav ? STAR_ON : STAR_OFF);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	/** Shown in a custom folder with no songs yet. */
	private class EmptyFolderRow extends JLabel
	{
		EmptyFolderRow(String folder)
		{
			super("<html>Empty. Click to open this folder, add .txt songs, then Reload.</html>");
			setFont(FontManager.getRunescapeSmallFont());
			setForeground(MUTED);
			setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));
			setAlignmentX(LEFT_ALIGNMENT);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					openFolder(folder);
				}
			});
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	/** Keeps rows as wide as the scroll pane so long titles shorten instead of hiding the stars. */
	private static class ListPanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	/** A tiny piano roll of the whole song, with a playhead while it plays. */
	private static class MelodyPreview extends JComponent
	{
		private static final Color BG = new Color(24, 24, 28);
		private static final Color WHITE_NOTE = new Color(90, 175, 255);
		private static final Color BLACK_NOTE = new Color(165, 120, 255);
		private static final Color PLAYED = new Color(90, 90, 90);

		private Song song;
		private int position = -1;

		void setSong(Song song)
		{
			this.song = song;
			this.position = -1;
			repaint();
		}

		void setPosition(int position)
		{
			if (this.position != position)
			{
				this.position = position;
				repaint();
			}
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			g.setColor(BG);
			g.fillRoundRect(0, 0, w, h, 6, 6);
			if (song == null || song.length() == 0 || song.lowestKey() == null)
			{
				g.dispose();
				return;
			}
			int low = song.lowestKey().ordinal();
			int span = Math.max(1, song.highestKey().ordinal() - low);
			double dx = (w - 8) / (double) song.length();
			int noteW = Math.max(2, (int) Math.round(dx) - 1);
			for (int i = 0; i < song.length(); i++)
			{
				Step step = song.getSteps().get(i);
				for (PianoKey key : step.getKeys())
				{
					int x = 4 + (int) Math.round(i * dx);
					int y = h - 6 - (int) Math.round((key.ordinal() - low) / (double) span * (h - 12));
					g.setColor(position >= 0 && i < position ? PLAYED : key.isBlack() ? BLACK_NOTE : WHITE_NOTE);
					g.fillRect(x, y - 1, noteW, 3);
				}
			}
			if (position >= 0 && position < song.length())
			{
				int x = 4 + (int) Math.round(position * dx);
				g.setColor(ColorScheme.BRAND_ORANGE);
				g.fillRect(x, 2, 2, h - 4);
			}
			g.dispose();
		}
	}
}
