package cc.clancollective.plugin.combat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatNotifierTest
{
	private static CombatNotifier.PricedItem item(final int quantity, final long unit)
	{
		return new CombatNotifier.PricedItem(1, quantity, unit);
	}

	@Test
	public void keepsThreeMostValuableItems()
	{
		final List<CombatNotifier.PricedItem> items = Arrays.asList(
			item(1, 1_000_000),
			item(1, 500_000),
			item(1, 250_000),
			item(1, 100_000),
			item(1, 50_000));
		assertEquals(150_000, CombatNotifier.valueLost(items, 3));
	}

	@Test
	public void skulledKeepsNothing()
	{
		final List<CombatNotifier.PricedItem> items = Arrays.asList(
			item(1, 1_000_000),
			item(1, 500_000));
		assertEquals(1_500_000, CombatNotifier.valueLost(items, 0));
	}

	@Test
	public void protectItemKeepsOneMore()
	{
		final List<CombatNotifier.PricedItem> items = Arrays.asList(
			item(1, 1_000_000),
			item(1, 500_000),
			item(1, 250_000),
			item(1, 100_000),
			item(1, 50_000));
		assertEquals(50_000, CombatNotifier.valueLost(items, 4));
	}

	@Test
	public void keepCountSpansStackedQuantities()
	{
		final List<CombatNotifier.PricedItem> items = Collections.singletonList(item(5, 10_000));
		assertEquals(20_000, CombatNotifier.valueLost(items, 3));
	}

	@Test
	public void everythingKeptWhenFewerItemsThanKeepCount()
	{
		final List<CombatNotifier.PricedItem> items = Arrays.asList(item(1, 1_000), item(1, 2_000));
		assertEquals(0, CombatNotifier.valueLost(items, 3));
	}

	@Test
	public void detectsPvpBroadcasts()
	{
		assertTrue(CombatNotifier.isPvpBroadcast("Tester has defeated Victim and received (500,000 coins)"));
		assertTrue(CombatNotifier.isPvpBroadcast("Tester has been defeated by Pker and lost (1,200,000 coins)"));
		assertTrue(CombatNotifier.isPvpBroadcast("Tester has opened a loot key worth 3,000,000 coins!"));
	}

	@Test
	public void ignoresNonPvpBroadcasts()
	{
		assertFalse(CombatNotifier.isPvpBroadcast("Tester has received a drop: Dragon claws"));
		assertFalse(CombatNotifier.isPvpBroadcast("Tester has completed a Hard combat task: Peach Conjurer"));
		assertFalse(CombatNotifier.isPvpBroadcast("Tester has reached a total level of 2000"));
	}

	@Test
	public void screenshotsOnlyWhenLocalPlayerIsSubject()
	{
		assertTrue(CombatNotifier.subjectMatches("AER5 has been defeated by Sir Towliee", "AER5"));
		assertTrue(CombatNotifier.subjectMatches("AER5 has defeated Sir Towliee", "AER5"));
		assertTrue(CombatNotifier.subjectMatches("AER5 has opened a loot key worth 3,000,000 coins!", "AER5"));
	}

	@Test
	public void doesNotScreenshotBroadcastsAboutOtherMembers()
	{
		assertFalse(CombatNotifier.subjectMatches("Joji rains has been defeated by CAPT RED BAR", "AER5"));
		assertFalse(CombatNotifier.subjectMatches("Joji rains has been defeated by AER5", "AER5"));
	}

	@Test
	public void subjectMatchIsCaseInsensitiveButNotSubstring()
	{
		assertTrue(CombatNotifier.subjectMatches("aer5 has defeated Victim", "AER5"));
		assertFalse(CombatNotifier.subjectMatches("Joji rains has been defeated by CAPT RED BAR", "Red"));
	}

	@Test
	public void subjectMatchHandlesNullRsn()
	{
		assertFalse(CombatNotifier.subjectMatches("AER5 has defeated Victim", null));
	}

	@Test
	public void extractsKillerFromDeathBroadcast()
	{
		final String message = "AER5 has been defeated by Sir Towliee";
		final int by = message.indexOf("has been defeated by ") + "has been defeated by ".length();
		assertEquals("Sir Towliee", CombatNotifier.killerAfter(message, by));
	}

	@Test
	public void extractsKillerAndStripsTrailingLootClause()
	{
		final String message = "AER5 has been defeated by Pker and lost (1,200,000) coins!";
		final int by = message.indexOf("has been defeated by ") + "has been defeated by ".length();
		assertEquals("Pker", CombatNotifier.killerAfter(message, by));
	}
}
