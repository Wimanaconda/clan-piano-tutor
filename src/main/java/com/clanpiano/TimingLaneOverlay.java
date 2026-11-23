package com.clanpiano;

import com.clanpiano.song.Song;
import com.clanpiano.song.Step;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A strip above the piano where the song's notes fall towards their keys, one row per game
 * tick. A note touches the line on top of the keys on the exact tick it should be played,
 * which is also when its key lights up green. The line flashes on every tick, like a
 * metronome, so the 0.6s beat can be felt before the first note.
 */
class TimingLaneOverlay extends Overlay
{
	private static final int PX_PER_TICK = 26;
	private static final Color LANE_BG = new Color(12, 12, 18, 175);
	private static final Color LANE_EDGE = new Color(255, 255, 255, 45);
	private static final Color GRID = new Color(255, 255, 255, 28);
	private static final Color NOTE_WHITE = new Color(90, 175, 255, 215);
	private static final Color NOTE_BLACK = new Color(165, 120, 255, 215);
	private static final Color PLAYED = new Color(140, 140, 140, 70);
	private static final Color TEXT = new Color(235, 235, 235);

	private final ClanPianoPlugin plugin;
	private final ClanPianoConfig config;
	private final PianoKeyOverlay keys;

	@Inject
	TimingLaneOverlay(ClanPianoPlugin plugin, ClanPianoConfig config, PianoKeyOverlay keys)
	{
		this.plugin = plugin;
		this.config = config;
		this.keys = keys;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showTimingLane() || !plugin.isPianoOpen())
		{
			return null;
		}
		SongPlayer player = plugin.getPlayer();
		Song song = player.getSong();
		Rectangle lowC = keys.bounds(PianoKey.C4);
		Rectangle highB = keys.bounds(PianoKey.B5);
		Rectangle sharp = keys.bounds(PianoKey.CS4);
		if (song == null || lowC == null || highB == null || sharp == null)
		{
			return null;
		}

		int ticks = config.laneTicks();
		int hitY = sharp.y; // top edge of the keys
		int left = lowC.x - 4;
		int right = highB.x + highB.width + 4;
		Rectangle lane = new Rectangle(left, Math.max(0, hitY - ticks * PX_PER_TICK), right - left, 0);
		lane.height = hitY - lane.y;
		if (lane.height < PX_PER_TICK)
		{
			return null;
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(LANE_BG);
		g.fill(lane);
		g.setColor(LANE_EDGE);
		g.setStroke(new BasicStroke(1));
		g.draw(lane);

		SongPlayer.State state = player.getState();
		double progress = plugin.tickProgress();

		Shape oldClip = g.getClip();
		g.clip(lane);

		// One faint line per tick, moving down with the notes.
		g.setColor(GRID);
		for (int k = 1; k <= ticks + 1; k++)
		{
			int y = (int) Math.round(hitY - (k - progress) * PX_PER_TICK);
			g.drawLine(lane.x, y, lane.x + lane.width, y);
		}

		if (state != SongPlayer.State.FINISHED)
		{
			drawNotes(g, player, song, hitY, ticks, progress);
		}
		g.setClip(oldClip);

		// The line the notes land on: flashes at the start of every tick.
		int alpha = state == SongPlayer.State.PLAYING || state == SongPlayer.State.COUNT_IN
			? (int) Math.round(80 + 175 * (1 - progress)) : 200;
		Color current = config.currentColor();
		g.setColor(new Color(current.getRed(), current.getGreen(), current.getBlue(), alpha));
		g.setStroke(new BasicStroke(3));
		g.drawLine(lane.x, hitY, lane.x + lane.width, hitY);

		drawCaption(g, player, lane);
		return null;
	}

	private void drawNotes(Graphics2D g, SongPlayer player, Song song, int hitY, int ticks, double progress)
	{
		int base = player.dueBase();
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		for (int i = Math.max(0, base); i < song.length() && i <= base + ticks + 1; i++)
		{
			Step step = song.getSteps().get(i);
			double due = i - base - progress; // ticks until this step should be played
			if (step.isRest() || due <= 0 || due > ticks + 1)
			{
				continue;
			}
			int bottom = (int) Math.round(hitY - due * PX_PER_TICK);
			// Green = its key is lit, play it now. Grey = already played, still landing.
			boolean open = i == player.getCursor() && player.isTargetOpen();
			boolean played = player.isActive() && i < player.getCursor();
			// White keys first so a black key's note is drawn on top.
			for (int pass = 0; pass < 2; pass++)
			{
				for (PianoKey key : step.getKeys())
				{
					if (key.isBlack() != (pass == 1))
					{
						continue;
					}
					Rectangle kb = keys.bounds(key);
					if (kb == null)
					{
						continue;
					}
					Rectangle block = new Rectangle(kb.x, bottom - (PX_PER_TICK - 4), kb.width, PX_PER_TICK - 4);
					Color fill = open ? config.currentColor() : played ? PLAYED : key.isBlack() ? NOTE_BLACK : NOTE_WHITE;
					g.setColor(fill);
					g.fillRoundRect(block.x, block.y, block.width, block.height, 6, 6);
					g.setColor(fill.darker());
					g.setStroke(new BasicStroke(1.5f));
					g.drawRoundRect(block.x, block.y, block.width, block.height, 6, 6);

					String name = key.displayName();
					int tw = fm.stringWidth(name);
					if (tw <= block.width)
					{
						int tx = block.x + (block.width - tw) / 2;
						int ty = block.y + (block.height + fm.getAscent()) / 2 - 1;
						g.setColor(Color.BLACK);
						g.drawString(name, tx + 1, ty + 1);
						g.setColor(TEXT);
						g.drawString(name, tx, ty);
					}
				}
			}
		}
	}

	private void drawCaption(Graphics2D g, SongPlayer player, Rectangle lane)
	{
		switch (player.getState())
		{
			case COUNT_IN:
				centered(g, lane, String.valueOf(player.getCountIn()), FontManager.getRunescapeBoldFont().deriveFont(28f), Color.YELLOW);
				break;
			case IDLE:
				top(g, lane, "Click the green key to start");
				break;
			case FINISHED:
				centered(g, lane, "Finished! " + player.getHits() + " / " + player.getSong().noteCount() + " notes",
					FontManager.getRunescapeBoldFont(), Color.GREEN);
				top(g, lane, "Click the green key to play again");
				break;
			default:
				break;
		}
	}

	private static void top(Graphics2D g, Rectangle lane, String text)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int x = lane.x + (lane.width - fm.stringWidth(text)) / 2;
		int y = lane.y + fm.getAscent() + 3;
		shadowed(g, text, x, y, TEXT);
	}

	private static void centered(Graphics2D g, Rectangle lane, String text, Font font, Color color)
	{
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int x = lane.x + (lane.width - fm.stringWidth(text)) / 2;
		int y = lane.y + (lane.height + fm.getAscent()) / 2;
		shadowed(g, text, x, y, color);
	}

	private static void shadowed(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}
}
