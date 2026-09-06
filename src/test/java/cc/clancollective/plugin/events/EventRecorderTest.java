package cc.clancollective.plugin.events;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EventRecorder}.
 *
 * <p>Mocking notes: every mock is fully constructed before any {@code when(...)} stubbing is opened,
 * and each layer of the {@code Client -> WorldView -> players()} chain is stubbed separately rather
 * than via deep stubs. This avoids both Mockito's {@code UnfinishedStubbingException} (which fires
 * when a mock-creating helper is called inside an open stub) and the generic-wildcard mismatch on
 * {@code players()}, whose element type is captured as {@code ? extends Player}.
 */
public class EventRecorderTest
{
	// ---- helpers -------------------------------------------------------------

	private static Player player(final String name)
	{
		final Player p = mock(Player.class);
		when(p.getName()).thenReturn(name);
		return p;
	}

	/**
	 * A clan channel whose {@code findMember} returns a stub member for each named member and null
	 * for anyone else. All stubbing is done here, up front, with no mock creation inside an open stub.
	 */
	private static ClanChannel channelWith(final String... memberNames)
	{
		final ClanChannel channel = mock(ClanChannel.class);
		for (final String name : memberNames)
		{
			// Pre-create the member, then stub the exact-argument call to return it.
			final ClanChannelMember member = mock(ClanChannelMember.class);
			when(channel.findMember(name)).thenReturn(member);
		}
		// Unstubbed names return null by Mockito default, matching the real API for non-members.
		return channel;
	}

	/**
	 * Wires client.getTopLevelWorldView().players() to iterate the given players, stubbing each
	 * layer separately. The real API returns {@code IndexedObjectSet<? extends Player>} (a wildcard),
	 * so doReturn is used for players() to sidestep the generic mismatch that breaks thenReturn.
	 */
	private static void stubVisiblePlayers(final Client client, final Player... players)
	{
		final WorldView worldView = mock(WorldView.class);
		@SuppressWarnings("unchecked")
		final IndexedObjectSet<Player> playerSet = mock(IndexedObjectSet.class);
		final List<Player> snapshot = new ArrayList<>(Arrays.asList(players));

		when(client.getTopLevelWorldView()).thenReturn(worldView);
		doReturn(playerSet).when(worldView).players();
		doAnswer(inv -> snapshot.iterator()).when(playerSet).iterator();
	}

	// ---- baseline state ------------------------------------------------------

	@Test
	public void startsEmptyAndIdle()
	{
		final EventRecorder recorder = new EventRecorder();
		assertFalse(recorder.isRecording());
		assertEquals(0, recorder.count());
		assertEquals(Duration.ZERO, recorder.elapsed());
	}

	@Test
	public void startBeginsRecording()
	{
		final EventRecorder recorder = new EventRecorder();
		recorder.start();
		assertTrue(recorder.isRecording());
	}

	@Test
	public void stopFreezesState()
	{
		final EventRecorder recorder = new EventRecorder();
		recorder.start();
		recorder.stop();
		assertFalse(recorder.isRecording());
	}

	@Test
	public void resetClearsEverything()
	{
		final EventRecorder recorder = new EventRecorder();
		final Player alice = player("Alice");
		final ClanChannel channel = channelWith("Alice");
		recorder.start();
		recorder.onPlayerSpawned(alice, channel);
		recorder.stop();
		recorder.reset();
		assertFalse(recorder.isRecording());
		assertEquals(0, recorder.count());
		assertEquals(Duration.ZERO, recorder.elapsed());
	}

	// ---- onPlayerSpawned -----------------------------------------------------

	@Test
	public void spawnedClanMemberIsRecorded()
	{
		final EventRecorder recorder = new EventRecorder();
		final Player alice = player("Alice");
		final ClanChannel channel = channelWith("Alice");
		recorder.start();
		assertTrue(recorder.onPlayerSpawned(alice, channel));
		assertEquals(1, recorder.count());
		assertEquals("Alice", recorder.names().get(0));
	}

	@Test
	public void spawnedNonMemberIsIgnored()
	{
		final EventRecorder recorder = new EventRecorder();
		final Player randomer = player("Randomer");
		final ClanChannel channel = channelWith("Alice");
		recorder.start();
		assertFalse(recorder.onPlayerSpawned(randomer, channel));
		assertEquals(0, recorder.count());
	}

	@Test
	public void duplicateSpawnRecordedOnce()
	{
		final EventRecorder recorder = new EventRecorder();
		final Player alice1 = player("Alice");
		final Player alice2 = player("Alice");
		final ClanChannel channel = channelWith("Alice");
		recorder.start();
		recorder.onPlayerSpawned(alice1, channel);
		assertFalse(recorder.onPlayerSpawned(alice2, channel));
		assertEquals(1, recorder.count());
	}

	@Test
	public void nullPlayerIsIgnored()
	{
		final EventRecorder recorder = new EventRecorder();
		final ClanChannel channel = channelWith("Alice");
		recorder.start();
		assertFalse(recorder.onPlayerSpawned(null, channel));
		assertEquals(0, recorder.count());
	}

	@Test
	public void nullChannelIsIgnored()
	{
		final EventRecorder recorder = new EventRecorder();
		final Player alice = player("Alice");
		recorder.start();
		assertFalse(recorder.onPlayerSpawned(alice, null));
		assertEquals(0, recorder.count());
	}

