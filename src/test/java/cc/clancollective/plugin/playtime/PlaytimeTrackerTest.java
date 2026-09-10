package cc.clancollective.plugin.playtime;

import cc.clancollective.plugin.CollectiveConfig;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlaytimeTrackerTest
{
	private ConfigManager configManager;
	private Client client;
	private CollectiveConfig config;
	private PlaytimeTracker tracker;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		config = mock(CollectiveConfig.class);
		configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(CollectiveConfig.GROUP, PlaytimeTracker.UNSENT_KEY))
			.thenReturn(null);

		tracker = new PlaytimeTracker(client, config, configManager, new OkHttpClient(), new Gson());
	}

	@Test
	public void rejectsInsecureBackendButAllowsHttpsAndLocalhost()
	{
		assertTrue(PlaytimeTracker.isSecure(HttpUrl.parse("https://clancollective.cc")));
		assertTrue(PlaytimeTracker.isSecure(HttpUrl.parse("http://localhost:8080")));
		assertTrue(PlaytimeTracker.isSecure(HttpUrl.parse("http://127.0.0.1:5000")));
		assertFalse(PlaytimeTracker.isSecure(HttpUrl.parse("http://evil.example.com")));
		assertFalse(PlaytimeTracker.isSecure(null));
	}

	@Test
	public void disabledFeatureDoesNotAccrue()
	{
		when(config.playtimeEnabled()).thenReturn(false);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

		tracker.onLogin();
		tracker.onGameTick();
		tracker.onGameTick();
		tracker.onLogout();

		assertEquals(0, tracker.pendingSeconds());
	}

	@Test
	public void accumulatesWholeSecondsAndCarriesRemainder()
	{
		tracker.recordElapsed(1500, "Clan", "Tester", 100L);
		assertEquals(1, tracker.pendingSeconds());

		tracker.recordElapsed(600, "Clan", "Tester", 100L);
		assertEquals(2, tracker.pendingSeconds());
	}

	@Test
	public void subSecondSlicesEventuallyFormSeconds()
	{
		for (int i = 0; i < 10; i++)
		{
			tracker.recordElapsed(100, "Clan", "Tester", 100L);
		}
		assertEquals(1, tracker.pendingSeconds());
	}

	@Test
	public void successfulSendDeductsSentAmount()
	{
		tracker.recordElapsed(5000, "Clan", "Tester", 100L);
		assertEquals(5, tracker.pendingSeconds());

		tracker.onSendResult(true, new PlaytimeTracker.Bucket("Clan", "Tester"), 5);
		assertEquals(0, tracker.pendingSeconds());
	}

	@Test
	public void failedSendKeepsPending()
	{
		tracker.recordElapsed(5000, "Clan", "Tester", 100L);
		tracker.onSendResult(false, new PlaytimeTracker.Bucket("Clan", "Tester"), 5);
		assertEquals(5, tracker.pendingSeconds());
	}

	@Test
	public void sendDeductsOnlyWhatWasSentSoNewTimeSurvives()
	{
		tracker.recordElapsed(5000, "Clan", "Tester", 100L);
		tracker.recordElapsed(3000, "Clan", "Tester", 100L);
		tracker.onSendResult(true, new PlaytimeTracker.Bucket("Clan", "Tester"), 5);
		assertEquals(3, tracker.pendingSeconds());
	}

	@Test
	public void timeIsKeptSeparatePerClanAndRsn()
	{
		tracker.recordElapsed(4000, "ClanA", "Tester", 100L);
		tracker.recordElapsed(6000, "ClanB", "Tester", 100L);
		tracker.recordElapsed(2000, "ClanA", "Other", 100L);

		assertEquals(4, tracker.pendingSeconds("ClanA", "Tester"));
		assertEquals(6, tracker.pendingSeconds("ClanB", "Tester"));
		assertEquals(2, tracker.pendingSeconds("ClanA", "Other"));
		assertEquals(12, tracker.pendingSeconds());
	}

	@Test
	public void sendOnlyDeductsFromItsOwnBucket()
	{
		tracker.recordElapsed(4000, "ClanA", "Tester", 100L);
		tracker.recordElapsed(6000, "ClanB", "Tester", 100L);

		tracker.onSendResult(true, new PlaytimeTracker.Bucket("ClanA", "Tester"), 4);

		assertEquals(0, tracker.pendingSeconds("ClanA", "Tester"));
		assertEquals(6, tracker.pendingSeconds("ClanB", "Tester"));
	}

	@Test
	public void loadsPersistedPendingOnConstruction()
	{
		when(configManager.getConfiguration(CollectiveConfig.GROUP, PlaytimeTracker.UNSENT_KEY))
			.thenReturn("[{\"clan\":\"Clan\",\"rsn\":\"Tester\",\"seconds\":42}]");
		final PlaytimeTracker restored = new PlaytimeTracker(mock(Client.class), mock(CollectiveConfig.class),
			configManager, new OkHttpClient(), new Gson());
		assertEquals(42, restored.pendingSeconds());
		assertEquals(42, restored.pendingSeconds("Clan", "Tester"));
	}

	@Test
	public void ignoresLegacyScalarPersistedValue()
	{
		when(configManager.getConfiguration(CollectiveConfig.GROUP, PlaytimeTracker.UNSENT_KEY))
			.thenReturn("42");
		final PlaytimeTracker restored = new PlaytimeTracker(mock(Client.class), mock(CollectiveConfig.class),
			configManager, new OkHttpClient(), new Gson());
		assertEquals(0, restored.pendingSeconds());
	}
}
