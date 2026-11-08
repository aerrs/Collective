package cc.clancollective.plugin.ui;

import cc.clancollective.plugin.bingo.BingoBoard;
import cc.clancollective.plugin.bingo.BingoService;
import cc.clancollective.plugin.net.CollectiveApi;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import okhttp3.HttpUrl;

public class BingoPanel extends PluginPanel
{
	public static final String ICON = "/cc/clancollective/plugin/icon.png";
	private static final int CELL_GAP = 2;
	private static final int WIDTH = PluginPanel.PANEL_WIDTH - 2 * Ui.PAD;
	private static final Color REVIEW = new Color(0x4A, 0x3F, 0x1C);
	private static final Color GOLD = new Color(0xF5, 0xD0, 0x4A);
	private static final Color EMPTY = new Color(0x0C, 0x0C, 0x0E);
	private static final int UNLOCK_ICON = 24;

	private final BingoService bingo;
	private final ItemManager itemManager;
	private final Runnable onOpen;
	private final Map<String, Integer> ids = new HashMap<>();
	private final JPanel body = Ui.column(Ui.BG, Ui.pad(Ui.PAD, Ui.PAD));
	private BingoBoard shown;
	private boolean enabled;
	private int drawnWidth;

	public BingoPanel(final BingoService bingo, final ItemManager itemManager, final Runnable onOpen)
	{
		this.bingo = bingo;
		this.itemManager = itemManager;
		this.onOpen = onOpen;
		setBackground(Ui.BG);
		setBorder(new EmptyBorder(0, 0, 0, 0));
		setLayout(new BorderLayout());

		final JPanel header = new JPanel(new BorderLayout(Ui.GAP * 2, 0));
		header.setBackground(Ui.SURFACE);
		header.setBorder(Ui.edge(0, 1));
		final BufferedImage icon = ImageUtil.loadImageResource(BingoPanel.class, ICON);
		header.add(new JLabel(new ImageIcon(icon.getScaledInstance(24, 24, Image.SCALE_SMOOTH))), BorderLayout.WEST);
		final JLabel title = Ui.label("Collective", Ui.TEXT);
		title.setFont(FontManager.getRunescapeFont());
		header.add(title, BorderLayout.CENTER);

		final JPanel footer = new JPanel(new BorderLayout());
		footer.setBackground(Ui.BG);
		footer.setBorder(Ui.edge(1, 0));
		final JLabel link = Ui.label("clancollective.cc", Ui.ACCENT);
		Ui.link(link, CollectiveApi.BASE_URL.toString(), Ui.ACCENT_HOVER);
		footer.add(link, BorderLayout.WEST);

		final JPanel content = Ui.column(Ui.BG, null);
		for (final JPanel part : new JPanel[]{header, body, footer})
		{
			part.setAlignmentX(Component.LEFT_ALIGNMENT);
			content.add(part);
		}
		add(content, BorderLayout.NORTH);
		body.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(final ComponentEvent e)
			{
				if (width() != drawnWidth)
				{
					render(shown, enabled);
				}
			}
		});
	}

	@Override
	public void onActivate()
	{
		onOpen.run();
	}

	public void show(final BingoBoard board, final boolean enabled)
	{
		SwingUtilities.invokeLater(() -> render(board, enabled));
	}

	private int width()
	{
		return body.getWidth() > 0 ? body.getWidth() - 2 * Ui.PAD : WIDTH;
	}

	private void render(final BingoBoard board, final boolean enabled)
	{
		shown = board;
		this.enabled = enabled;
		drawnWidth = width();
		body.removeAll();
		if (board == null || board.getSize() < 1)
		{
			body.add(Ui.label(enabled ? "No live bingo for you right now." : "Clan bingo is turned off.", Ui.TEXT_DIM));
		}
		else
		{
			final Color team = colour(board.getColor());
			final JLabel name = Ui.label((board.getTeam() == null ? "" : board.getTeam()).toUpperCase(Locale.ROOT), team);
			name.setFont(FontManager.getRunescapeBoldFont());
			stack(name, Ui.label(board.getEvent() == null ? "" : board.getEvent(), Ui.TEXT_DIM), stats(board),
				grid(board, team, drawnWidth), Ui.label("UNLOCKS", Ui.ACCENT));
			if (board.getUnlocks().isEmpty())
			{
				stack(Ui.label("None yet", Ui.TEXT_DIM));
			}
			final Map<String, BingoBoard.Tile> byName = new HashMap<>();
			board.getTiles().forEach(t -> byName.putIfAbsent(t.getName(), t));
			board.getUnlocks().forEach(u -> stack(unlock(u, byName.get(u.getTile()))));
		}
		body.revalidate();
		body.repaint();
	}

	private void stack(final JComponent... parts)
	{
		for (final JComponent part : parts)
		{
			part.setAlignmentX(Component.LEFT_ALIGNMENT);
			part.setMaximumSize(new Dimension(Integer.MAX_VALUE, part.getPreferredSize().height));
			body.add(part);
			body.add(Box.createVerticalStrut(Ui.GAP));
		}
	}

	private static JPanel stats(final BingoBoard board)
	{
		final JPanel row = new JPanel(new GridLayout(1, 4, CELL_GAP, 0));
		row.setBackground(Ui.BG);
		final String[][] stats = {
			{board.getTeams() > 0 ? board.getRank() + "/" + board.getTeams() : "-", "PLACE"},
			{String.valueOf(board.getPoints()), "POINTS"},
			{board.getDone() + "/" + board.getTotal(), "TILES"},
			board.isHidden() ? new String[]{String.valueOf(board.getPicks()), "PICKS"} : new String[]{String.valueOf(board.getLines()), "LINES"},
		};
		for (final String[] stat : stats)
		{
			final JPanel box = Ui.column(Ui.SURFACE, Ui.pad(5, 2));
			final JLabel value = centred(stat[0], GOLD);
			value.setFont(FontManager.getRunescapeBoldFont());
			box.add(value);
			box.add(centred(stat[1], Ui.TEXT_DIM));
			row.add(box);
		}
		return row;
	}

	private static JLabel centred(final String text, final Color fg)
	{
		final JLabel label = Ui.label(text, fg);
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		return label;
	}

	private JPanel unlock(final BingoBoard.Unlock unlock, final BingoBoard.Tile tile)
	{
		final JPanel row = new JPanel(new BorderLayout(Ui.PAD, 0));
		row.setBackground(Ui.SURFACE);
		row.setBorder(Ui.pad(5, Ui.PAD));
		if (tile != null)
		{
			final JLabel icon = new JLabel("", SwingConstants.CENTER);
			icon.setFont(FontManager.getRunescapeSmallFont());
			icon.setForeground(Ui.TEXT_DIM);
			icon.setPreferredSize(new Dimension(UNLOCK_ICON, UNLOCK_ICON));
			icon(icon, tile, UNLOCK_ICON);
			row.add(icon, BorderLayout.WEST);
		}
		final JPanel text = Ui.column(Ui.SURFACE, null);
		text.add(Ui.label(unlock.getTile(), Ui.TEXT));
		final String by = String.join(", ", unlock.getBy());
		final String when = ago(unlock.getAt(), Instant.now());
		text.add(Ui.label((by.isEmpty() ? "" : by + (when.isEmpty() ? "" : "  ·  ")) + when, Ui.TEXT_DIM));
		row.add(text, BorderLayout.CENTER);
		return row;
	}

	private JPanel grid(final BingoBoard board, final Color team, final int width)
	{
		final int n = board.getSize();
		final int cell = (width - (n - 1) * CELL_GAP) / n;
		final BingoBoard.Tile[] cells = new BingoBoard.Tile[n * n];
		board.getTiles().stream().filter(t -> t.getPos() >= 0 && t.getPos() < cells.length).forEach(t -> cells[t.getPos()] = t);
		final JPanel grid = new JPanel(new GridLayout(n, n, CELL_GAP, CELL_GAP));
		grid.setBackground(Ui.BG);
		for (final BingoBoard.Tile tile : cells)
		{
			grid.add(tile(tile, cell, team, board.getUrl()));
		}
		grid.setPreferredSize(new Dimension(n * cell + (n - 1) * CELL_GAP, n * cell + (n - 1) * CELL_GAP));
		return grid;
	}

	private JLabel tile(final BingoBoard.Tile tile, final int cell, final Color team, final String url)
	{
		final JLabel label = new JLabel("", SwingConstants.CENTER);
		label.setOpaque(true);
		label.setPreferredSize(new Dimension(cell, cell));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Ui.TEXT);
		label.setVerticalTextPosition(SwingConstants.BOTTOM);
		label.setHorizontalTextPosition(SwingConstants.CENTER);
		label.setIconTextGap(0);
		label.setBackground(tile == null || tile.isLocked() ? EMPTY : tile.isDone() ? team.darker().darker() : tile.isReview() ? REVIEW : Ui.SURFACE);
		if (tile == null)
		{
			return label;
		}
		if (tile.isLocked())
		{
			label.setToolTipText("Locked");
			icon(label, tile, Math.min(cell / 2, 20));
			return label;
		}
		label.setBorder(tile.isMine() ? BorderFactory.createLineBorder(team) : null);
		final boolean xp = "xp".equals(tile.getKind());
		final String progress = (xp ? shortNumber(tile.getProgress()) : tile.getProgress()) + "/"
			+ (xp ? shortNumber(tile.getTarget()) : tile.getTarget());
		if (!tile.isDone() && !tile.isReview() && tile.getTarget() > 1 && tile.getProgress() > 0)
		{
			label.setText(progress);
		}
		label.setToolTipText("<html><b>" + Ui.escape(tile.getName()) + "</b> (" + tile.getPoints() + " pts)<br>"
			+ (tile.isDone() ? "Done" : tile.isReview() ? "Waiting for a captain" : progress.replace("/", " / "))
			+ (tile.getItems().isEmpty() ? "" : "<br>" + Ui.escape(String.join(", ", tile.getItems()))) + "</html>");
		icon(label, tile, Math.min(cell - (label.getText().isEmpty() ? 6 : 14), 32));
		final HttpUrl page = CollectiveApi.page(url);
		if (page != null)
		{
			Ui.link(label, page.toString(), Ui.TEXT);
		}
		return label;
	}

	private void icon(final JLabel label, final BingoBoard.Tile tile, final int size)
	{
		if (size < 8)
		{
			return;
		}
		bingo.image(tile.getImg(), image -> SwingUtilities.invokeLater(() ->
		{
			if (image == null)
			{
				itemIcon(label, tile, size);
				return;
			}
			final int side = Math.max(image.getWidth(), image.getHeight());
			label.setIcon(new ImageIcon(image.getScaledInstance(Math.max(1, image.getWidth() * size / side),
				Math.max(1, image.getHeight() * size / side), Image.SCALE_SMOOTH)));
		}));
	}

	private void itemIcon(final JLabel label, final BingoBoard.Tile tile, final int size)
	{
		final String name = tile.getIcon() == null ? "" : tile.getIcon().trim();
		final int id = name.isEmpty() ? -1 : ids.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> itemManager.search(name)
			.stream().filter(p -> p.getName().equalsIgnoreCase(name)).findFirst().map(p -> p.getId()).orElse(-1));
		if (id < 0)
		{
			if (label.getText().isEmpty())
			{
				label.setText(initials(tile.getName()));
			}
			return;
		}
		final AsyncBufferedImage image = itemManager.getImage(id);
		final Runnable apply = () -> label.setIcon(new ImageIcon(image.getScaledInstance(size, size * 32 / 36, Image.SCALE_SMOOTH)));
		image.onLoaded(apply);
		apply.run();
	}

	private static Color colour(final String hex)
	{
		try
		{
			return Color.decode(hex);
		}
		catch (NumberFormatException | NullPointerException e)
		{
			return Ui.ACCENT;
		}
	}

	static String ago(final String iso, final Instant now)
	{
		final long minutes;
		try
		{
			minutes = Duration.between(Instant.parse(iso), now).toMinutes();
		}
		catch (DateTimeParseException | NullPointerException e)
		{
			return "";
		}
		return minutes < 1 ? "just now" : minutes < 60 ? minutes + "m ago" : minutes < 2880 ? minutes / 60 + "h ago" : minutes / 1440 + "d ago";
	}

	static String shortNumber(final int n)
	{
		if (n < 10_000)
		{
			return String.valueOf(n);
		}
		final String s = String.format(Locale.ROOT, "%.1f", n < 1_000_000 ? n / 1000.0 : n / 1_000_000.0);
		return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + (n < 1_000_000 ? "k" : "m");
	}

	static String initials(final String name)
	{
		final StringBuilder sb = new StringBuilder();
		for (final String word : (name == null ? "" : name).split("\\s+"))
		{
			if (!word.isEmpty() && sb.length() < 3 && Character.isLetterOrDigit(word.charAt(0)))
			{
				sb.append(Character.toUpperCase(word.charAt(0)));
			}
		}
		return sb.toString();
	}
}
