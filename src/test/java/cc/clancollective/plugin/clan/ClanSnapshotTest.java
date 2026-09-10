package cc.clancollective.plugin.clan;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClanSnapshotTest
{
	@Test
	public void emptyIsNotInClan()
	{
		assertFalse(ClanSnapshot.EMPTY.isInClan());
		assertEquals(0, ClanSnapshot.EMPTY.getOnlineCount());
	}

	@Test
	public void nullClanNameIsNotInClan()
	{
		assertFalse(ClanSnapshot.of(null, null, 0, Collections.emptyList()).isInClan());
		assertFalse(ClanSnapshot.of("", null, 0, Collections.emptyList()).isInClan());
	}

	@Test
	public void ordersByRankThenName()
	{
		final ClanMemberEntry owner = new ClanMemberEntry("Zed", "Owner", 270, 302);
		final ClanMemberEntry general = new ClanMemberEntry("Amy", "General", 100, 330);
		final ClanMemberEntry memberB = new ClanMemberEntry("bob", "Member", 0, 420);
		final ClanMemberEntry memberA = new ClanMemberEntry("alice", "Member", 0, 301);

		final ClanSnapshot snapshot = ClanSnapshot.of(
			"My Clan", "Owner", 200, Arrays.asList(memberB, general, memberA, owner));

		assertTrue(snapshot.isInClan());
		assertEquals("My Clan", snapshot.getClanName());
		assertEquals(4, snapshot.getOnlineCount());
		assertEquals(200, snapshot.getTotalMembers());

		// Highest rank first; ties broken alphabetically (case-insensitive).
		assertEquals("Zed", snapshot.getOnlineMembers().get(0).getName());
		assertEquals("Amy", snapshot.getOnlineMembers().get(1).getName());
		assertEquals("alice", snapshot.getOnlineMembers().get(2).getName());
		assertEquals("bob", snapshot.getOnlineMembers().get(3).getName());
	}

	@Test
	public void onlineMembersListIsUnmodifiable()
	{
		final ClanSnapshot snapshot = ClanSnapshot.of("Clan", "Member", 1,
			Collections.singletonList(new ClanMemberEntry("A", "Member", 0, 301)));
		try
		{
			snapshot.getOnlineMembers().add(new ClanMemberEntry("B", "Member", 0, 302));
			throw new AssertionError("expected unmodifiable list");
		}
		catch (UnsupportedOperationException expected)
		{
			// ok
		}
	}

	@Test
	public void negativeTotalIsClampedToZero()
	{
		assertEquals(0, ClanSnapshot.of("Clan", null, -5, Collections.emptyList()).getTotalMembers());
	}
}
