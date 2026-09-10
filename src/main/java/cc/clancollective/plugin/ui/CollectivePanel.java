package cc.clancollective.plugin.ui;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.clan.ClanMemberEntry;
import cc.clancollective.plugin.clan.ClanSnapshot;
import cc.clancollective.plugin.clan.ClanStats;
import cc.clancollective.plugin.events.EventRecorder;
import cc.clancollective.plugin.playtime.PlaytimeEntry;
import cc.clancollective.plugin.net.WebhookClient;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

public class CollectivePanel extends PluginPanel
{
	private final CollectiveConfig config;
	private final ConfigManager configManager;
	private final WebhookClient webhookClient;
	private final EventRecorder recorder;
	private volatile String localRsn;
	private final JPanel feedsBody = new JPanel();
	private final JPanel clanBody = new JPanel();
	private final JPanel rosterBody = new JPanel();
	private JPanel rosterSection;
	private JScrollPane rosterScroll;
	private JComboBox<String> rankFilter;
	private boolean updatingRankFilter;
	private final Map<String, ImageIcon> rankIcons = new HashMap<>();
	private List<ClanMemberEntry> lastRoster = Collections.emptyList();
	private String rosterSignature = "";
	private final JPanel clanStatsBody = new JPanel();
	private JPanel clanStatsSection;
	private boolean clanStatsRendered;
	private final JPanel playtimeBody = new JPanel();
	private JPanel playtimeSection;
	private JScrollPane playtimeScroll;
	private boolean playtimeRendered;
	private final Runnable healthListener = this::onHealthChanged;

	private JButton eventToggle;
	private JButton eventCopy;
	private JButton eventReset;
	private JLabel eventStatus;

	public CollectivePanel(final CollectiveConfig config, final ConfigManager configManager,
		final WebhookClient webhookClient, final EventRecorder recorder)
	{
		super(true);
		this.config = config;
		this.configManager = configManager;
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
		content.add(buildClanSection());
		content.add(buildRosterSection());
		content.add(buildClanStatsSection());
		content.add(buildPlaytimeSection());
		content.add(buildEventsSection());
		content.add(buildFeedsSection());
		content.add(buildSetupSection());
		content.add(buildFooter());

		add(content, BorderLayout.NORTH);

		refreshFeeds();
		updateClan(ClanSnapshot.EMPTY);
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

	public void setLocalRsn(final String rsn)
	{
		localRsn = rsn;
	}

	private boolean isSelf(final String name)
	{
		if (name == null || localRsn == null)
		{
			return false;
		}
		return name.replace('_', ' ').trim().equalsIgnoreCase(localRsn.replace('_', ' ').trim());
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

	private JPanel buildClanSection()
	{
		clanBody.setBackground(PanelConstants.BG);
		clanBody.setLayout(new BoxLayout(clanBody, BoxLayout.Y_AXIS));
		return section(PanelConstants.SECTION_CLAN, clanBody);
	}

	public void updateClan(final ClanSnapshot snapshot)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> updateClan(snapshot));
			return;
		}

		clanBody.removeAll();

		if (snapshot == null || !snapshot.isInClan())
		{
			clanBody.add(clanSummaryLabel(PanelConstants.CLAN_NONE_TITLE, PanelConstants.TEXT, true));
			clanBody.add(javax.swing.Box.createVerticalStrut(PanelConstants.ROW_GAP));
			clanBody.add(clanHint(PanelConstants.CLAN_NONE_HINT));
			clanBody.revalidate();
			clanBody.repaint();
			updateRoster(null);
			return;
		}

		clanBody.add(clanHeaderCard(snapshot));

		clanBody.revalidate();
		clanBody.repaint();

