package cc.clancollective.plugin.milestones;

import cc.clancollective.plugin.domain.LevelNotifyMode;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MilestoneNotifierTest
{
	@Test
	public void everyLevelAlwaysNotifies()
	{
		assertTrue(MilestoneNotifier.shouldNotifyLevel(LevelNotifyMode.EVERY_LEVEL, 10, 2));
		assertTrue(MilestoneNotifier.shouldNotifyLevel(LevelNotifyMode.EVERY_LEVEL, 10, 47));
		assertTrue(MilestoneNotifier.shouldNotifyLevel(LevelNotifyMode.EVERY_LEVEL, 10, 99));
	}

	@Test
	public void ninetyNineOnlyNotifiesAtNinetyNine()
	{
		assertFalse(MilestoneNotifier.shouldNotifyLevel(LevelNotifyMode.NINETY_NINE_ONLY, 10, 98));
		assertTrue(MilestoneNotifier.shouldNotifyLevel(LevelNotifyMode.NINETY_NINE_ONLY, 10, 99));
	}

	@Test
	public void intervalNotifiesOnMultiplesAndAtNinetyNine()
	{
		assertTrue(MilestoneNotifier.shouldNotifyLevel(LevelNotifyMode.INTERVAL, 10, 10));
		assertTrue(MilestoneNotifier.shouldNotifyLevel(LevelNotifyMode.INTERVAL, 10, 50));
		assertFalse(MilestoneNotifier.shouldNotifyLevel(LevelNotifyMode.INTERVAL, 10, 47));
		// 99 is not a multiple of 10 but a maxed skill should always post
		assertTrue(MilestoneNotifier.shouldNotifyLevel(LevelNotifyMode.INTERVAL, 10, 99));
	}

	@Test
	public void intervalWithFiveStep()
	{
		assertTrue(MilestoneNotifier.shouldNotifyLevel(LevelNotifyMode.INTERVAL, 5, 15));
		assertFalse(MilestoneNotifier.shouldNotifyLevel(LevelNotifyMode.INTERVAL, 5, 16));
	}

	@Test
	public void parsesQuestTitleStrippingPrefixAndPunctuation()
	{
		assertEquals("Dragon Slayer II",
			MilestoneNotifier.parseQuestTitle("You have completed Dragon Slayer II!"));
		assertEquals("Cook's Assistant",
			MilestoneNotifier.parseQuestTitle("Congratulations! You have completed Cook's Assistant."));
		assertEquals("Recipe for Disaster",
			MilestoneNotifier.parseQuestTitle("<col=ff0000>Recipe for Disaster</col>"));
	}

	@Test
	public void questTitleReturnsNullWhenEmpty()
	{
		assertNull(MilestoneNotifier.parseQuestTitle(null));
		assertNull(MilestoneNotifier.parseQuestTitle("You have completed"));
	}
}
