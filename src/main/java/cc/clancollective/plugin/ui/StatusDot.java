package cc.clancollective.plugin.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

public class StatusDot extends JComponent
{
	private Color color = PanelConstants.STATUS_UNKNOWN;

	public StatusDot(final int diameter)
	{
		final Dimension size = new Dimension(diameter, diameter);
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		setOpaque(false);
	}

	public void setColor(final Color color)
	{
		if (!color.equals(this.color))
		{
			this.color = color;
			repaint();
		}
	}

	@Override
	protected void paintComponent(final Graphics g)
	{
		final Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			final int d = Math.min(getWidth(), getHeight());
			final int x = (getWidth() - d) / 2;
			final int y = (getHeight() - d) / 2;
			g2.fillOval(x, y, d, d);
		}
		finally
		{
			g2.dispose();
		}
	}
}
