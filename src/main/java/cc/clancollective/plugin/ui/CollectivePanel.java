package cc.clancollective.plugin.ui;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.events.EventRecorder;
import cc.clancollective.plugin.net.WebhookClient;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
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
	private final EventRecorder recorder;
	private final JPanel feedsBody = new JPanel();
	private final Runnable healthListener = this::onHealthChanged;

	private JButton eventToggle;
	private JButton eventCopy;
	private JButton eventReset;
	private JLabel eventStatus;

	public CollectivePanel(final CollectiveConfig config, final WebhookClient webhookClient,
		final EventRecorder recorder)
	{
		super(false);
		this.config = config;
		this.webhookClient = webhookClient;
		this.recorder = recorder;

		setBackground(PanelConstants.BG);
		setBorder(new EmptyBorder(0, 0, 0, 0));
		setLayout(new BorderLayout());

		final JPanel content = new JPanel();
		content.setBackground(PanelConstants.BG);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(new EmptyBorder(0, 0, 0, 0));

		content.add(buildHeader());
		content.add(buildFeedsSection());
		content.add(buildEventsSection());
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

	private JPanel buildEventsSection()
	{
		final JPanel body = new JPanel();
		body.setBackground(PanelConstants.BG);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(new EmptyBorder(PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X,
			PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X));

		eventStatus = CollectiveSwing.smallLabel(" ", PanelConstants.TEXT);
		eventStatus.setToolTipText(PanelConstants.EVENT_IDLE_HINT);
		eventStatus.setAlignmentX(Component.LEFT_ALIGNMENT);

		eventToggle = flatButton(PanelConstants.EVENT_START);
		CollectiveSwing.onClick(eventToggle, this::onToggleEvent);

		final JPanel controls = new JPanel(new GridLayout(1, 2, PanelConstants.ROW_GAP, 0));
		controls.setBackground(PanelConstants.BG);
		controls.setAlignmentX(Component.LEFT_ALIGNMENT);

		eventCopy = flatButton(PanelConstants.EVENT_COPY);
		CollectiveSwing.onClick(eventCopy, this::onCopyEvent);

		eventReset = flatButton(PanelConstants.EVENT_RESET);
		CollectiveSwing.onClick(eventReset, this::onResetEvent);

		controls.add(eventCopy);
		controls.add(eventReset);

		body.add(eventStatus);
		body.add(javax.swing.Box.createVerticalStrut(PanelConstants.ROW_GAP));
		body.add(eventToggle);
		body.add(javax.swing.Box.createVerticalStrut(PanelConstants.ROW_GAP));
		body.add(controls);

		refreshEvents();
		return section(PanelConstants.SECTION_EVENTS, body);
	}

	private JButton flatButton(final String text)
	{
		final JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setBackground(PanelConstants.SURFACE);
		button.setForeground(PanelConstants.TEXT);
		button.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(1, 1, 1, 1, PanelConstants.BORDER),
			new EmptyBorder(PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X,
				PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X)));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
		return button;
	}

	private void onToggleEvent()
	{
		if (recorder.isRecording())
		{
			recorder.stop();
		}
		else
		{
			recorder.start();
		}
		refreshEvents();
	}

	private void onCopyEvent()
	{
		if (recorder.count() == 0)
		{
			return;
		}
		final String text = recorder.toClipboard();
		if (!text.isEmpty())
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		}
	}

	private void onResetEvent()
	{
		if (recorder.isRecording() || recorder.count() == 0)
		{
			return;
		}
		recorder.reset();
		refreshEvents();
	}

	public void refreshEvents()
	{
		if (eventToggle == null)
		{
			return;
		}

		final boolean recording = recorder.isRecording();
		final int count = recorder.count();

		eventToggle.setText(recording ? PanelConstants.EVENT_STOP : PanelConstants.EVENT_START);
		eventStatus.setText(formatTimer(recorder.elapsed()) + "  \u00b7  " + count);

		final List<String> names = recorder.names();
		eventStatus.setToolTipText(names.isEmpty()
			? PanelConstants.EVENT_NO_NAMES
			: "<html>" + String.join("<br>", names) + "</html>");

		eventCopy.setEnabled(count > 0);
		eventReset.setEnabled(!recording && count > 0);
	}

	private static String formatTimer(final Duration elapsed)
	{
		final long total = Math.max(0, elapsed.getSeconds());
		final long minutes = total / 60;
		final long seconds = total % 60;
		return String.format("%02d:%02d", minutes, seconds);
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
