package cc.clancollective.plugin.clan;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.net.EmbedStyle;
import cc.clancollective.plugin.net.WebhookClient;
import cc.clancollective.plugin.net.WebhookPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

@Singleton
public class ClanRankTracker
{
	private static final String KIND_RANK = "rank";
	private static final String SNAPSHOT_KEY = "rankSnapshot";
	private static final int LOAD_DELAY_TICKS = 5;

	private final Client client;
	private final CollectiveConfig config;
	private final WebhookClient webhookClient;
	private final ConfigManager configManager;

	private boolean checkedThisSession;
	private int ticks;

	@Inject
	public ClanRankTracker(final Client client, final CollectiveConfig config,
		final WebhookClient webhookClient, final ConfigManager configManager)
	{
		this.client = client;
		this.config = config;
		this.webhookClient = webhookClient;
		this.configManager = configManager;
	}

	public void onGameTick()
	{
		if (checkedThisSession)
		{
			return;
		}

		final ClanSettings settings = client.getClanSettings();
		if (settings == null)
		{
			return;
		}

		if (++ticks < LOAD_DELAY_TICKS)
		{
			return;
		}

		checkedThisSession = true;

		final ClanChannel channel = client.getClanChannel();
		final String clanName = channel != null ? channel.getName() : null;
		if (clanName == null)
		{
			return;
		}

		compare(settings, clanName);
	}

	public void reset()
	{
		checkedThisSession = false;
		ticks = 0;
	}

	private void compare(final ClanSettings settings, final String clanName)
	{
		final Map<String, Integer> current = snapshot(settings);
		final Map<String, Integer> previous = load(clanName);
		save(clanName, current);

		if (previous.isEmpty())
		{
			return;
		}

		if (!config.relayRankChanges() || config.clanAdminWebhook().trim().isEmpty())
		{
			return;
		}

		for (final Map.Entry<String, Integer> entry : current.entrySet())
		{
			final Integer old = previous.get(entry.getKey());
			if (old == null || old.equals(entry.getValue()))
			{
				continue;
			}
			post(settings, clanName, entry.getKey(), old, entry.getValue());
		}
	}

	private void post(final ClanSettings settings, final String clanName, final String name,
		final int oldRank, final int newRank)
	{
		final boolean promoted = newRank > oldRank;
		final String oldTitle = titleFor(settings, oldRank);
		final String newTitle = titleFor(settings, newRank);
		final String verb = promoted ? "promoted" : "demoted";

		final String message = name + " has been " + verb + " from " + oldTitle + " to " + newTitle + ".";

		final WebhookPayload payload = WebhookPayload.of(KIND_RANK, name)
			.author(clanName, null)
			.description(WebhookPayload.quote(message))
			.color(EmbedStyle.CLAN_ADMIN);

		webhookClient.send(config.clanAdminWebhook(), payload);
	}

	private Map<String, Integer> snapshot(final ClanSettings settings)
	{
		final Map<String, Integer> map = new HashMap<>();
		final List<ClanMember> members = settings.getMembers();
		if (members == null)
		{
			return map;
		}
		for (final ClanMember member : members)
		{
			if (member == null || member.getName() == null)
			{
				continue;
			}
			map.put(Text.toJagexName(member.getName()), member.getRank().getRank());
		}
		return map;
	}

	private String titleFor(final ClanSettings settings, final int rank)
	{
		final ClanTitle title = settings.titleForRank(new ClanRank(rank));
		return title != null ? title.getName() : "Member";
	}

	private Map<String, Integer> load(final String clanName)
	{
		final Map<String, Integer> map = new HashMap<>();
		final String raw = configManager.getConfiguration(CollectiveConfig.GROUP, key(clanName));
		if (raw == null || raw.isEmpty())
		{
			return map;
		}
		for (final String pair : raw.split(";"))
		{
			final int sep = pair.lastIndexOf('=');
			if (sep <= 0)
			{
				continue;
			}
			try
			{
				map.put(pair.substring(0, sep), Integer.parseInt(pair.substring(sep + 1)));
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		return map;
	}

	private void save(final String clanName, final Map<String, Integer> snapshot)
	{
		final StringBuilder sb = new StringBuilder();
		for (final Map.Entry<String, Integer> entry : snapshot.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(';');
			}
			sb.append(entry.getKey()).append('=').append(entry.getValue());
		}
		configManager.setConfiguration(CollectiveConfig.GROUP, key(clanName), sb.toString());
	}

	private static String key(final String clanName)
	{
		return SNAPSHOT_KEY + "_" + clanName.toLowerCase().replaceAll("[^a-z0-9]", "");
	}
}
