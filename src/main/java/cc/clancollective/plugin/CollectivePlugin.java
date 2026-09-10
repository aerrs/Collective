package cc.clancollective.plugin;

import cc.clancollective.plugin.chat.ChatRelayNotifier;
import cc.clancollective.plugin.clan.ClanDirectoryService;
import cc.clancollective.plugin.clan.ClanInfoService;
import cc.clancollective.plugin.clan.ClanRankTracker;
import cc.clancollective.plugin.clan.ClanSnapshot;
import cc.clancollective.plugin.clan.CollectiveStatsService;
import cc.clancollective.plugin.events.EventRecorder;
import cc.clancollective.plugin.net.WebhookClient;
import cc.clancollective.plugin.ui.CollectivePanel;
import cc.clancollective.plugin.ui.PanelConstants;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Collective",
	description = "Clan tools and Discord integration for OSRS clans",
	tags = {"clan", "clans", "discord", "webhook", "roster", "directory", "recruitment"}
)
public class CollectivePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private CollectiveConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private WebhookClient webhookClient;

	@Inject
	private ChatRelayNotifier chatRelayNotifier;

	@Inject
	private ClanRankTracker clanRankTracker;

	@Inject
	private ClanInfoService clanInfoService;

	@Inject
	private CollectiveStatsService collectiveStatsService;

	@Inject
	private ClanDirectoryService clanDirectoryService;

	private static final int CLAN_REFRESH_TICKS = 10;

	private final EventRecorder eventRecorder = new EventRecorder();
	private CollectivePanel panel;
	private NavigationButton navButton;
	private int ticksUntilClanRefresh = CLAN_REFRESH_TICKS;

	@Override
	protected void startUp() throws Exception
	{
		panel = new CollectivePanel(config, configManager, eventRecorder, clanDirectoryService);

		final NavigationButton.NavigationButtonBuilder builder = NavigationButton.builder()
			.tooltip(PanelConstants.NAV_TOOLTIP)
			.priority(PanelConstants.NAV_PRIORITY)
			.panel(panel);

		final BufferedImage icon = loadIcon();
		if (icon != null)
		{
			builder.icon(icon);
		}
		navButton = builder.build();

		clientToolbar.addNavigation(navButton);

		log.debug("Collective started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		webhookClient.cancelPendingRetries();

		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		panel = null;
		navButton = null;

		log.debug("Collective stopped");
	}

	@Provides
	CollectiveConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CollectiveConfig.class);
	}

	@Subscribe
	public void onChatMessage(final ChatMessage event)
	{
		chatRelayNotifier.onChatMessage(event);
	}

	@Subscribe
	public void onGameTick(final GameTick event)
	{
		clanRankTracker.onGameTick();

		if (--ticksUntilClanRefresh <= 0)
		{
			ticksUntilClanRefresh = CLAN_REFRESH_TICKS;
			pushClanSnapshot();
		}

		if (eventRecorder.isRecording())
		{
			eventRecorder.seedIfNeeded(client);
			final CollectivePanel current = panel;
			if (current != null)
			{
				javax.swing.SwingUtilities.invokeLater(current::refreshEvents);
			}
		}
	}

	@Subscribe
	public void onPlayerSpawned(final PlayerSpawned event)
	{
		if (!eventRecorder.isRecording())
		{
			return;
		}

		final ClanChannel channel = client.getClanChannel();
		if (channel == null)
		{
			return;
		}

		if (eventRecorder.onPlayerSpawned(event.getPlayer(), channel))
		{
			final CollectivePanel current = panel;
			if (current != null)
			{
				javax.swing.SwingUtilities.invokeLater(current::refreshEvents);
			}
		}
	}

	@Subscribe
	public void onClanChannelChanged(final ClanChannelChanged event)
	{
		pushClanSnapshot();
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		final GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			clanRankTracker.reset();
			ticksUntilClanRefresh = CLAN_REFRESH_TICKS;

			final CollectivePanel current = panel;
			if (current != null)
			{
				current.setLocalRsn(null);
				current.updateClan(ClanSnapshot.EMPTY);
				current.setClanStatsVisible(false);
			}
		}
	}

	private String localRsn()
	{
		final Player local = client.getLocalPlayer();
		return local != null && local.getName() != null ? Text.toJagexName(local.getName()) : null;
	}

	private void pushClanSnapshot()
	{
		final CollectivePanel current = panel;
		if (current == null)
		{
			return;
		}

		current.setLocalRsn(localRsn());

		final ClanSnapshot snapshot = clanInfoService.snapshot();
		current.updateClan(snapshot);

		if (config.showClanStats() && snapshot.isInClan())
		{
			current.setClanStatsVisible(true);
			collectiveStatsService.request(snapshot.getClanName(), config.clanCollectiveSlug(),
				stats ->
				{
					final CollectivePanel p = panel;
					if (p != null)
					{
						p.updateClanStats(stats);
					}
				});
		}
		else
		{
			current.setClanStatsVisible(false);
		}
	}

	private static BufferedImage loadIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(PanelConstants.class, PanelConstants.ICON_RESOURCE);
		}
		catch (Exception e)
		{
			log.warn("Collective: nav icon could not be loaded from {}", PanelConstants.ICON_RESOURCE, e);
			return null;
		}
	}
}
