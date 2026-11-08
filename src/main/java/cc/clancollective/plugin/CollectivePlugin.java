package cc.clancollective.plugin;

import cc.clancollective.plugin.bingo.BingoCapture;
import cc.clancollective.plugin.bingo.BingoChat;
import cc.clancollective.plugin.bingo.BingoService;
import cc.clancollective.plugin.ui.BingoPanel;
import com.google.inject.Provides;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Collective",
	description = "Tick your clan's Clan Collective bingo tiles automatically and follow your team's board",
	tags = {"clan", "bingo", "event", "competition"}
)
public class CollectivePlugin extends Plugin
{
	private static final int CHECK_TICKS = 10;
	private static final int PAIR_TICKS = 5;
	private static final String CLOG_PREFIX = "New item added to your collection log: ";
	private static final String[] PET_MESSAGES = {
		"You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack",
		"You have a funny feeling like you would have been followed",
	};

	@Inject
	private Client client;
	@Inject
	private ChatMessageManager chatMessageManager;
	@Inject
	private CollectiveConfig config;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ItemManager itemManager;
	@Inject
	private BingoService bingoService;
	@Inject
	private BingoCapture capture;

	private final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
	private BingoPanel panel;
	private NavigationButton navButton;
	private int ticks;
	private String kcName;
	private int kcTick = Integer.MIN_VALUE / 2;
	private int bestSeconds;
	private int secondsTick = Integer.MIN_VALUE / 2;
	private String lastClan;
	private String lastRsn;
	private long lastHash = -1;

	@Override
	protected void startUp()
	{
		panel = new BingoPanel(bingoService, itemManager, () -> bingoService.refreshIfOlderThan(60_000L));
		bingoService.onChange(this::redraw);
		navButton = NavigationButton.builder().tooltip("Collective").priority(7).panel(panel)
			.icon(ImageUtil.loadImageResource(BingoPanel.class, BingoPanel.ICON)).build();
		clientToolbar.addNavigation(navButton);
		redraw();
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		flushXp(true);
		xp.clear();
		levels.clear();
		bingoService.onChange(() -> { });
		bingoService.reset();
		panel = null;
		navButton = null;
		ticks = 0;
		kcName = null;
		bestSeconds = 0;
		lastClan = null;
		lastRsn = null;
		lastHash = -1;
	}

	@Provides
	CollectiveConfig provideConfig(final ConfigManager configManager)
	{
		return configManager.getConfig(CollectiveConfig.class);
	}

	@Subscribe
	public void onConfigChanged(final ConfigChanged event)
	{
		if (!CollectiveConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (config.bingo())
		{
			redraw();
		}
		else
		{
			bingoService.reset();
		}
	}

	@Subscribe
	public void onGameTick(final GameTick event)
	{
		if (config.bingo() && ++ticks >= CHECK_TICKS)
		{
			ticks = 0;
			lastClan = clanName();
			lastRsn = localRsn();
			lastHash = client.getAccountHash();
			bingoService.refresh(lastClan, lastRsn, lastHash);
			flushXp(false);
		}
	}

	@Subscribe
	public void onStatChanged(final StatChanged event)
	{
		final Integer before = xp.put(event.getSkill(), event.getXp());
		final Integer levelBefore = levels.put(event.getSkill(), event.getLevel());
		if (config.bingo() && before != null)
		{
			if (bingoService.addXp(event.getSkill().getName(), event.getXp() - before))
			{
				send(BingoService.XP, bingoService.takeXp(true), config.bingoScreenshots());
			}
			if (event.getLevel() > levelBefore)
			{
				submit(BingoService.LEVEL, Collections.singletonMap(event.getSkill().getName(), event.getLevel()));
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			flushXp(true);
			xp.clear();
			levels.clear();
		}
	}

	@Subscribe
	public void onChatMessage(final ChatMessage event)
	{
		if (!config.bingo() || event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		final String message = Text.removeTags(event.getMessage());
		if (message.startsWith(CLOG_PREFIX))
		{
			submit(BingoService.CLOG, Collections.singletonMap(message.substring(CLOG_PREFIX.length()).trim(), 1));
			return;
		}
		for (final String pet : PET_MESSAGES)
		{
			if (message.startsWith(pet))
			{
				submit(BingoService.PET, Collections.emptyMap());
				return;
			}
		}
		final int tick = client.getTickCount();
		final String boss = BingoChat.killCount(message);
		final int seconds = boss == null ? BingoChat.seconds(message) : -1;
		if (boss != null)
		{
			submit(BingoService.KC, Collections.singletonMap(boss, 1));
			kcName = boss;
			kcTick = tick;
		}
		else if (seconds > 0)
		{
			bestSeconds = tick - secondsTick > PAIR_TICKS ? seconds : Math.max(bestSeconds, seconds);
			secondsTick = tick;
		}
		else
		{
			final String tier = BingoChat.clueTier(message);
			if (tier != null)
			{
				submit(BingoService.CLUE, Collections.singletonMap(tier, 1));
			}
			return;
		}
		if (kcName != null && bestSeconds > 0 && Math.abs(kcTick - secondsTick) <= PAIR_TICKS)
		{
			submit(BingoService.PB, Collections.singletonMap(kcName, bestSeconds));
			kcName = null;
			bestSeconds = 0;
		}
	}

	@Subscribe
	public void onLootReceived(final LootReceived event)
	{
		if (config.bingo())
		{
			final Map<String, Integer> items = new LinkedHashMap<>();
			for (final ItemStack stack : event.getItems())
			{
				items.merge(itemManager.getItemComposition(stack.getId()).getName(), stack.getQuantity(), Integer::sum);
			}
			submit(BingoService.DROP, items);
		}
	}

	private void submit(final String kind, final Map<String, Integer> items)
	{
		final Map<String, Integer> matched = bingoService.matching(kind, items);
		send(kind, matched, config.bingoScreenshots() && (!BingoService.KC.equals(kind) || bingoService.completes(kind, matched)));
	}

	private void send(final String kind, final Map<String, Integer> matched, final boolean screenshot)
	{
		final String clan = clanName();
		final String rsn = localRsn();
		final long hash = client.getAccountHash();
		if (matched.isEmpty() || clan == null || rsn == null)
		{
			return;
		}
		if (!screenshot)
		{
			bingoService.submit(clan, rsn, hash, kind, matched, null, this::announce);
			return;
		}
		capture.take(shot -> bingoService.submit(clan, rsn, hash, kind, matched, shot, this::announce));
	}

	private void flushXp(final boolean force)
	{
		if (lastClan != null && lastRsn != null && lastHash != -1)
		{
			bingoService.submit(lastClan, lastRsn, lastHash, BingoService.XP, bingoService.takeXp(force), null, this::announce);
		}
	}

	private void redraw()
	{
		final BingoPanel p = panel;
		if (p != null)
		{
			p.show(bingoService.board(), config.bingo());
		}
	}

	private void announce(final String message)
	{
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("[Bingo] ")
				.append(ChatColorType.NORMAL).append(message).build()).build());
	}

	private String clanName()
	{
		final ClanChannel channel = client.getClanChannel();
		return channel != null ? channel.getName() : null;
	}

	private String localRsn()
	{
		final Player local = client.getLocalPlayer();
		return local != null && local.getName() != null ? Text.toJagexName(local.getName()) : null;
	}
}
