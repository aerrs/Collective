package cc.clancollective.plugin;

import cc.clancollective.plugin.domain.ChatPrivacyMode;
import cc.clancollective.plugin.domain.CombatTier;
import cc.clancollective.plugin.domain.LevelNotifyMode;
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
		name = "Screenshots",
		description = "How screenshots are captured and attached to webhook posts.",
		position = 0
	)
	String SECTION_SCREENSHOT = "screenshot";

	@ConfigSection(
		name = "Network",
		description = "Webhook delivery: timeouts and retry behaviour.",
		position = 1,
		closedByDefault = true
	)
	String SECTION_NETWORK = "network";

	@ConfigSection(
		name = "Drops",
		description = "Post item drops to a Discord webhook.",
		position = 2
	)
	String SECTION_DROPS = "drops";

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
		name = "Milestones",
		description = "Post personal achievements - combat tasks, personal bests, pets, level-ups, quests and clues.",
		position = 5
	)
	String SECTION_MILESTONES = "milestones";

	@ConfigSection(
		name = "Combat & PvP",
		description = "Post deaths, player kills and loot keys to a Discord webhook.",
		position = 6
	)
	String SECTION_COMBAT = "combat";

	@ConfigItem(
		keyName = "chatPrivacy",
		name = "Chat privacy",
		description = "How much of the chat interface to hide before capturing a screenshot. "
			+ "Hiding chat causes a brief flicker in-client as it's hidden for the capture. "
			+ "Defaults to hiding all chat so private conversations never leak into a post; "
			+ "choose Show all for no flicker, but be aware chat will then appear in screenshots.",
		section = SECTION_SCREENSHOT,
		position = 0
	)
	default ChatPrivacyMode chatPrivacy()
	{
		return ChatPrivacyMode.HIDE_ALL;
	}

	@Range(min = 10, max = 100)
	@ConfigItem(
		keyName = "screenshotScale",
		name = "Screenshot scale",
		description = "Percentage to scale captured screenshots to before sending. Lower values reduce upload size.",
		section = SECTION_SCREENSHOT,
		position = 1
	)
	@Units(Units.PERCENT)
	default int screenshotScale()
	{
		return 100;
	}

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
		keyName = "dropsWebhook",
		name = "Drops webhook URL",
		secret = true,
		description = "Discord webhook URL(s) to post drops to. Put one URL per line to post to multiple channels.",
		section = SECTION_DROPS,
		position = 0
	)
	default String dropsWebhook()
	{
		return "";
	}

	@ConfigItem(
		keyName = "notifyDrops",
		name = "Drops",
		description = "Post item drops from NPC kills and player kills, including the items and their total value, to the configured Discord webhook.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_DROPS,
		position = 1
	)
	default boolean notifyDrops()
	{
		return false;
	}

	@Range(min = 0)
	@ConfigItem(
		keyName = "dropsMinValue",
		name = "Minimum value",
		description = "Only post drops whose total value is at least this many coins. Set to 0 to post everything.",
		section = SECTION_DROPS,
		position = 2
	)
	default int dropsMinValue()
	{
		return 50_000;
	}

	@ConfigItem(
		keyName = "dropsScreenshot",
		name = "Include screenshot",
		description = "Attach a screenshot to drop posts. Screenshots may show other players and, unless hidden, chat; the chat privacy setting applies.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_DROPS,
		position = 3
	)
	default boolean dropsScreenshot()
	{
		return false;
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
		description = "If set, clan chat, clan admin, rank, PvP and personal-best relays only fire for the clan with this exact name. Leave blank to relay for whichever clan you are currently in.",
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
		keyName = "milestonesWebhook",
		name = "Milestones webhook URL",
		secret = true,
		description = "Discord webhook URL(s) to post milestones to. Put one URL per line to post to multiple channels.",
		section = SECTION_MILESTONES,
		position = 0
	)
	default String milestonesWebhook()
	{
		return "";
	}

	@ConfigItem(
		keyName = "milestonesScreenshot",
		name = "Include screenshot",
		description = "Attach a screenshot to milestone posts. Screenshots may show other players and, unless hidden, chat; the chat privacy setting applies.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_MILESTONES,
		position = 1
	)
	default boolean milestonesScreenshot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "notifyCombatTasks",
		name = "Combat tasks",
		description = "Post when you complete a combat achievement task.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_MILESTONES,
		position = 2
	)
	default boolean notifyCombatTasks()
	{
		return false;
	}

	@ConfigItem(
		keyName = "combatTaskMinTier",
		name = "Combat task min tier",
		description = "Only post combat tasks of at least this tier.",
		section = SECTION_MILESTONES,
		position = 3
	)
	default CombatTier combatTaskMinTier()
	{
		return CombatTier.EASY;
	}

	@ConfigItem(
		keyName = "notifyPersonalBests",
		name = "Personal bests",
		description = "Post when you achieve a new personal best time.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_MILESTONES,
		position = 4
	)
	default boolean notifyPersonalBests()
	{
		return false;
	}

	@ConfigItem(
		keyName = "notifyPets",
		name = "Pets",
		description = "Post when you receive a pet.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_MILESTONES,
		position = 5
	)
	default boolean notifyPets()
	{
		return false;
	}

	@ConfigItem(
		keyName = "notifyLevels",
		name = "Level-ups",
		description = "Post when you level up a skill.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_MILESTONES,
		position = 6
	)
	default boolean notifyLevels()
	{
		return false;
	}

	@ConfigItem(
		keyName = "levelMode",
		name = "Level-up mode",
		description = "Which level-ups to post: every level, every N levels, or only when a skill reaches 99.",
		section = SECTION_MILESTONES,
		position = 7
	)
	default LevelNotifyMode levelMode()
	{
		return LevelNotifyMode.EVERY_LEVEL;
	}

	@Range(min = 2, max = 99)
	@ConfigItem(
		keyName = "levelInterval",
		name = "Level interval",
		description = "When level-up mode is 'Every N levels', post only on levels that are a multiple of this number.",
		section = SECTION_MILESTONES,
		position = 8
	)
	default int levelInterval()
	{
		return 10;
	}

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = "xpMilestoneInterval",
		name = "XP milestone interval",
		description = "Also post whenever a skill crosses a multiple of this many million XP. Set to 0 to disable.",
		section = SECTION_MILESTONES,
		position = 9
	)
	default int xpMilestoneInterval()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "notifyQuests",
		name = "Quests",
		description = "Post when you complete a quest.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_MILESTONES,
		position = 10
	)
	default boolean notifyQuests()
	{
		return false;
	}

	@ConfigItem(
		keyName = "notifyClues",
		name = "Clues",
		description = "Post when you complete a clue scroll.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_MILESTONES,
		position = 11
	)
	default boolean notifyClues()
	{
		return false;
	}

	@ConfigItem(
		keyName = "clueMinTier",
		name = "Clue min tier",
		description = "Only post clues of at least this tier.",
		section = SECTION_MILESTONES,
		position = 12
	)
	default CombatTier clueMinTier()
	{
		return CombatTier.BEGINNER;
	}

	@Range(min = 0)
	@ConfigItem(
		keyName = "clueMinValue",
		name = "Clue minimum value",
		description = "Only post clues whose total reward value is at least this many coins. Set to 0 to post everything.",
		section = SECTION_MILESTONES,
		position = 13
	)
	default int clueMinValue()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "pbWebhook",
		name = "Personal best webhook URL",
		secret = true,
		description = "Discord webhook URL(s) to post personal bests to. Put one URL per line to post to multiple channels. Falls back to the milestones webhook if left blank.",
		section = SECTION_MILESTONES,
		position = 14
	)
	default String pbWebhook()
	{
		return "";
	}

	@ConfigItem(
		keyName = "pbScreenshot",
		name = "Personal best screenshot",
		description = "Attach a screenshot to your own personal-best posts. Screenshots may show other players and, unless hidden, chat; the chat privacy setting applies.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_MILESTONES,
		position = 15
	)
	default boolean pbScreenshot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "combatWebhook",
		name = "Combat webhook URL",
		secret = true,
		description = "Discord webhook URL(s) to post combat events to. Put one URL per line to post to multiple channels.",
		section = SECTION_COMBAT,
		position = 0
	)
	default String combatWebhook()
	{
		return "";
	}

	@ConfigItem(
		keyName = "combatScreenshot",
		name = "Include screenshot",
		description = "Attach a screenshot to combat posts. Screenshots may show other players and, unless hidden, chat; the chat privacy setting applies.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_COMBAT,
		position = 1
	)
	default boolean combatScreenshot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "notifyDeaths",
		name = "Deaths",
		description = "Post when you die, with the value of items lost.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_COMBAT,
		position = 2
	)
	default boolean notifyDeaths()
	{
		return false;
	}

	@Range(min = 0)
	@ConfigItem(
		keyName = "deathMinValue",
		name = "Death minimum value",
		description = "Only post deaths where the value of lost items is at least this many coins. Set to 0 to post every death.",
		section = SECTION_COMBAT,
		position = 3
	)
	default int deathMinValue()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "notifyPvp",
		name = "PvP kills, deaths & loot keys",
		description = "Relay clan PvP broadcasts (kills, deaths, loot keys), including the names in the broadcast, to the configured Discord webhook.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = SECTION_COMBAT,
		position = 4
	)
	default boolean notifyPvp()
	{
		return false;
	}
}
