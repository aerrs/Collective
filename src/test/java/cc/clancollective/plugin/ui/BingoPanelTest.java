package cc.clancollective.plugin.ui;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import org.junit.Test;

public class BingoPanelTest
{
	private static final Instant NOW = Instant.parse("2026-11-08T21:00:00Z");

	@Test
	public void shortensBigXpNumbers()
	{
		assertEquals("9999", BingoPanel.shortNumber(9999));
		assertEquals("12.3k", BingoPanel.shortNumber(12_345));
		assertEquals("500k", BingoPanel.shortNumber(500_000));
		assertEquals("1.5m", BingoPanel.shortNumber(1_500_000));
		assertEquals("13m", BingoPanel.shortNumber(13_034_431));
	}

	@Test
	public void agoIsShortAndSafe()
	{
		assertEquals("just now", BingoPanel.ago("2026-11-08T20:59:40Z", NOW));
		assertEquals("12m ago", BingoPanel.ago("2026-11-08T20:48:00Z", NOW));
		assertEquals("5h ago", BingoPanel.ago("2026-11-08T16:00:00Z", NOW));
		assertEquals("3d ago", BingoPanel.ago("2026-11-05T21:00:00Z", NOW));
		assertEquals("", BingoPanel.ago("not a date", NOW));
		assertEquals("", BingoPanel.ago(null, NOW));
	}

	@Test
	public void initialsFromTileName()
	{
		assertEquals("1BC", BingoPanel.initials("1 Beginner clue"));
		assertEquals("", BingoPanel.initials(null));
	}
}
