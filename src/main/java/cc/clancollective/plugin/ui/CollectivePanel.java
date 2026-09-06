package cc.clancollective.plugin.ui;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.net.WebhookClient;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

public class CollectivePanel extends PluginPanel
{
	private final CollectiveConfig config;
	private final WebhookClient webhookClient;
	private final JPanel feedsBody = new JPanel();
	private final Runnable healthListener = this::onHealthChanged;

	public CollectivePanel(final CollectiveConfig config, final WebhookClient webhookClient)
	{
		super(false);
		this.config = config;
		this.webhookClient = webhookClient;

		setBackground(PanelConstants.BG);
		setBorder(new EmptyBorder(0, 0, 0, 0));
		setLayout(new BorderLayout());

		final JPanel content = new JPanel();
		content.setBackground(PanelConstants.BG);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(new EmptyBorder(0, 0, 0, 0));

		content.add(buildHeader());
		content.add(buildFeedsSection());
		content.add(buildSetupSection());
		content.add(buildFooter());

		add(content, BorderLayout.NORTH);

		refreshFeeds();
	}

	public void onActivate()
	{
		webhookClient.addHealthListener(healthListener);
		refreshFeeds();
	}

	public void onDeactivate()
	{
		webhookClient.removeHealthListener(healthListener);
	}

	private JPanel buildHeader()
	{
		final JPanel header = new JPanel(new BorderLayout(PanelConstants.ICON_GAP, 0));
		header.setBackground(PanelConstants.SURFACE);
		header.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(0, 0, PanelConstants.SEPARATOR_HEIGHT, 0, PanelConstants.BORDER),
			new EmptyBorder(PanelConstants.HEADER_PADDING_Y, PanelConstants.HEADER_PADDING_X,
				PanelConstants.HEADER_PADDING_Y, PanelConstants.HEADER_PADDING_X)));

		final JLabel icon = buildIcon();
		if (icon != null)
		{
			header.add(icon, BorderLayout.WEST);
		}

		final JLabel title = CollectiveSwing.normalLabel(PanelConstants.TITLE_TEXT, PanelConstants.TEXT);
		header.add(title, BorderLayout.CENTER);

		return header;
	}

	private JLabel buildIcon()
	{
		try
		{
			final BufferedImage image = ImageUtil.loadImageResource(PanelConstants.class, PanelConstants.ICON_RESOURCE);
			if (image == null)
			{
				return null;
			}
			final BufferedImage scaled = scaleIcon(image, PanelConstants.HEADER_ICON_SIZE);
			return new JLabel(new ImageIcon(scaled));
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private static BufferedImage scaleIcon(final BufferedImage src, final int size)
	{
		final BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = out.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.drawImage(src, 0, 0, size, size, null);
		}
		finally
		{
			g.dispose();
		}
		return out;
	}

	private JPanel buildFeedsSection()
	{
		feedsBody.setBackground(PanelConstants.BG);
		feedsBody.setLayout(new BoxLayout(feedsBody, BoxLayout.Y_AXIS));
		return section(PanelConstants.SECTION_FEEDS, feedsBody);
	}

	private void refreshFeeds()
	{
		feedsBody.removeAll();

		int shown = 0;

		if (!config.dropsWebhook().trim().isEmpty())
		{
			feedsBody.add(feedRow("Drops", firstHealth(config.dropsWebhook())));
			feedsBody.add(javax.swing.Box.createVerticalStrut(PanelConstants.ROW_GAP));
			shown++;
		}

		if (shown == 0)
		{
			final JLabel empty = CollectiveSwing.smallLabel(
				"<html><body style='width:150px'>" + PanelConstants.NO_FEEDS_TEXT + "</body></html>",
				PanelConstants.TEXT_DIM);
			empty.setBorder(new EmptyBorder(PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X,
				PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X));
			feedsBody.add(empty);
		}

		feedsBody.revalidate();
		feedsBody.repaint();
	}

	private cc.clancollective.plugin.net.WebhookHealth firstHealth(final String webhookValue)
	{
		final String first = webhookValue.trim().split("\\R", 2)[0].trim();
		return webhookClient.healthFor(first);
	}

	private JPanel feedRow(final String name, final cc.clancollective.plugin.net.WebhookHealth health)
	{
		final JPanel row = new JPanel(new BorderLayout(PanelConstants.ICON_GAP, 0));
		row.setBackground(PanelConstants.SURFACE);
		row.setBorder(new EmptyBorder(PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X,
			PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X));

		final StatusDot dot = new StatusDot(PanelConstants.STATUS_DOT_DIAMETER);
		dot.setColor(CollectiveSwing.statusColor(health.getState()));

		final JPanel dotWrap = new JPanel(new BorderLayout());
		dotWrap.setOpaque(false);
		dotWrap.add(dot, BorderLayout.CENTER);
		dotWrap.setPreferredSize(new Dimension(
			PanelConstants.STATUS_DOT_DIAMETER + 2, PanelConstants.STATUS_DOT_DIAMETER + 2));

		row.add(dotWrap, BorderLayout.WEST);
		row.add(CollectiveSwing.smallLabel(name, PanelConstants.TEXT), BorderLayout.CENTER);
		return row;
	}

	private void onHealthChanged()
	{
		SwingUtilities.invokeLater(this::refreshFeeds);
	}

	private JPanel buildSetupSection()
	{
		final JPanel body = new JPanel();
		body.setBackground(PanelConstants.BG);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		final JLabel placeholder = CollectiveSwing.smallLabel(
			"<html><body style='width:150px'>" + PanelConstants.SETUP_PLACEHOLDER + "</body></html>",
			PanelConstants.TEXT_DIM);
		placeholder.setBorder(new EmptyBorder(PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X,
			PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X));
		body.add(placeholder);

		return section(PanelConstants.SECTION_SETUP, body);
	}

	private JPanel buildFooter()
	{
		final JPanel footer = new JPanel();
		footer.setBackground(PanelConstants.BG);
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		footer.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(PanelConstants.SEPARATOR_HEIGHT, 0, 0, 0, PanelConstants.BORDER),
			new EmptyBorder(PanelConstants.HEADER_PADDING_Y, PanelConstants.HEADER_PADDING_X,
				PanelConstants.HEADER_PADDING_Y, PanelConstants.HEADER_PADDING_X)));

		final JLabel web = CollectiveSwing.smallLabel(PanelConstants.FOOTER_WEB_LABEL, PanelConstants.ACCENT);
		CollectiveSwing.asLink(web, PanelConstants.FOOTER_WEB_URL, PanelConstants.ACCENT, PanelConstants.ACCENT_HOVER);
		web.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JPanel row = new JPanel(new BorderLayout());
		row.setBackground(PanelConstants.BG);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(web, BorderLayout.WEST);

		footer.add(row);
		return footer;
	}

	private JPanel section(final String title, final JPanel body)
	{
		final JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(PanelConstants.BG);
		wrapper.setBorder(new EmptyBorder(PanelConstants.SECTION_GAP, 0, 0, 0));

		final JLabel header = CollectiveSwing.smallLabel(title.toUpperCase(), PanelConstants.ACCENT);
		header.setBorder(new EmptyBorder(PanelConstants.SECTION_HEADER_PADDING_Y, PanelConstants.HEADER_PADDING_X,
			PanelConstants.SECTION_HEADER_PADDING_Y, PanelConstants.HEADER_PADDING_X));

		wrapper.add(header, BorderLayout.NORTH);
		wrapper.add(body, BorderLayout.CENTER);
		return wrapper;
	}
}
