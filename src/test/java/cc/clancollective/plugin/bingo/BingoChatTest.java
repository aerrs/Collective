package cc.clancollective.plugin.bingo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BingoChatTest
{
	@Test
	public void readsKillCounts()
	{
		assertEquals("Zulrah", BingoChat.killCount("Your Zulrah kill count is: 152."));
		assertEquals("Chambers of Xeric", BingoChat.killCount("Your completed Chambers of Xeric count is: 5."));
		assertEquals("Tombs of Amascut: Expert Mode", BingoChat.killCount("Your completed Tombs of Amascut: Expert Mode count is: 3."));
		assertEquals("Barrows", BingoChat.killCount("Your Barrows chest count is: 41."));
		assertEquals("Gauntlet", BingoChat.killCount("Your Gauntlet completion count is: 12."));
		assertEquals("Wintertodt", BingoChat.killCount("Your subdued Wintertodt count is: 80."));
		assertEquals("Kree'arra", BingoChat.killCount("Your Kree'arra kill count is: 1,204."));
		assertEquals("Guardians of the Rift", BingoChat.killCount("Amount of rifts you have closed: 300."));
		assertNull(BingoChat.killCount("You have a funny feeling like you're being followed."));
	}

	@Test
	public void readsDurations()
	{
		assertEquals(83, BingoChat.seconds("Fight duration: 1:23. Personal best: 1:10."));
		assertEquals(70, BingoChat.seconds("Fight duration: 1:09.60 (new personal best)."));
		assertEquals(1513, BingoChat.seconds("Congratulations - your raid is complete! Team size: Solo Duration: 25:13 (new personal best)"));
		assertEquals(1043, BingoChat.seconds("Theatre of Blood total completion time: 17:23. Personal best: 16:01"));
		assertEquals(3923, BingoChat.seconds("Duration: 1:05:23 (new personal best)"));
		assertEquals(-1, BingoChat.seconds("Your Zulrah kill count is: 152."));
	}

	@Test
	public void readsClueTiers()
	{
		assertEquals("Hard", BingoChat.clueTier("You have completed 12 hard Treasure Trails."));
		assertEquals("Beginner", BingoChat.clueTier("You have completed 1 beginner Treasure Trail."));
		assertNull(BingoChat.clueTier("You have completed the Tutorial."));
	}
}
