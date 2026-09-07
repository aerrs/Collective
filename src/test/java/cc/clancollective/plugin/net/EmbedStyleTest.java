package cc.clancollective.plugin.net;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EmbedStyleTest
{
	@Test
	public void itemIconUsesRuneliteCache()
	{
		assertEquals("https://static.runelite.net/cache/item/icon/4151.png", EmbedStyle.itemIcon(4151));
	}

	@Test
	public void skillIconCapitalises()
	{
		assertEquals("https://oldschool.runescape.wiki/images/Attack_icon.png", EmbedStyle.skillIcon("ATTACK"));
	}

	@Test
	public void wikiSpriteResolvesUnderWikiImages()
	{
		assertTrue(EmbedStyle.clanIcon().startsWith("https://oldschool.runescape.wiki/images/"));
	}

	@Test
	public void clanRankIconSentenceCasesMultiWordTitles()
	{
		assertEquals("https://oldschool.runescape.wiki/images/Clan_icon_-_Deputy_owner.png",
			EmbedStyle.clanRankIcon("Deputy Owner"));
	}

	@Test
	public void clanRankIconHandlesSingleWordTitles()
	{
		assertEquals("https://oldschool.runescape.wiki/images/Clan_icon_-_Owner.png",
			EmbedStyle.clanRankIcon("Owner"));
	}

	@Test
	public void clanRankIconReturnsNullForBlankTitle()
	{
		assertNull(EmbedStyle.clanRankIcon(null));
		assertNull(EmbedStyle.clanRankIcon("   "));
	}
}
