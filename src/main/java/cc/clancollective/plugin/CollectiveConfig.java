package cc.clancollective.plugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(CollectiveConfig.GROUP)
public interface CollectiveConfig extends Config
{
	String GROUP = "collective";

	@ConfigSection(
		name = "Network",
		description = "Webhook delivery: timeouts and retry behaviour.",
		position = 1,
		closedByDefault = true
	)
	String SECTION_NETWORK = "network";

	@ConfigSection(
		name = "Clan chat",
		description = "Relay your clan channel to a Discord webhook.",
		position = 3
	)
	String SECTION_CHAT = "chat";

	@ConfigSection(
		name = "Clan Admin",
		description = "Relay clan applications and invites to a Discord webhook.",
		position = 4
	)
	String SECTION_CLAN_ADMIN = "clanAdmin";

	@ConfigSection(
		name = "Clan stats",
		description = "Show your clan's EHP/EHB and totals in the side panel, from Clan Collective.",
		position = 7
	)
	String SECTION_CLAN_STATS = "clanStats";

	@Range(min = 1, max = 60)
	@ConfigItem(
		keyName = "networkTimeout",
		name = "Network timeout",
		description = "How long to wait for a webhook request before treating it as failed.",
		section = SECTION_NETWORK,
		position = 0
	)
	@Units(Units.SECONDS)
	default int networkTimeout()
	{
		return 10;
	}

	@Range(min = 0, max = 10)
	@ConfigItem(
		keyName = "maxRetries",
		name = "Max retries",
		description = "How many times to retry a failed webhook delivery before giving up. Set to 0 to disable retries.",
		section = SECTION_NETWORK,
		position = 1
	)
	default int maxRetries()
	{
		return 3;
	}

	@Range(min = 0, max = 10000)
	@ConfigItem(
		keyName = "baseRetryDelay",
		name = "Base retry delay",
		description = "Starting delay before the first retry, in milliseconds. Each subsequent retry doubles this. "
			+ "Set to 0 to disable retries.",
		section = SECTION_NETWORK,
		position = 2
	)
	@Units(Units.MILLISECONDS)
	default int baseRetryDelay()
	{
		return 500;
	}

	@ConfigItem(
		keyName = "chatWebhook",
		name = "Clan chat webhook URL",
		secret = true,
		description = "Discord webhook URL(s) to post clan chat to. Put one URL per line to post to multiple channels.",
		section = SECTION_CHAT,
		position = 0
	)
	default String chatWebhook()
	{
		return "";
	}

	@ConfigItem(
		keyName = "clanFilter",
		name = "Clan name filter",
		description = "If set, clan chat, clan admin and rank relays only fire for the clan with this exact name. Leave blank to relay for whichever clan you are currently in.",
		section = SECTION_CHAT,
		position = 1
	)
	default String clanFilter()
	{
		return "";
	}

	@ConfigItem(
		keyName = "relayMessages",
		name = "Messages",
		description = "Relay ordinary clan chat messages, including the sender's RuneScape name and clan rank, to the configured Discord webhook.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_CHAT,
		position = 2
	)
	default boolean relayMessages()
	{
		return false;
	}

	@ConfigItem(
		keyName = "relayBroadcasts",
		name = "Broadcasts",
		description = "Relay clan broadcasts (drops, level-ups, completions), including player names, to the configured Discord webhook.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_CHAT,
		position = 3
	)
	default boolean relayBroadcasts()
	{
		return false;
	}

	@ConfigItem(
		keyName = "clanAdminWebhook",
		name = "Clan admin webhook URL",
		secret = true,
		description = "Discord webhook URL(s) to post clan admin events to. Put one URL per line to post to multiple channels.",
		section = SECTION_CLAN_ADMIN,
		position = 0
	)
	default String clanAdminWebhook()
	{
		return "";
	}

	@ConfigItem(
		keyName = "relayApplications",
		name = "Applications",
		description = "Relay members applying to join the clan, including their RuneScape name, to the configured Discord webhook.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_CLAN_ADMIN,
		position = 1
	)
	default boolean relayApplications()
	{
		return false;
	}

	@ConfigItem(
		keyName = "relayInvites",
		name = "Invites",
		description = "Relay members being invited into the clan, including their RuneScape name, to the configured Discord webhook.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_CLAN_ADMIN,
		position = 2
	)
	default boolean relayInvites()
	{
		return false;
	}

	@ConfigItem(
		keyName = "relayJoins",
		name = "Joins",
		description = "Relay members joining the clan, including their RuneScape name, to the configured Discord webhook.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_CLAN_ADMIN,
		position = 3
	)
	default boolean relayJoins()
	{
		return false;
	}

	@ConfigItem(
		keyName = "relayLeaves",
		name = "Leaves",
		description = "Relay members leaving the clan, including their RuneScape name, to the configured Discord webhook.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_CLAN_ADMIN,
		position = 4
	)
	default boolean relayLeaves()
	{
		return false;
	}

	@ConfigItem(
		keyName = "relayRankChanges",
		name = "Rank changes",
		description = "Relay members being promoted or demoted, including their RuneScape name, to the configured Discord webhook.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_CLAN_ADMIN,
		position = 5
	)
	default boolean relayRankChanges()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showClanStats",
		name = "Show clan stats",
		description = "Show your clan's EHP, EHB, total XP and member count in the side panel. "
			+ "Stats come from Clan Collective (clancollective.cc), which sources them from Wise Old Man / TempleOSRS. "
			+ "Your clan must be listed on Clan Collective for stats to appear.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_CLAN_STATS,
		position = 0
	)
	default boolean showClanStats()
	{
		return false;
	}

	@ConfigItem(
		keyName = "clanCollectiveSlug",
		name = "Clan Collective slug",
		description = "Optional. The slug from your clan's Clan Collective page URL "
			+ "(clancollective.cc/clans/your-slug). Leave blank to match automatically by your in-game clan name.",
		section = SECTION_CLAN_STATS,
		position = 1
	)
	default String clanCollectiveSlug()
	{
		return "";
	}
}
