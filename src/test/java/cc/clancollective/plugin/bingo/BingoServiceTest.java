package cc.clancollective.plugin.bingo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public class BingoServiceTest
{
	private final Gson gson = new Gson();

	private BingoService.Watch watch(final String json)
	{
		return gson.fromJson(json, BingoService.Watch.class).normalised();
	}

	@Test
	public void matchesOnlyWatchedItemsIgnoringCase()
	{
		final BingoService.Watch w = watch("{\"active\":true,\"drop\":[\"tanzanite fang\"],\"clog\":[\"blood moon helm\"],\"pet\":false}");
		assertTrue(w.wants(BingoService.DROP, "Tanzanite fang"));
		assertFalse(w.wants(BingoService.DROP, "Blood moon helm"));
		assertTrue(w.wants(BingoService.CLOG, "Blood moon helm"));
		assertFalse(w.wants(BingoService.PET, ""));
	}

	@Test
	public void inactiveWatchWantsNothing()
	{
		assertFalse(watch("{\"active\":false}").wants(BingoService.PET, ""));
		assertFalse(BingoService.Watch.NONE.wants(BingoService.DROP, "Raw beef"));
	}

	@Test
	public void accountHashIsScrambledAndStable()
	{
		final String once = BingoService.scramble(123456789L);
		assertEquals(32, once.length());
		assertEquals(once, BingoService.scramble(123456789L));
		assertFalse(once.contains("123456789"));
	}

	@Test
	public void boardParsesFromWatch()
	{
		final BingoService.Watch w = watch("{\"active\":true,\"event\":\"Autumn\",\"team\":\"Wolves\","
			+ "\"board\":{\"size\":3,\"color\":\"#8fa0ff\",\"points\":5,\"done\":1,\"lines\":0,\"total\":5,"
			+ "\"tiles\":[{\"pos\":1,\"name\":\"Find Beef\",\"icon\":\"Raw beef\",\"target\":1,\"progress\":1,"
			+ "\"status\":\"done\",\"mine\":true}]}}");
		final BingoBoard board = w.board.named(w.event, w.team);
		assertEquals("Wolves", board.getTeam());
		assertEquals(3, board.getSize());
		assertTrue(board.getTiles().get(0).isDone());
		assertTrue(board.getTiles().get(0).isMine());
	}

	@Test
	public void claimMessages()
	{
		final BingoService.Claims c = gson.fromJson(
			"{\"tiles\":[{\"tile\":\"3x Zulrah Uniques\",\"status\":\"open\",\"progress\":2,\"target\":3},"
				+ "{\"tile\":\"Find Beef\",\"status\":\"done\",\"progress\":1,\"target\":1}]}",
			BingoService.Claims.class);
		assertEquals("3x Zulrah Uniques 2/3", c.tiles.get(0).message());
		assertEquals("Find Beef done!", c.tiles.get(1).message());
	}

	@Test
	public void watchesSkillsForXpTiles()
	{
		final BingoService.Watch w = watch("{\"active\":true,\"xp\":[\"fishing\",\"runecraft\"]}");
		assertTrue(w.wants(BingoService.XP, "Fishing"));
		assertTrue(w.wants(BingoService.XP, "Runecraft"));
		assertFalse(w.wants(BingoService.XP, "Mining"));
		assertFalse(w.wants(BingoService.DROP, "Fishing"));
	}

	@Test
	public void watchesKillCountsTimesCluesAndLevels()
	{
		final BingoService.Watch w = watch("{\"active\":true,\"kc\":[\"zulrah\",\"theatre of blood\",\"gauntlet\",\"dagannoth rex\"],"
			+ "\"pb\":[],\"clue\":[\"hard\"],\"level\":[\"slayer\"]}");
		assertTrue(w.wants(BingoService.KC, "Zulrah"));
		assertTrue(w.wants(BingoService.KC, "Theatre of Blood: Hard Mode"));
		assertTrue(w.wants(BingoService.KC, "Gauntlet"));
		assertTrue(w.wants(BingoService.KC, "Dagannoth Rex"));
		assertFalse(w.wants(BingoService.KC, "Seers' Village Rooftop"));
		assertFalse(w.wants(BingoService.PB, "Zulrah"));
		assertTrue(w.wants(BingoService.CLUE, "Hard"));
		assertFalse(w.wants(BingoService.CLUE, "Elite"));
		assertTrue(w.wants(BingoService.LEVEL, "Slayer"));
		assertFalse(w.wants(BingoService.LEVEL, "Mining"));
	}

	@Test
	public void boardParsesStatsAndUnlocks()
	{
		final BingoService.Watch w = watch("{\"active\":true,\"board\":{\"size\":3,\"rank\":2,\"teams\":4,\"tiles\":["
			+ "{\"pos\":0,\"name\":\"500k Fishing XP\",\"kind\":\"xp\",\"img\":\"/static/img/metrics/fishing.png\",\"target\":500000}],"
			+ "\"unlocks\":[{\"tile\":\"Vorkath kill\",\"by\":[\"Zezima\"],\"at\":\"2026-11-08T20:00:00Z\"}]}}");
		assertEquals(2, w.board.getRank());
		assertEquals(4, w.board.getTeams());
		assertEquals("/static/img/metrics/fishing.png", w.board.getTiles().get(0).getImg());
		assertEquals("Vorkath kill", w.board.getUnlocks().get(0).getTile());
		assertEquals("Zezima", w.board.getUnlocks().get(0).getBy().get(0));
	}

	@Test
	public void hiddenBoardParsesPicksAndLockedTiles()
	{
		final BingoService.Watch w = watch("{\"active\":true,\"board\":{\"size\":3,\"hidden\":true,\"picks\":2,\"tiles\":["
			+ "{\"pos\":0,\"name\":\"Locked\",\"kind\":\"locked\",\"status\":\"locked\",\"img\":\"/static/img/wiki/padlock.png\"}]}}");
		assertTrue(w.board.isHidden());
		assertEquals(2, w.board.getPicks());
		assertTrue(w.board.getTiles().get(0).isLocked());
		assertFalse(w.completes(BingoService.KC, Collections.singletonMap("Locked", 1), false));
	}

	@Test
	public void killScreenshotOnlyOnCompletingKill()
	{
		final BingoService.Watch w = watch("{\"active\":true,\"kc\":[\"vorkath\"],\"board\":{\"size\":3,\"tiles\":["
			+ "{\"pos\":0,\"name\":\"3 Vorkath kills\",\"kind\":\"kc\",\"items\":[\"Vorkath\"],\"target\":3,\"progress\":1,\"status\":\"open\"}]}}");
		final Map<String, Integer> kill = Collections.singletonMap("Vorkath", 1);
		assertFalse(w.completes(BingoService.KC, kill, true));
		final BingoService.Claims.Tile reply = new BingoService.Claims.Tile();
		reply.tile = "3 Vorkath kills";
		reply.progress = 2;
		reply.status = "open";
		w.update(Collections.singletonList(reply));
		assertEquals(2, w.board.getTiles().get(0).getProgress());
		assertTrue(w.completes(BingoService.KC, kill, true));
		assertTrue(w.completes(BingoService.KC, Collections.singletonMap("Dagannoth Rex", 1), true));
	}

	@Test
	public void xpFinishesTileOnlyWhenTargetReached()
	{
		final BingoService.Watch w = watch("{\"active\":true,\"xp\":[\"attack\"],\"board\":{\"size\":3,\"tiles\":["
			+ "{\"pos\":0,\"name\":\"100 Attack XP\",\"kind\":\"xp\",\"items\":[\"Attack\"],\"target\":100,\"progress\":40,\"status\":\"open\"}]}}");
		assertFalse(w.completes(BingoService.XP, Collections.singletonMap("Attack", 59), false));
		assertTrue(w.completes(BingoService.XP, Collections.singletonMap("Attack", 60), false));
		assertFalse(w.completes(BingoService.XP, Collections.singletonMap("Mining", 500), false));
	}
}