		updateRoster(snapshot);
	}

	private JPanel buildRosterSection()
	{
		rosterBody.setBackground(PanelConstants.BG);
		rosterBody.setLayout(new BoxLayout(rosterBody, BoxLayout.Y_AXIS));

		rosterScroll = new JScrollPane(rosterBody,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		rosterScroll.setBorder(new MatteBorder(1, 1, 1, 1, PanelConstants.BORDER));
		rosterScroll.setBackground(PanelConstants.BG);
		rosterScroll.getViewport().setBackground(PanelConstants.BG);
		rosterScroll.getVerticalScrollBar().setUnitIncrement(12);

		rankFilter = new JComboBox<>();
		rankFilter.setFont(FontManager.getRunescapeSmallFont());
		rankFilter.setBackground(PanelConstants.SURFACE);
		rankFilter.setForeground(PanelConstants.TEXT);
		rankFilter.setFocusable(false);
		rankFilter.addItem(PanelConstants.ROSTER_FILTER_ALL);
		rankFilter.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(final JList<?> list, final Object value,
				final int index, final boolean isSelected, final boolean cellHasFocus)
			{
				final JLabel label = (JLabel) super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
				label.setIcon(value == null ? null : rankIcons.get(value));
				label.setFont(FontManager.getRunescapeSmallFont());
				return label;
			}
		});
		rankFilter.addActionListener(e ->
		{
			if (!updatingRankFilter)
			{
				renderRosterRows();
			}
		});

		final JPanel filterWrap = new JPanel(new BorderLayout());
		filterWrap.setBackground(PanelConstants.BG);
		filterWrap.setBorder(new EmptyBorder(0, 0, PanelConstants.ROW_GAP, 0));
		filterWrap.add(rankFilter, BorderLayout.CENTER);
		filterWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			rankFilter.getPreferredSize().height + PanelConstants.ROW_GAP));

		final JPanel wrap = new JPanel();
		wrap.setBackground(PanelConstants.BG);
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setBorder(new EmptyBorder(0, PanelConstants.ROW_PADDING_X, 0, PanelConstants.ROW_PADDING_X));
		wrap.add(filterWrap);
		wrap.add(rosterScroll);

		rosterSection = section(PanelConstants.SECTION_ROSTER, wrap);
		rosterSection.setVisible(false);
		return rosterSection;
	}

	private void updateRoster(final ClanSnapshot snapshot)
	{
		if (rosterSection == null)
		{
			return;
		}

		final List<ClanMemberEntry> roster =
			snapshot == null ? Collections.emptyList() : snapshot.getRoster();

		if (snapshot == null || !snapshot.isInClan() || roster.isEmpty())
		{
			rosterSection.setVisible(false);
			rosterSignature = "";
			lastRoster = Collections.emptyList();
			rosterBody.removeAll();
			resetRankFilter();
			rosterSection.revalidate();
			rosterSection.repaint();
			return;
		}

		lastRoster = roster;
		rosterSection.setVisible(true);
		rebuildRankFilter(roster);
		renderRosterRows();
	}

	private void resetRankFilter()
	{
		if (rankFilter == null)
		{
			return;
		}
		updatingRankFilter = true;
		try
		{
			rankFilter.removeAllItems();
			rankFilter.addItem(PanelConstants.ROSTER_FILTER_ALL);
			rankFilter.setSelectedIndex(0);
		}
		finally
		{
			updatingRankFilter = false;
		}
	}

	private void rebuildRankFilter(final List<ClanMemberEntry> roster)
	{
		if (rankFilter == null)
		{
			return;
		}
		buildRankIcons(roster);
		updatingRankFilter = true;
		try
		{
			final Object current = rankFilter.getSelectedItem();
			final List<String> titles = distinctRankTitles(roster);
			rankFilter.removeAllItems();
			rankFilter.addItem(PanelConstants.ROSTER_FILTER_ALL);
			for (final String title : titles)
			{
				rankFilter.addItem(title);
			}
			if (current != null && (PanelConstants.ROSTER_FILTER_ALL.equals(current) || titles.contains(current)))
			{
				rankFilter.setSelectedItem(current);
			}
			else
			{
				rankFilter.setSelectedIndex(0);
			}
		}
		finally
		{
			updatingRankFilter = false;
		}
	}

	private void buildRankIcons(final List<ClanMemberEntry> roster)
	{
		rankIcons.clear();
		for (final ClanMemberEntry m : roster)
		{
			final String title = m.getRankTitle();
			if (title == null || title.isEmpty() || rankIcons.containsKey(title))
			{
				continue;
			}
			final BufferedImage icon = m.getRankIcon();
			if (icon != null)
			{
				rankIcons.put(title, new ImageIcon(fitIcon(icon, PanelConstants.ROSTER_ICON_SIZE)));
			}
		}
	}

	private static List<String> distinctRankTitles(final List<ClanMemberEntry> roster)
	{
		final Map<String, Integer> order = new LinkedHashMap<>();
		for (final ClanMemberEntry m : roster)
		{
			final String title = m.getRankTitle();
			if (title == null || title.isEmpty())
			{
				continue;
			}
			order.merge(title, m.getRankOrder(), Math::max);
		}
		final List<String> titles = new ArrayList<>(order.keySet());
		titles.sort((a, b) ->
		{
			final int c = Integer.compare(order.get(b), order.get(a));
			return c != 0 ? c : a.compareToIgnoreCase(b);
		});
		return titles;
	}

	private void renderRosterRows()
	{
		if (rosterSection == null)
		{
			return;
		}
		final Object selected = rankFilter == null ? null : rankFilter.getSelectedItem();
		final String signature = selected + "#" + localRsn + "#" + rosterSignatureOf(lastRoster);
		if (signature.equals(rosterSignature))
		{
			return;
		}
		rosterSignature = signature;

		rosterBody.removeAll();
		for (final ClanMemberEntry member : lastRoster)
		{
			if (selected == null || PanelConstants.ROSTER_FILTER_ALL.equals(selected)
				|| selected.equals(member.getRankTitle()))
			{
				rosterBody.add(rosterRow(member));
			}
		}

		final int rowHeight = rosterBody.getPreferredSize().height;
		final int height = Math.min(rowHeight, PanelConstants.ROSTER_MAX_HEIGHT);
		rosterScroll.setPreferredSize(new Dimension(0, height));
		rosterScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));

		rosterBody.revalidate();
		rosterBody.repaint();
		rosterSection.revalidate();
		rosterSection.repaint();
	}

	private static String rosterSignatureOf(final List<ClanMemberEntry> roster)
	{
		final StringBuilder sb = new StringBuilder(roster.size() * 12);
		for (final ClanMemberEntry m : roster)
		{
			sb.append(m.getName()).append(':').append(m.getRankOrder())
				.append(m.isOnline() ? '1' : '0').append('|');
		}
		return sb.toString();
	}

	private JPanel rosterRow(final ClanMemberEntry member)
	{
		final JPanel row = new JPanel(new BorderLayout(PanelConstants.ICON_GAP, 0));
		row.setBackground(PanelConstants.SURFACE);
		row.setBorder(new EmptyBorder(PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X,
			PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X));

		final JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(PanelConstants.ROSTER_ICON_SIZE, PanelConstants.ROSTER_ICON_SIZE));
		final BufferedImage rankIcon = member.getRankIcon();
		if (rankIcon != null)
		{
			icon.setIcon(new ImageIcon(fitIcon(rankIcon, PanelConstants.ROSTER_ICON_SIZE)));
		}
		icon.setToolTipText(member.getRankTitle());
		row.add(icon, BorderLayout.WEST);

		final boolean self = isSelf(member.getName());
		final JLabel name = CollectiveSwing.smallLabel(member.getName(),
			self ? PanelConstants.ACCENT : (member.isOnline() ? PanelConstants.TEXT : PanelConstants.TEXT_DIM));
		if (self)
		{
			name.setFont(name.getFont().deriveFont(Font.BOLD));
		}
		name.setToolTipText(member.getRankTitle() + (member.isOnline() ? " · online" : ""));
		row.add(name, BorderLayout.CENTER);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static BufferedImage fitIcon(final BufferedImage src, final int max)
	{
		final int w = src.getWidth();
		final int h = src.getHeight();
		if (w <= max && h <= max)
		{
			return src;
		}
		final double scale = Math.min(max / (double) w, max / (double) h);
		final int nw = Math.max(1, (int) Math.round(w * scale));
		final int nh = Math.max(1, (int) Math.round(h * scale));
		final BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = out.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(src, 0, 0, nw, nh, null);
		}
		finally
		{
			g.dispose();
		}
		return out;
	}

	private JPanel clanHeaderCard(final ClanSnapshot snapshot)
	{
		final JPanel card = new JPanel();
		card.setBackground(PanelConstants.SURFACE);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(new EmptyBorder(PanelConstants.ROW_PADDING_Y + 2, PanelConstants.ROW_PADDING_X,
			PanelConstants.ROW_PADDING_Y + 2, PanelConstants.ROW_PADDING_X));

		final JLabel name = CollectiveSwing.normalLabel(snapshot.getClanName(), PanelConstants.TEXT);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(name);

		final String counts = snapshot.getOnlineCount() + " online  ·  " + snapshot.getTotalMembers() + " members";
		final JLabel countsLabel = CollectiveSwing.smallLabel(counts, PanelConstants.TEXT_DIM);
		countsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(javax.swing.Box.createVerticalStrut(3));
		card.add(countsLabel);

		final String rank = snapshot.getLocalRankTitle();
		if (rank != null && !rank.isEmpty())
		{
			final JLabel you = CollectiveSwing.smallLabel(PanelConstants.CLAN_YOU_PREFIX + rank, PanelConstants.ACCENT);
			you.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(javax.swing.Box.createVerticalStrut(3));
			card.add(you);
		}

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JLabel clanSummaryLabel(final String text, final java.awt.Color fg, final boolean padded)
	{
		final JLabel label = CollectiveSwing.smallLabel(text, fg);
		if (padded)
		{
			label.setBorder(new EmptyBorder(PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X,
				PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X));
		}
		return label;
	}

	private JLabel clanHint(final String text)
	{
		final JLabel hint = CollectiveSwing.smallLabel(
			"<html><body style='width:150px'>" + text + "</body></html>", PanelConstants.TEXT_DIM);
		hint.setBorder(new EmptyBorder(0, PanelConstants.ROW_PADDING_X,
			PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X));
		return hint;
	}

	private JPanel buildClanStatsSection()
	{
		clanStatsBody.setBackground(PanelConstants.BG);
		clanStatsBody.setLayout(new BoxLayout(clanStatsBody, BoxLayout.Y_AXIS));
		clanStatsSection = collapsibleSection(PanelConstants.SECTION_CLAN_STATS, clanStatsBody, false);
		clanStatsSection.setVisible(false);
		return clanStatsSection;
	}

	public void setClanStatsVisible(final boolean visible)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> setClanStatsVisible(visible));
			return;
		}
		if (clanStatsSection == null)
		{
			return;
		}
		if (visible && !clanStatsRendered)
		{
			renderStatsMessage(PanelConstants.STATS_LOADING);
		}
		if (!visible)
		{
			clanStatsRendered = false;
			clanStatsBody.removeAll();
		}
		clanStatsSection.setVisible(visible);
		clanStatsSection.revalidate();
		clanStatsSection.repaint();
	}

	public void updateClanStats(final ClanStats stats)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> updateClanStats(stats));
			return;
		}
		if (clanStatsSection == null || !clanStatsSection.isVisible() || stats == null)
		{
			return;
		}

		switch (stats.getState())
		{
			case OK:
				if (stats.hasAnyStats())
				{
					renderStats(stats);
				}
				else
				{
					renderStatsMessage(PanelConstants.STATS_PENDING);
				}
				break;
			case NOT_FOUND:
				renderStatsMessage(PanelConstants.STATS_NOT_LISTED);
				break;
			default:
				if (!clanStatsRendered)
				{
					renderStatsMessage(PanelConstants.STATS_ERROR);
				}
				break;
		}
	}

	private void renderStats(final ClanStats stats)
	{
		clanStatsBody.removeAll();

		final JPanel tiles = pillRow(3);
		tiles.add(statPill(PanelConstants.STATS_EHP, formatInt(stats.getEhp()), null));
		tiles.add(statPill(PanelConstants.STATS_EHB, formatInt(stats.getEhb()), null));
		tiles.add(statPill(PanelConstants.STATS_TOTAL_XP, formatXp(stats.getTotalXp()), null));
		clanStatsBody.add(tiles);

		final ClanStats.Weekly weekly = stats.getWeekly();
		if (weekly != null && weekly.hasData())
		{
			clanStatsBody.add(Box.createVerticalStrut(PanelConstants.ROW_GAP + 3));
			final JLabel header = CollectiveSwing.smallLabel(PanelConstants.STATS_WEEKLY_HEADER,
				PanelConstants.TEXT_DIM);
			header.setBorder(new EmptyBorder(0, PanelConstants.ROW_PADDING_X, 2, PanelConstants.ROW_PADDING_X));
			clanStatsBody.add(header);

			final JPanel weeklyTiles = pillRow(2);
			weeklyTiles.add(statPill(PanelConstants.STATS_EHP, "+" + formatHours(weekly.getEhpGained()),
				topTooltip(weekly.getEhpTop())));
			weeklyTiles.add(statPill(PanelConstants.STATS_EHB, "+" + formatHours(weekly.getEhbGained()),
				topTooltip(weekly.getEhbTop())));
			clanStatsBody.add(weeklyTiles);
		}

		final String footer = statsFooter(stats);
		if (footer != null)
		{
			final JLabel src = CollectiveSwing.smallLabel(footer, PanelConstants.TEXT_DIM);
			src.setBorder(new EmptyBorder(PanelConstants.ROW_GAP, PanelConstants.ROW_PADDING_X, 0,
				PanelConstants.ROW_PADDING_X));
			clanStatsBody.add(Box.createVerticalStrut(PanelConstants.ROW_GAP));
			clanStatsBody.add(src);
		}

		clanStatsRendered = true;
		clanStatsBody.revalidate();
		clanStatsBody.repaint();
	}

	private JPanel pillRow(final int columns)
	{
		final JPanel row = new JPanel(new GridLayout(1, columns, PanelConstants.ROW_GAP, 0));
		row.setBackground(PanelConstants.BG);
		row.setBorder(new EmptyBorder(0, PanelConstants.ROW_PADDING_X, 0, PanelConstants.ROW_PADDING_X));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private JPanel statPill(final String label, final String value, final String tooltip)
	{
		final JPanel pill = new JPanel()
		{
			@Override
			protected void paintComponent(final Graphics g)
			{
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(PanelConstants.SURFACE);
				g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
					PanelConstants.PILL_RADIUS, PanelConstants.PILL_RADIUS);
				g2.dispose();
			}
		};
		pill.setOpaque(false);
		pill.setLayout(new BoxLayout(pill, BoxLayout.Y_AXIS));
		pill.setBorder(new EmptyBorder(6, 4, 6, 4));

		final JLabel valueLabel = CollectiveSwing.smallLabel(value, SwingConstants.CENTER, PanelConstants.TEXT);
		valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		final JLabel titleLabel = CollectiveSwing.smallLabel(label, SwingConstants.CENTER, PanelConstants.TEXT_DIM);
		titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		pill.add(valueLabel);
		pill.add(Box.createVerticalStrut(2));
		pill.add(titleLabel);

		if (tooltip != null)
		{
			pill.setToolTipText(tooltip);
		}
		return pill;
	}

	private void renderStatsMessage(final String message)
	{
		clanStatsBody.removeAll();
		final JLabel label = CollectiveSwing.smallLabel(
			"<html><body style='width:150px'>" + message + "</body></html>", PanelConstants.TEXT_DIM);
		label.setBorder(new EmptyBorder(PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X,
			PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X));
		clanStatsBody.add(label);
		clanStatsBody.revalidate();
		clanStatsBody.repaint();
	}

	private static String topTooltip(final List<ClanStats.Contributor> top)
	{
		if (top == null || top.isEmpty())
		{
			return null;
		}
		final StringBuilder sb = new StringBuilder("<html>");
		for (int i = 0; i < top.size(); i++)
		{
			if (i > 0)
			{
				sb.append("<br>");
			}
			final ClanStats.Contributor c = top.get(i);
			sb.append(c.getRsn()).append("  +").append(formatHours(c.getGained()));
		}
		return sb.append("</html>").toString();
	}

	private static String formatHours(final Double value)
	{
		if (value == null)
		{
			return "—";
		}
		final double d = value;
		if (d == Math.floor(d) && !Double.isInfinite(d))
		{
			return String.format("%,d", (long) d);
		}
		return String.format("%,.1f", d);
	}

	private static String statsFooter(final ClanStats stats)
	{
		final StringBuilder sb = new StringBuilder();
		final String source = sourceLabel(stats.getSource());
		if (source != null)
		{
			sb.append("via ").append(source);
		}
		final String ago = formatAgo(stats.getSyncedAt());
		if (ago != null)
		{
			if (sb.length() > 0)
			{
				sb.append("  ·  ");
			}
			sb.append("synced ").append(ago);
		}
		return sb.length() > 0 ? sb.toString() : null;
	}

	private static String sourceLabel(final String source)
	{
		if (source == null)
		{
			return null;
		}
		switch (source)
		{
			case "wom":
				return "Wise Old Man";
			case "temple":
				return "TempleOSRS";
			default:
				return source;
		}
	}

	private static String formatInt(final Number value)
	{
		return value == null ? "—" : String.format("%,d", value.longValue());
	}

	private static String formatXp(final Long xp)
	{
		if (xp == null)
		{
			return "—";
		}
		final long n = xp;
		if (n >= 1_000_000_000L)
		{
			return String.format("%.1fB", n / 1_000_000_000d);
		}
		if (n >= 1_000_000L)
		{
			return String.format("%.1fM", n / 1_000_000d);
		}
		if (n >= 1_000L)
		{
			return String.format("%.1fK", n / 1_000d);
		}
		return String.format("%,d", n);
	}

	private static String formatAgo(final String isoTimestamp)
	{
		if (isoTimestamp == null || isoTimestamp.isEmpty())
		{
			return null;
		}
		final Instant then;
		try
		{
			then = Instant.parse(isoTimestamp);
		}
		catch (Exception e)
		{
			return null;
		}
		final long seconds = Math.max(0, Duration.between(then, Instant.now()).getSeconds());
		if (seconds < 90)
		{
			return "just now";
		}
		final long minutes = seconds / 60;
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		final long hours = minutes / 60;
		if (hours < 24)
		{
			return hours + "h ago";
		}
		return (hours / 24) + "d ago";
	}

	private JPanel buildPlaytimeSection()
	{
		playtimeBody.setBackground(PanelConstants.BG);
		playtimeBody.setLayout(new BoxLayout(playtimeBody, BoxLayout.Y_AXIS));

		playtimeScroll = new JScrollPane(playtimeBody,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		playtimeScroll.setBorder(new MatteBorder(1, 1, 1, 1, PanelConstants.BORDER));
		playtimeScroll.setBackground(PanelConstants.BG);
		playtimeScroll.getViewport().setBackground(PanelConstants.BG);
		playtimeScroll.getVerticalScrollBar().setUnitIncrement(12);
		playtimeScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JLabel sub = CollectiveSwing.smallLabel(PanelConstants.PLAYTIME_SUBHEADER, PanelConstants.TEXT_DIM);
		sub.setBorder(new EmptyBorder(0, 0, PanelConstants.ROW_GAP, 0));
		sub.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JPanel wrap = new JPanel();
		wrap.setBackground(PanelConstants.BG);
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setBorder(new EmptyBorder(0, PanelConstants.ROW_PADDING_X, 0, PanelConstants.ROW_PADDING_X));
		wrap.add(sub);
		wrap.add(playtimeScroll);

		playtimeSection = collapsibleSection(PanelConstants.SECTION_PLAYTIME, wrap, false);
		playtimeSection.setVisible(false);
		return playtimeSection;
	}

	public void setPlaytimeVisible(final boolean visible)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> setPlaytimeVisible(visible));
			return;
		}
		if (playtimeSection == null)
		{
			return;
		}
		if (visible && !playtimeRendered)
		{
			renderPlaytimeMessage(PanelConstants.PLAYTIME_LOADING);
		}
		if (!visible)
		{
			playtimeRendered = false;
			playtimeBody.removeAll();
		}
		playtimeSection.setVisible(visible);
		playtimeSection.revalidate();
		playtimeSection.repaint();
	}

	public void updatePlaytime(final List<PlaytimeEntry> entries)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> updatePlaytime(entries));
			return;
		}
		if (playtimeSection == null || !playtimeSection.isVisible() || entries == null)
		{
			return;
		}
		if (entries.isEmpty())
		{
			renderPlaytimeMessage(PanelConstants.PLAYTIME_EMPTY);
			return;
		}

		playtimeBody.removeAll();
		int rank = 1;
		for (final PlaytimeEntry entry : entries)
		{
			playtimeBody.add(playtimeRow(rank++, entry));
		}

		final int height = Math.min(playtimeBody.getPreferredSize().height, PanelConstants.PLAYTIME_MAX_HEIGHT);
		playtimeScroll.setPreferredSize(new Dimension(0, height));
		playtimeScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));

		playtimeRendered = true;
		playtimeSection.revalidate();
		playtimeSection.repaint();
	}

	private JPanel playtimeRow(final int rank, final PlaytimeEntry entry)
	{
		final JPanel row = new JPanel(new BorderLayout(PanelConstants.ICON_GAP, 0));
		row.setBackground(PanelConstants.SURFACE);
		row.setBorder(new EmptyBorder(PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X,
			PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X));

		final JLabel rankLabel = CollectiveSwing.smallLabel(rank + ".", PanelConstants.TEXT_DIM);
		rankLabel.setPreferredSize(new Dimension(22, rankLabel.getPreferredSize().height));
		row.add(rankLabel, BorderLayout.WEST);

		final boolean self = isSelf(entry.getRsn());
		final JLabel rsn = CollectiveSwing.smallLabel(entry.getRsn(),
			self ? PanelConstants.ACCENT : PanelConstants.TEXT);
		if (self)
		{
			rsn.setFont(rsn.getFont().deriveFont(Font.BOLD));
		}
		row.add(rsn, BorderLayout.CENTER);
		row.add(CollectiveSwing.smallLabel(formatPlaytime(entry.getSeconds()), PanelConstants.ACCENT),
			BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private void renderPlaytimeMessage(final String message)
	{
		playtimeBody.removeAll();
		final JLabel label = CollectiveSwing.smallLabel(
			"<html><body style='width:150px'>" + message + "</body></html>", PanelConstants.TEXT_DIM);
		label.setBorder(new EmptyBorder(PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X,
			PanelConstants.ROW_PADDING_Y, PanelConstants.ROW_PADDING_X));
		playtimeBody.add(label);
		playtimeScroll.setPreferredSize(null);
		playtimeScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		playtimeSection.revalidate();
		playtimeSection.repaint();
	}

	private static String formatPlaytime(final long seconds)
	{
		final long totalMinutes = Math.max(0, seconds) / 60;
		final long hours = totalMinutes / 60;
		final long minutes = totalMinutes % 60;
		if (hours > 0)
		{
			return hours + "h " + minutes + "m";
		}
		return minutes + "m";
	}

	private JPanel buildFeedsSection()
	{
		feedsBody.setBackground(PanelConstants.BG);
		feedsBody.setLayout(new BoxLayout(feedsBody, BoxLayout.Y_AXIS));
		return collapsibleSection(PanelConstants.SECTION_FEEDS, feedsBody, false);
	}

	private void refreshFeeds()
	{
		feedsBody.removeAll();

		int shown = 0;
		shown += addFeedRow("Clan chat", config.chatWebhook());
		shown += addFeedRow("Clan admin", config.clanAdminWebhook());

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

	private int addFeedRow(final String name, final String webhookValue)
	{
		if (webhookValue.trim().isEmpty())
		{
			return 0;
		}
		feedsBody.add(feedRow(name, firstHealth(webhookValue)));
		feedsBody.add(javax.swing.Box.createVerticalStrut(PanelConstants.ROW_GAP));
		return 1;
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

	public void refreshFeedHealth()
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

		eventCopy = flatButton(PanelConstants.EVENT_COPY);
		CollectiveSwing.onClick(eventCopy, this::onCopyEvent);

		eventReset = flatButton(PanelConstants.EVENT_RESET);
		CollectiveSwing.onClick(eventReset, this::onResetEvent);

		final JPanel controls = new JPanel(new GridLayout(1, 3, PanelConstants.ROW_GAP, 0));
		controls.setBackground(PanelConstants.BG);
		controls.setAlignmentX(Component.LEFT_ALIGNMENT);
		controls.add(eventToggle);
		controls.add(eventCopy);
		controls.add(eventReset);

		body.add(eventStatus);
		body.add(javax.swing.Box.createVerticalStrut(PanelConstants.ROW_GAP));
		body.add(controls);

		refreshEvents();
		return collapsibleSection(PanelConstants.SECTION_EVENTS, body, false);
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

		final List<String> lines = recorder.lines();
		eventStatus.setToolTipText(lines.isEmpty()
			? PanelConstants.EVENT_NO_NAMES
			: "<html>" + String.join("<br>", lines) + "</html>");

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

		return collapsibleSection(PanelConstants.SECTION_SETUP, body, true);
	}

	private JPanel buildFooter()
	{
		final JPanel footer = new JPanel(new BorderLayout());
		footer.setBackground(PanelConstants.BG);
		footer.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(PanelConstants.SEPARATOR_HEIGHT, 0, 0, 0, PanelConstants.BORDER),
			new EmptyBorder(PanelConstants.HEADER_PADDING_Y, PanelConstants.HEADER_PADDING_X,
				PanelConstants.HEADER_PADDING_Y, PanelConstants.HEADER_PADDING_X)));

		final JLabel web = CollectiveSwing.smallLabel(PanelConstants.FOOTER_WEB_LABEL, PanelConstants.ACCENT);
		CollectiveSwing.asLink(web, PanelConstants.FOOTER_WEB_URL, PanelConstants.ACCENT, PanelConstants.ACCENT_HOVER);

		footer.add(web, BorderLayout.WEST);
		return footer;
	}

	private JPanel section(final String title, final Component body)
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

	private JPanel collapsibleSection(final String title, final Component body, final boolean defaultCollapsed)
	{
		final JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(PanelConstants.BG);
		wrapper.setBorder(new EmptyBorder(PanelConstants.SECTION_GAP, 0, 0, 0));

		final JLabel header = CollectiveSwing.smallLabel("", PanelConstants.ACCENT);
		header.setBorder(new EmptyBorder(PanelConstants.SECTION_HEADER_PADDING_Y, PanelConstants.HEADER_PADDING_X,
			PanelConstants.SECTION_HEADER_PADDING_Y, PanelConstants.HEADER_PADDING_X));

		final String key = collapseKey(title);
		final boolean[] state = {loadCollapsed(key, defaultCollapsed)};
		final Runnable apply = () ->
		{
			header.setText((state[0] ? PanelConstants.CHEVRON_COLLAPSED : PanelConstants.CHEVRON_EXPANDED)
				+ "  " + title.toUpperCase());
			body.setVisible(!state[0]);
		};
		apply.run();
		CollectiveSwing.onClick(header, () ->
		{
			state[0] = !state[0];
			saveCollapsed(key, state[0]);
			apply.run();
			wrapper.revalidate();
			wrapper.repaint();
		});

		wrapper.add(header, BorderLayout.NORTH);
		wrapper.add(body, BorderLayout.CENTER);
		return wrapper;
	}

	private static String collapseKey(final String title)
	{
		return "collapsed" + title.replaceAll("[^A-Za-z0-9]", "");
	}

	private boolean loadCollapsed(final String key, final boolean fallback)
	{
		if (configManager == null)
		{
			return fallback;
		}
		final String raw = configManager.getConfiguration(CollectiveConfig.GROUP, key);
		return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
	}

	private void saveCollapsed(final String key, final boolean collapsed)
	{
		if (configManager != null)
		{
			configManager.setConfiguration(CollectiveConfig.GROUP, key, Boolean.toString(collapsed));
		}
	}
}
