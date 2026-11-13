package cc.clancollective.plugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(CollectiveConfig.GROUP)
public interface CollectiveConfig extends Config
{
	String GROUP = "clancollective";
	String CLANS = "clans";
	String WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";

	@ConfigItem(
		keyName = "bingo",
		name = "Clan bingo",
		description = "While your clan runs a bingo on Clan Collective (clancollective.cc) and you are on a team, "
			+ "drops, collection log slots, pets, XP, levels, kill counts, kill times and clues that match an open tile "
			+ "are sent there. Sends your clan name, RSN, a one-way hash of your account and the matching names and amounts.",
		warning = WARNING,
		position = 0
	)
	default boolean bingo()
	{
		return false;
	}

	@ConfigItem(
		keyName = "bingoScreenshots",
		name = "Bingo screenshots",
		description = "Attach a screenshot of your game when something ticks a bingo tile, so captains can check it. "
			+ "Kill counts and XP only on the kill or gain that finishes the tile. Chat is hidden.",
		warning = WARNING,
		position = 1
	)
	default boolean bingoScreenshots()
	{
		return false;
	}

	@ConfigItem(
		keyName = CLANS,
		name = "Clan listings",
		description = "Show your clan's Clan Collective (clancollective.cc) listing in the side panel, "
			+ "or clans to browse when you are not in one. Sends your clan name.",
		warning = WARNING,
		position = 2
	)
	default boolean clans()
	{
		return false;
	}
}
