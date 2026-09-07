package cc.clancollective.plugin.milestones;

import cc.clancollective.plugin.domain.LevelNotifyMode;
import java.util.List;
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

	@Test
	public void matchesNewPersonalBestFromFirstPersonMessage()
	{
		assertEquals("1:23", MilestoneNotifier.matchNewPbTime(
			"Fight duration: <col=ff0000>1:23</col> (new personal best)"));
		assertEquals("0:45", MilestoneNotifier.matchNewPbTime(
			"Subdued in <col=ff0000>0:45</col> (new personal best)"));
		assertEquals("22:00", MilestoneNotifier.matchNewPbTime(
			"<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: "
				+ "<col=ff0000>3 players</col> Duration:</col> <col=ff0000>22:00</col> (new personal best)</col>"));
	}

	@Test
	public void ignoresNonNewPersonalBestMessages()
	{
		assertNull(MilestoneNotifier.matchNewPbTime(
			"Fight duration: <col=ff0000>1:23</col>. Personal best: 1:20"));
		assertNull(MilestoneNotifier.matchNewPbTime("You have received a drop."));
	}

	@Test
	public void detectsPersonalBestBroadcast()
	{
		assertTrue(MilestoneNotifier.isPersonalBestBroadcast(
			"TMMW, jogrefruitt, Im Her Steve and AER5 achieved a new Tombs of Amascut (team size: 4) "
				+ "Normal mode Challenge personal best: 22:28"));
		assertFalse(MilestoneNotifier.isPersonalBestBroadcast("AER5 has joined the clan."));
	}

	@Test
	public void parsesTeamPersonalBestBroadcast()
	{
		final MilestoneNotifier.PbBroadcast pb = MilestoneNotifier.parsePbBroadcast(
			"TMMW, jogrefruitt, Im Her Steve and AER5 achieved a new Tombs of Amascut (team size: 4) "
				+ "Normal mode Challenge personal best: 22:28");
		assertEquals(List.of("TMMW", "jogrefruitt", "Im Her Steve", "AER5"), pb.names);
		assertEquals("Tombs of Amascut (team size: 4) Normal mode Challenge", pb.activity);
		assertEquals("22:28", pb.time);
	}
}
