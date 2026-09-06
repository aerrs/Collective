package cc.clancollective.plugin.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

public final class CollectiveSwing
{
	private CollectiveSwing()
	{
	}

	public static JLabel smallLabel(final String text, final Color fg)
	{
		return smallLabel(text, SwingConstants.LEADING, fg);
	}

	public static JLabel smallLabel(final String text, final int alignment, final Color fg)
	{
		final JLabel label = new JLabel(text, alignment);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(fg);
		return label;
	}

	public static JLabel normalLabel(final String text, final Color fg)
	{
		final JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeFont());
		label.setForeground(fg);
		return label;
	}

	public static void asLink(final JLabel label, final String url, final Color resting, final Color hover)
	{
		label.setForeground(resting);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(final MouseEvent e)
			{
				LinkBrowser.browse(url);
			}

			@Override
			public void mouseEntered(final MouseEvent e)
			{
				label.setForeground(hover);
			}

			@Override
			public void mouseExited(final MouseEvent e)
			{
				label.setForeground(resting);
			}
		});
	}

	public static void onClick(final Component c, final Runnable action)
	{
		c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		c.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(final MouseEvent e)
			{
				action.run();
			}
		});
	}

	public static Color statusColor(final cc.clancollective.plugin.net.WebhookHealth.State state)
	{
		switch (state)
		{
			case OK:
				return PanelConstants.STATUS_OK;
			case WARN:
				return PanelConstants.STATUS_WARN;
			case ERROR:
				return PanelConstants.STATUS_ERROR;
			default:
				return PanelConstants.STATUS_UNKNOWN;
		}
	}
}
