package com.clanpiano;

import com.clanpiano.song.Song;
import com.clanpiano.song.Step;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** A small box with the song name, progress, the note to play now and the notes after it. */
class SongInfoOverlay extends OverlayPanel
{
	private final ClanPianoPlugin plugin;
	private final ClanPianoConfig config;

	@Inject
	SongInfoOverlay(ClanPianoPlugin plugin, ClanPianoConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(220, 0));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		SongPlayer player = plugin.getPlayer();
		Song song = player.getSong();
		if (!config.showInfoBox() || song == null || !plugin.isPianoOpen())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder().text(song.getTitle()).color(Color.ORANGE).build());

		switch (player.getState())
		{
			case IDLE:
				line("Click the green key to start", "", Color.GREEN, Color.GREEN);
				break;
			case COUNT_IN:
				line("Get ready", String.valueOf(player.getCountIn()), Color.YELLOW);
				line("First", upcomingText(player.upcoming(config.upcomingCount())));
				break;
			case PLAYING:
				Step now = player.current();
				line("Play", now == null || now.isRest() ? "(rest)" : now.label(), Color.GREEN);
				line("Then", upcomingText(player.upcoming(config.upcomingCount())));
				line("Progress", (player.getPosition() + 1) + " / " + song.length());
				if (player.getMode() == PlayMode.TEMPO)
				{
					line("Hit / missed", player.getHits() + " / " + player.getMisses());
				}
				line("' = right half of the piano", "", Color.GRAY, Color.GRAY);
				break;
			case FINISHED:
				line("Finished!", "", Color.GREEN);
				line("Notes hit", player.getHits() + " / " + song.noteCount());
				line("Wrong keys", String.valueOf(player.getWrong()));
				break;
		}
		return super.render(g);
	}

	private static String upcomingText(List<Step> steps)
	{
		return steps.stream().map(Step::label).collect(Collectors.joining(" "));
	}

	private void line(String left, String right)
	{
		line(left, right, Color.WHITE);
	}

	private void line(String left, String right, Color rightColor)
	{
		line(left, right, Color.WHITE, rightColor);
	}

	private void line(String left, String right, Color leftColor, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.leftColor(leftColor)
			.right(right)
			.rightColor(rightColor)
			.build());
	}
}
