package cc.clancollective.plugin.clan;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.net.WebhookClient;
import cc.clancollective.plugin.net.WebhookPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClanRankTrackerTest
{
	private static final String ADMIN_WEBHOOK = "https://discord.com/api/webhooks/2/tok";

	private Client client;
	private CollectiveConfig config;
	private WebhookClient webhookClient;
	private ConfigManager configManager;
	private ClanRankTracker tracker;
	private Map<String, String> store;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		config = mock(CollectiveConfig.class);
		webhookClient = mock(WebhookClient.class);
		configManager = mock(ConfigManager.class);
		tracker = new ClanRankTracker(client, config, webhookClient, configManager);

		when(config.relayRankChanges()).thenReturn(true);
		when(config.clanAdminWebhook()).thenReturn(ADMIN_WEBHOOK);

		store = new HashMap<>();
		when(configManager.getConfiguration(eq(CollectiveConfig.GROUP), anyString()))
			.thenAnswer(inv -> store.get(inv.getArgument(1)));
		doAnswer(inv ->
		{
			store.put(inv.getArgument(1), inv.getArgument(2));
			return null;
		}).when(configManager).setConfiguration(eq(CollectiveConfig.GROUP), anyString(), any());
	}

	private ClanSettings settings(final String name, final Map<String, Integer> members)
	{
		final ClanSettings settings = mock(ClanSettings.class);

		final ClanChannel channel = mock(ClanChannel.class);
		when(channel.getName()).thenReturn(name);
		when(client.getClanChannel()).thenReturn(channel);

		final List<ClanMember> list = new ArrayList<>();
		final Map<Integer, ClanTitle> titlesByRank = new HashMap<>();
		for (final Map.Entry<String, Integer> e : members.entrySet())
		{
			final ClanMember member = mock(ClanMember.class);
			when(member.getName()).thenReturn(e.getKey());
			when(member.getRank()).thenReturn(new ClanRank(e.getValue()));
			list.add(member);

			final int rank = e.getValue();
			if (!titlesByRank.containsKey(rank))
			{
				titlesByRank.put(rank, new ClanTitle(rank, "Rank" + rank));
			}
		}
		when(settings.getMembers()).thenReturn(list);

		when(settings.titleForRank(any(ClanRank.class))).thenAnswer(inv ->
		{
			final ClanRank r = inv.getArgument(0);
			return titlesByRank.get(r.getRank());
		});
		return settings;
	}

	private Map<String, Integer> roster(final Object... pairs)
	{
		final Map<String, Integer> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	private void runTicks(final ClanSettings settings)
	{
		when(client.getClanSettings()).thenReturn(settings);
		for (int i = 0; i < 6; i++)
		{
			tracker.onGameTick();
		}
	}

	@Test
	public void firstRunStoresBaselineAndPostsNothing()
	{
		runTicks(settings("HLDRS", roster("AER5", 124, "Sasha", 10)));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void promotionPostsToClanAdmin()
	{
		runTicks(settings("HLDRS", roster("Sasha", 10)));
		tracker.reset();
		runTicks(settings("HLDRS", roster("Sasha", 50)));
		verify(webhookClient).send(eq(ADMIN_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void demotionPostsToClanAdmin()
	{
		runTicks(settings("HLDRS", roster("Sasha", 50)));
		tracker.reset();
		runTicks(settings("HLDRS", roster("Sasha", 10)));
		verify(webhookClient).send(eq(ADMIN_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void unchangedRosterPostsNothing()
	{
		runTicks(settings("HLDRS", roster("Sasha", 10, "AER5", 124)));
		tracker.reset();
		runTicks(settings("HLDRS", roster("Sasha", 10, "AER5", 124)));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void joinsAndLeavesAreIgnored()
	{
		runTicks(settings("HLDRS", roster("Sasha", 10)));
		tracker.reset();
		runTicks(settings("HLDRS", roster("Sasha", 10, "NewGuy", 10)));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void nullClanSettingsIsNoOp()
	{
		when(client.getClanSettings()).thenReturn(null);
		for (int i = 0; i < 6; i++)
		{
			tracker.onGameTick();
		}
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void skippedWhenToggleOff()
	{
		runTicks(settings("HLDRS", roster("Sasha", 10)));
		tracker.reset();
		when(config.relayRankChanges()).thenReturn(false);
		runTicks(settings("HLDRS", roster("Sasha", 50)));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void skippedWhenWebhookBlank()
	{
		runTicks(settings("HLDRS", roster("Sasha", 10)));
		tracker.reset();
		when(config.clanAdminWebhook()).thenReturn("   ");
		runTicks(settings("HLDRS", roster("Sasha", 50)));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void checksOnlyOncePerSession()
	{
		runTicks(settings("HLDRS", roster("Sasha", 10)));
		tracker.reset();
		final ClanSettings changed = settings("HLDRS", roster("Sasha", 50));
		when(client.getClanSettings()).thenReturn(changed);
		for (int i = 0; i < 7; i++)
		{
			tracker.onGameTick();
		}
		verify(webhookClient).send(eq(ADMIN_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void waitsForLoadDelayBeforeChecking()
	{
		final ClanSettings loaded = settings("HLDRS", roster("Sasha", 10));
		when(client.getClanSettings()).thenReturn(loaded);
		tracker.onGameTick();
		tracker.onGameTick();
		verify(configManager, never()).setConfiguration(eq(CollectiveConfig.GROUP), anyString(), any());
	}

	@Test
	public void separateClansKeepSeparateBaselines()
	{
		runTicks(settings("HLDRS", roster("Sasha", 10)));
		tracker.reset();
		runTicks(settings("Other Clan", roster("Bob", 20)));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}
}
