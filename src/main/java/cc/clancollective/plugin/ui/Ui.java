package cc.clancollective.plugin.ui;

import com.google.common.html.HtmlEscapers;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

final class Ui
{
	static final Color ACCENT = new Color(0x60, 0x78, 0xF8);
	static final Color ACCENT_HOVER = new Color(0x50, 0x60, 0xE0);
	static final Color BG = new Color(0x05, 0x04, 0x03);
	static final Color SURFACE = new Color(0x14, 0x14, 0x16);
	static final Color BORDER = new Color(0x23, 0x27, 0x43);
	static final Color TEXT = new Color(0xE1, 0xE1, 0xE3);
	static final Color TEXT_DIM = ColorScheme.LIGHT_GRAY_COLOR;
	static final int GAP = 4;
	static final int PAD = 8;

	private Ui()
	{
	}

	static JLabel label(final String text, final Color fg)
	{
		final JLabel label = new JLabel();
		label.putClientProperty("html.disable", Boolean.TRUE);
		label.setText(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(fg);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	static JLabel html(final String text, final Color fg)
	{
		final JLabel label = label("", fg);
		label.putClientProperty("html.disable", null);
		label.setText(text);
		return label;
	}

	static JPanel column(final Color bg, final Border border)
	{
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(bg);
		panel.setBorder(border);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	static EmptyBorder pad(final int v, final int h)
	{
		return new EmptyBorder(v, h, v, h);
	}

	static Border edge(final int top, final int bottom)
	{
		return BorderFactory.createCompoundBorder(new MatteBorder(top, 0, bottom, 0, BORDER), pad(PAD, 10));
	}

	static void link(final JComponent c, final String url, final Color hover)
	{
		final Color normal = c.getForeground();
		click(c, () -> LinkBrowser.browse(url));
		c.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(final MouseEvent e)
			{
				c.setForeground(hover);
			}

			@Override
			public void mouseExited(final MouseEvent e)
			{
				c.setForeground(normal);
			}
		});
	}

	static void click(final JComponent c, final Runnable action)
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

	static String escape(final String s)
	{
		return s == null ? "" : HtmlEscapers.htmlEscaper().escape(s);
	}
}
