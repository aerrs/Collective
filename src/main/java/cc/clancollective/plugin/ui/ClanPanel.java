package cc.clancollective.plugin.ui;

import cc.clancollective.plugin.clan.ClanService;
import cc.clancollective.plugin.net.CollectiveApi;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

public class ClanPanel extends JPanel
{
	private static final long FRESH_MS = 300_000L;
	private static final String[] TYPES = {null, "Variety", "Main", "Ironman", "PvM", "PvP", "Pure", "Skilling", "Social"};

	private final ClanService clans;
	private final JPanel tags = new JPanel(new GridLayout(0, 3, 2, 2));
	private final JPanel list = Ui.column(Ui.BG, null);
	private volatile boolean stopped;
	private String type;
	private String lastRequestKey;
	private long lastRequestedAt;
	private int requestId;

	public ClanPanel(final ClanService clans)
	{
		this.clans = clans;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Ui.BG);
		setBorder(Ui.pad(Ui.PAD, Ui.PAD));
		tags.setBackground(Ui.BG);
		tags.setAlignmentX(LEFT_ALIGNMENT);
		tags.setBorder(new EmptyBorder(0, 0, Ui.PAD, 0));
		for (final String t : TYPES)
		{
			final JLabel tag = Ui.label(t == null ? "All" : t, Ui.TEXT_DIM);
			tag.setName(t);
			tag.setHorizontalAlignment(SwingConstants.CENTER);
			tag.setOpaque(true);
			tag.setBackground(Ui.SURFACE);
			tag.setBorder(Ui.pad(4, 2));
			Ui.click(tag, () ->
			{
				type = t;
				show(null, true);
			});
			tags.add(tag);
		}
		add(tags);
		add(list);
	}

	public void stop()
	{
		stopped = true;
	}

	public void show(final String clan, final boolean enabled)
	{
		final String key = enabled + "|" + clan + "|" + type;
		final long now = System.currentTimeMillis();
		if (stopped || key.equals(lastRequestKey) && now - lastRequestedAt < FRESH_MS)
		{
			return;
		}
		lastRequestKey = key;
		lastRequestedAt = now;
		setVisible(enabled || clan == null);
		tags.setVisible(enabled && clan == null);
		for (final Component tag : tags.getComponents())
		{
			tag.setForeground(Objects.equals(type, tag.getName()) ? Ui.ACCENT : Ui.TEXT_DIM);
		}
		final int id = ++requestId;
		list.removeAll();
		stack(Ui.label(enabled ? "Loading..." : "Clan listings are turned off.", Ui.TEXT_DIM));
		if (enabled && clan != null)
		{
			clans.mine(clan, mine -> renderIfCurrent(id, () -> mine(clan, mine)));
		}
		else if (enabled)
		{
			clans.directory(type, found -> renderIfCurrent(id, () -> directory(found)));
		}
		revalidate();
		repaint();
	}

	private void mine(final String clan, final ClanService.Mine mine)
	{
		if (mine == null)
		{
			failed("Could not load your clan.", clan);
			return;
		}
		final ClanService.Clan c = mine.getCard();
		if (c == null)
		{
			stack(Ui.label("Not listed on Clan Collective.", Ui.TEXT_DIM));
			return;
		}
		final List<JLabel> extra = new ArrayList<>();
		if (c.getBlurb() != null && !c.getBlurb().isEmpty())
		{
			extra.add(Ui.html("<html><div style='width:130px'>" + Ui.escape(c.getBlurb()) + "</div></html>", Ui.TEXT));
		}
		final List<String> stats = new ArrayList<>();
		if (mine.getEhp() != null)
		{
			stats.add(hours(mine.getEhp(), "EHP"));
		}
		if (mine.getEhb() != null)
		{
			stats.add(hours(mine.getEhb(), "EHB"));
		}
		if (!stats.isEmpty())
		{
			extra.add(Ui.label(String.join(" · ", stats), Ui.TEXT_DIM));
		}
		stack(card(c, extra));
	}

	private void directory(final List<ClanService.Clan> found)
	{
		if (found == null)
		{
			failed("Could not load clans.", null);
			return;
		}
		if (found.isEmpty())
		{
			stack(Ui.label("No clans found.", Ui.TEXT_DIM));
		}
		found.forEach(c -> stack(card(c, Collections.emptyList())));
	}

	private void failed(final String text, final String clan)
	{
		lastRequestKey = null;
		final JLabel label = Ui.label(text + " Click to retry.", Ui.TEXT_DIM);
		Ui.click(label, () -> show(clan, true));
		stack(label);
	}

	private JPanel card(final ClanService.Clan c, final List<JLabel> extra)
	{
		final JPanel card = new JPanel(new BorderLayout(Ui.PAD, 0));
		card.setBackground(Ui.SURFACE);
		card.setBorder(Ui.pad(5, Ui.PAD));
		card.add(avatar(c), BorderLayout.WEST);
		final JPanel text = Ui.column(Ui.SURFACE, null);
		final JLabel name = Ui.label(c.getName(), Ui.TEXT);
		name.setFont(FontManager.getRunescapeBoldFont());
		if (c.getSlug() != null)
		{
			Ui.link(name, CollectiveApi.url("clans").addPathSegment(c.getSlug()).build().toString(), Ui.ACCENT);
		}
		text.add(name);
		final List<String> details = new ArrayList<>(c.getTypes().subList(0, Math.min(2, c.getTypes().size())));
		if (c.getMembers() > 0)
		{
			details.add(c.getMembers() + " members");
		}
		text.add(Ui.label(String.join(" · ", details), Ui.TEXT_DIM));
		extra.forEach(text::add);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	private JLabel avatar(final ClanService.Clan c)
	{
		final JLabel avatar = new JLabel(initial(c.getName()));
		avatar.setPreferredSize(new Dimension(ClanService.AVATAR, ClanService.AVATAR));
		avatar.setVerticalAlignment(SwingConstants.TOP);
		clans.avatar(c.getAvatar(), image -> SwingUtilities.invokeLater(() ->
		{
			if (image != null)
			{
				avatar.setIcon(new ImageIcon(image));
			}
		}));
		return avatar;
	}

	private static String hours(final double value, final String unit)
	{
		return String.format(Locale.ROOT, "%,d %s", Math.round(value), unit);
	}

	private static ImageIcon initial(final String name)
	{
		final int size = ClanService.AVATAR;
		final BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Ui.ACCENT);
		g.setStroke(new BasicStroke(2));
		g.drawOval(1, 1, size - 3, size - 3);
		final String letter = name == null || name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(Locale.ROOT);
		g.setFont(FontManager.getRunescapeBoldFont());
		g.setColor(Color.WHITE);
		final FontMetrics fm = g.getFontMetrics();
		g.drawString(letter, (size - fm.stringWidth(letter)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
		g.dispose();
		return new ImageIcon(img);
	}

	private void renderIfCurrent(final int id, final Runnable draw)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (id == requestId && !stopped)
			{
				list.removeAll();
				draw.run();
				revalidate();
				repaint();
			}
		});
	}

	private void stack(final JComponent part)
	{
		part.setAlignmentX(LEFT_ALIGNMENT);
		part.setMaximumSize(new Dimension(Integer.MAX_VALUE, part.getPreferredSize().height));
		list.add(part);
		list.add(Box.createVerticalStrut(Ui.GAP));
	}
}
