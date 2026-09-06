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
		// keep top 3 (1M + 500k + 250k), lose 100k + 50k
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
		// keep top 4, lose only the 50k item
		assertEquals(50_000, CombatNotifier.valueLost(items, 4));
	}

	@Test
	public void keepCountSpansStackedQuantities()
	{
		// a single stack of 5 identical items: keep 3, lose 2 units
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
}