	@Test
	public void spawnIgnoredWhenNotRecording()
	{
		final EventRecorder recorder = new EventRecorder();
		final Player alice = player("Alice");
		final ClanChannel channel = channelWith("Alice");
		// no start()
		assertFalse(recorder.onPlayerSpawned(alice, channel));
		assertEquals(0, recorder.count());
	}

	@Test
	public void namelessPlayerIsIgnored()
	{
		final EventRecorder recorder = new EventRecorder();
		final Player noName = player(null);
		final Player emptyName = player("");
		final ClanChannel channel = channelWith("Alice");
		recorder.start();
		assertFalse(recorder.onPlayerSpawned(noName, channel));
		assertFalse(recorder.onPlayerSpawned(emptyName, channel));
		assertEquals(0, recorder.count());
	}

	// ---- seedIfNeeded --------------------------------------------------------

	@Test
	public void seedRecordsLocalAndVisibleClanMembers()
	{
		final EventRecorder recorder = new EventRecorder();
		// Build every mock first, before any chain stubbing.
		final Player me = player("Me");
		final Player alice = player("Alice");
		final Player randomer = player("Randomer");
		final Player bob = player("Bob");
		final ClanChannel channel = channelWith("Me", "Alice", "Bob");
		final Client client = mock(Client.class);

		when(client.getClanChannel()).thenReturn(channel);
		when(client.getLocalPlayer()).thenReturn(me);
		stubVisiblePlayers(client, alice, randomer, bob);

		recorder.start();
		assertTrue(recorder.seedIfNeeded(client));

		final List<String> names = recorder.names();
		assertTrue(names.contains("Me"));
		assertTrue(names.contains("Alice"));
		assertTrue(names.contains("Bob"));
		assertFalse(names.contains("Randomer"));
		assertEquals(3, recorder.count());
	}

	@Test
	public void seedRunsOnlyOnce()
	{
		final EventRecorder recorder = new EventRecorder();
		final Player alice = player("Alice");
		final ClanChannel channel = channelWith("Alice");
		final Client client = mock(Client.class);

		when(client.getClanChannel()).thenReturn(channel);
		when(client.getLocalPlayer()).thenReturn(null);
		stubVisiblePlayers(client, alice);

		recorder.start();
		assertTrue(recorder.seedIfNeeded(client));
		// A second seed is a no-op; attendees must not be duplicated.
		assertFalse(recorder.seedIfNeeded(client));
		assertEquals(1, recorder.count());
	}

	@Test
	public void seedWithNoClanDoesNotCrash()
	{
		final EventRecorder recorder = new EventRecorder();
		final Client client = mock(Client.class);
		when(client.getClanChannel()).thenReturn(null);

		recorder.start();
		assertFalse(recorder.seedIfNeeded(client));
		assertEquals(0, recorder.count());
	}

	@Test
	public void seedIgnoredWhenNotRecording()
	{
		final EventRecorder recorder = new EventRecorder();
		final Client client = mock(Client.class);
		assertFalse(recorder.seedIfNeeded(client));
	}

	@Test
	public void memberFromSeedAndLaterSpawnRecordedOnce()
	{
		final EventRecorder recorder = new EventRecorder();
		final Player aliceSeed = player("Alice");
		final Player aliceSpawn = player("Alice");
		final ClanChannel channel = channelWith("Alice");
		final Client client = mock(Client.class);

		when(client.getClanChannel()).thenReturn(channel);
		when(client.getLocalPlayer()).thenReturn(null);
		stubVisiblePlayers(client, aliceSeed);

		recorder.start();
		recorder.seedIfNeeded(client);
		assertEquals(1, recorder.count());

		// Alice later fires a PlayerSpawned too; she must not be double-counted.
		assertFalse(recorder.onPlayerSpawned(aliceSpawn, channel));
		assertEquals(1, recorder.count());
		assertEquals(Arrays.asList("Alice"), recorder.names());
	}

	// ---- clipboard / ordering ------------------------------------------------

	@Test
	public void clipboardListsAttendeesInInsertionOrder()
	{
		final EventRecorder recorder = new EventRecorder();
		final Player alice = player("Alice");
		final Player bob = player("Bob");
		final Player charlie = player("Charlie");
		final ClanChannel channel = channelWith("Alice", "Bob", "Charlie");
		recorder.start();
		recorder.onPlayerSpawned(alice, channel);
		recorder.onPlayerSpawned(bob, channel);
		recorder.onPlayerSpawned(charlie, channel);

		assertEquals(Arrays.asList("Alice", "Bob", "Charlie"), recorder.names());
		assertEquals("Alice" + System.lineSeparator() + "Bob" + System.lineSeparator() + "Charlie",
			recorder.toClipboard());
	}

	@Test
	public void restartClearsPreviousAttendees()
	{
		final EventRecorder recorder = new EventRecorder();
		final Player alice = player("Alice");
		final Player bob = player("Bob");
		final ClanChannel channel = channelWith("Alice", "Bob");
		recorder.start();
		recorder.onPlayerSpawned(alice, channel);
		recorder.stop();

		recorder.start();
		assertEquals(0, recorder.count());
		recorder.onPlayerSpawned(bob, channel);
		assertEquals(Arrays.asList("Bob"), recorder.names());
	}
}
