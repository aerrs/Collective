package cc.clancollective.plugin.net;

import java.awt.Color;

public final class EmbedStyle
{
	private static final String ITEM_ICON_BASE = "https://static.runelite.net/cache/item/icon/";
	private static final String WIKI_IMAGE_BASE = "https://oldschool.runescape.wiki/images/";

	public static final Color DROP = new Color(0xC8, 0xA2, 0x3C);
	public static final Color LEVEL = new Color(0x4C, 0xAF, 0x50);
	public static final Color QUEST = new Color(0x3F, 0x8F, 0xD6);
	public static final Color PET = new Color(0xC8, 0x6F, 0xD4);
	public static final Color DEATH = new Color(0xC0, 0x39, 0x2B);
	public static final Color CLUE = new Color(0xD8, 0x9B, 0x2A);
	public static final Color CLAN_CHAT = new Color(0x60, 0x78, 0xF8);
	public static final Color BROADCAST = new Color(0x50, 0x60, 0xE0);
	public static final Color CLAN_ADMIN = new Color(0xE0, 0x8A, 0x2C);
	public static final Color RECRUIT = new Color(0x60, 0x78, 0xF8);
	public static final Color JOIN = new Color(0x4C, 0xAF, 0x50);
	public static final Color LEAVE = new Color(0xC0, 0x39, 0x2B);
	public static final Color CA = new Color(0xC8, 0xA2, 0x3C);
	public static final Color PB = new Color(0xC8, 0xA2, 0x3C);
	public static final Color PK = new Color(0xD8, 0x9B, 0x2A);
	public static final Color LOOTKEY = new Color(0xD8, 0x9B, 0x2A);

	private EmbedStyle()
	{
	}

	public static String itemIcon(final int itemId)
	{
		return ITEM_ICON_BASE + itemId + ".png";
	}

	public static String wikiSprite(final String fileName)
	{
		return WIKI_IMAGE_BASE + fileName;
	}

	public static String skillIcon(final String skillName)
	{
		final String name = skillName.substring(0, 1).toUpperCase() + skillName.substring(1).toLowerCase();
		return wikiSprite(name + "_icon.png");
	}

	public static String dropIcon()
	{
		return wikiSprite("Coins_10000.png");
	}

	public static String questIcon()
	{
		return wikiSprite("Quest_point_icon.png");
	}

	public static String petIcon()
	{
		return wikiSprite("Call_follower.png");
	}

	public static String deathIcon()
	{
		return wikiSprite("Skull_%28status%29_icon.png");
	}

	public static String clueIcon()
	{
		return wikiSprite("Clue_scroll_%28master%29.png");
	}

	public static String combatTaskIcon()
	{
		return wikiSprite("Combat_Achievements_icon.png");
	}

	public static String pbIcon()
	{
		return wikiSprite("Stopwatch.png");
	}

	public static String pkIcon()
	{
		return wikiSprite("Skull_%28status%29_icon.png");
	}

	public static String lootKeyIcon()
	{
		return wikiSprite("Loot_key_detail.png");
	}

	public static String clanIcon()
	{
		return wikiSprite("Clan_icon_-_default.png");
	}

	public static String clanRankIcon(final String rankTitle)
	{
		if (rankTitle == null || rankTitle.trim().isEmpty())
		{
			return null;
		}
		final String trimmed = rankTitle.trim();
		final String sentence = trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1).toLowerCase();
		final String name = sentence.replace(' ', '_');
		return wikiSprite("Clan_icon_-_" + name + ".png");
	}
}
