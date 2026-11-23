package com.clanpiano;

import com.clanpiano.song.Step;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Paints boxes straight onto the piano: the key(s) to play now, the next key(s), and wrong presses.
 *
 * Boxes are placed from the note letters the piano prints (white-key letters under the keys,
 * sharps above them), because those sit exactly where the drawn keys are. The key widgets'
 * own bounds are only a fallback.
 */
class PianoKeyOverlay extends Overlay
{
	private final Client client;
	private final ClanPianoPlugin plugin;
	private final ClanPianoConfig config;

	@Inject
	PianoKeyOverlay(Client client, ClanPianoPlugin plugin, ClanPianoConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.isPianoOpen())
		{
			return null;
		}
		SongPlayer player = plugin.getPlayer();

		if (player.getState() == SongPlayer.State.IDLE || player.getState() == SongPlayer.State.FINISHED)
		{
			// Not started (or done): light the first note, which starts the song when played.
			Step first = player.firstNote();
			if (first != null)
			{
				first.getKeys().forEach(k -> fill(g, k, config.currentColor()));
			}
			return null;
		}

		if (config.showNextKey())
		{
			List<Step> upcoming = player.upcoming(8);
			Step next = upcoming.stream().filter(s -> !s.isRest()).findFirst().orElse(null);
			if (next != null)
			{
				for (PianoKey key : next.getKeys())
				{
					outline(g, key, config.nextColor());
				}
			}
		}

		Set<PianoKey> now = plugin.inHitBlink() ? Set.of() : player.remaining();
		for (PianoKey key : now)
		{
			fill(g, key, config.currentColor());
		}

		PianoKey wrong = config.showWrongKey() ? plugin.activeWrongKey() : null;
		if (wrong != null)
		{
			fill(g, wrong, config.wrongColor());
		}
		return null;
	}

	Rectangle bounds(PianoKey key)
	{
		Rectangle r = labelBox(key);
		return r != null ? r : widgetBox(key);
	}

	/** Box worked out from the printed note letters. */
	private Rectangle labelBox(PianoKey key)
	{
		Rectangle lowC = labelBounds(PianoKey.C4);
		Rectangle highB = labelBounds(PianoKey.B5);
		Rectangle sharp = labelBounds(PianoKey.CS4);
		Rectangle mine = labelBounds(key);
		if (lowC == null || highB == null || sharp == null || mine == null)
		{
			return null;
		}

		// The label widgets are tall boxes that overlap the keys: the sharp letters sit at the
		// top of theirs and the white-key letters at the bottom of theirs. These proportions
		// were measured against the piano as drawn in game.
		double whiteWidth = (centerX(highB) - centerX(lowC)) / 13.0;
		double top = sharp.y + sharp.height * 0.48;     // top edge of the keys
		double bottom = lowC.y + lowC.height * 0.72;    // bottom edge of the white keys
		double height = bottom - top;
		if (whiteWidth < 6 || height < 12)
		{
			return null;
		}

		int cx = centerX(mine);
		if (key.isBlack())
		{
			int w = (int) Math.round(whiteWidth * 0.6);
			int h = (int) Math.round(height * 0.56);
			return new Rectangle(cx - w / 2, (int) Math.round(top), w, h);
		}
		// White keys: the part below the black keys, so the box never hides a black key.
		int w = (int) Math.round(whiteWidth * 0.8);
		int y = (int) Math.round(top + height * 0.6);
		return new Rectangle(cx - w / 2, y, w, (int) Math.round(bottom) - y);
	}

	private Rectangle widgetBox(PianoKey key)
	{
		Widget w = client.getWidget(key.getWidgetId());
		if (w == null || w.isHidden())
		{
			return null;
		}
		Rectangle r = w.getBounds();
		if (r == null || r.width <= 2 || r.height <= 2)
		{
			return null;
		}
		return new Rectangle(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
	}

	private Rectangle labelBounds(PianoKey key)
	{
		Widget w = client.getWidget(key.getLabelWidgetId());
		if (w == null || w.isHidden())
		{
			return null;
		}
		Rectangle r = w.getBounds();
		return r == null || r.width <= 0 || r.height <= 0 ? null : r;
	}

	private static int centerX(Rectangle r)
	{
		return r.x + r.width / 2;
	}

	private void fill(Graphics2D g, PianoKey key, Color color)
	{
		Rectangle r = bounds(key);
		if (r == null)
		{
			return;
		}
		g.setColor(color);
		g.fill(r);
		g.setColor(color.darker());
		g.setStroke(new BasicStroke(2));
		g.draw(r);
	}

	private void outline(Graphics2D g, PianoKey key, Color color)
	{
		Rectangle r = bounds(key);
		if (r == null)
		{
			return;
		}
		g.setColor(color);
		g.setStroke(new BasicStroke(3));
		g.draw(r);
	}
}
