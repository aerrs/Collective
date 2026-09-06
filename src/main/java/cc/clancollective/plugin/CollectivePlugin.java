package cc.clancollective.plugin;

import cc.clancollective.plugin.chat.ChatRelayNotifier;
import cc.clancollective.plugin.clan.ClanRankTracker;
import cc.clancollective.plugin.combat.CombatNotifier;
import cc.clancollective.plugin.milestones.MilestoneNotifier;
import cc.clancollective.plugin.net.WebhookClient;
import cc.clancollective.plugin.notifiers.DropNotifier;
import cc.clancollective.plugin.ui.CollectivePanel;
import cc.clancollective.plugin.ui.PanelConstants;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Collective",
	description = "Multi-clan Discord integration for OSRS clans",
	tags = {"clan", "discord", "webhook", "drops"}
)
public class CollectivePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private CollectiveConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private WebhookClient webhookClient;

	@Inject
	private DropNotifier dropNotifier;

	@Inject
	private ChatRelayNotifier chatRelayNotifier;

	@Inject
	private ClanRankTracker clanRankTracker;

	@Inject
	private MilestoneNotifier milestoneNotifier;

	@Inject
	private CombatNotifier combatNotifier;

	private CollectivePanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception
	{
		panel = new CollectivePanel(config, webhookClient);

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
		panel.onActivate();

		log.debug("Collective started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		webhookClient.cancelPendingRetries();

		if (panel != null)
		{
			panel.onDeactivate();
		}
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
	public void onNpcLootReceived(final NpcLootReceived event)
	{
		dropNotifier.onNpcLootReceived(event);
	}

	@Subscribe
	public void onPlayerLootReceived(final PlayerLootReceived event)
	{
		dropNotifier.onPlayerLootReceived(event);
	}

	@Subscribe
	public void onChatMessage(final ChatMessage event)
	{
		chatRelayNotifier.onChatMessage(event);

		switch (event.getType())
		{
			case GAMEMESSAGE:
				// Skip messages this plugin (or others) inject into the chatbox to avoid feedback loops.
				if (!"runelite".equals(event.getName()))
				{
					milestoneNotifier.onGameMessage(event.getMessage());
				}
				break;
			case CLAN_MESSAGE:
			case BROADCAST:
				combatNotifier.onClanBroadcast(event.getMessage());
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onStatChanged(final StatChanged event)
	{
		milestoneNotifier.onStatChanged(event);
	}

	@Subscribe
	public void onWidgetLoaded(final WidgetLoaded event)
	{
		milestoneNotifier.onWidgetLoaded(event);
	}

	@Subscribe
	public void onActorDeath(final ActorDeath event)
	{
		combatNotifier.onActorDeath(event);
	}

	@Subscribe
	public void onInteractingChanged(final InteractingChanged event)
	{
		combatNotifier.onInteractingChanged(event);
	}

	@Subscribe
	public void onGameTick(final GameTick event)
	{
		clanRankTracker.onGameTick();
		milestoneNotifier.onGameTick();
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			clanRankTracker.reset();
			milestoneNotifier.reset();
			combatNotifier.reset();
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
