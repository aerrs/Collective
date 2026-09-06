package cc.clancollective.plugin.domain;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CombatTierTest
{
	@Test
	public void parsesCaseInsensitively()
	{
		assertEquals(CombatTier.HARD, CombatTier.parse("Hard"));
		assertEquals(CombatTier.HARD, CombatTier.parse("hard"));
		assertEquals(CombatTier.GRANDMASTER, CombatTier.parse("GRANDMASTER"));
		assertEquals(CombatTier.BEGINNER, CombatTier.parse(" beginner "));
	}

	@Test
	public void returnsNullForUnknown()
	{
		assertNull(CombatTier.parse("nonsense"));
		assertNull(CombatTier.parse(null));
	}

	@Test
	public void ordinalGatingHoldsExpectedOrder()
	{
		assertTrue(CombatTier.ELITE.ordinal() > CombatTier.HARD.ordinal());
		assertTrue(CombatTier.MASTER.ordinal() >= CombatTier.MASTER.ordinal());
		assertTrue(CombatTier.EASY.ordinal() > CombatTier.BEGINNER.ordinal());
	}

	@Test
	public void displayNameIsCapitalised()
	{
		assertEquals("Grandmaster", CombatTier.GRANDMASTER.toString());
		assertEquals("Easy", CombatTier.EASY.toString());
	}
}
