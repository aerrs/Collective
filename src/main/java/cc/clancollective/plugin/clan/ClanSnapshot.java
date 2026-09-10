package cc.clancollective.plugin.clan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClanSnapshot
{
	public static final ClanSnapshot EMPTY =
		new ClanSnapshot(null, null, 0, Collections.emptyList(), Collections.emptyList());

	private final String clanName;
	private final String localRankTitle;
	private final int totalMembers;
	private final List<ClanMemberEntry> onlineMembers;
	private final List<ClanMemberEntry> roster;

	private ClanSnapshot(final String clanName, final String localRankTitle, final int totalMembers,
		final List<ClanMemberEntry> onlineMembers, final List<ClanMemberEntry> roster)
	{
		this.clanName = clanName;
		this.localRankTitle = localRankTitle;
		this.totalMembers = totalMembers;
		this.onlineMembers = onlineMembers;
		this.roster = roster;
	}

	public static ClanSnapshot of(final String clanName, final String localRankTitle, final int totalMembers,
		final List<ClanMemberEntry> onlineMembers)
	{
		return of(clanName, localRankTitle, totalMembers, onlineMembers, Collections.emptyList());
	}

	public static ClanSnapshot of(final String clanName, final String localRankTitle, final int totalMembers,
		final List<ClanMemberEntry> onlineMembers, final List<ClanMemberEntry> roster)
	{
		return new ClanSnapshot(clanName, localRankTitle, Math.max(0, totalMembers),
			sortedCopy(onlineMembers), sortedCopy(roster));
	}

	private static List<ClanMemberEntry> sortedCopy(final List<ClanMemberEntry> members)
	{
		final List<ClanMemberEntry> sorted = new ArrayList<>(
			members != null ? members : Collections.emptyList());
		sorted.sort(ClanMemberEntry.RANK_THEN_NAME);
		return Collections.unmodifiableList(sorted);
	}

	public boolean isInClan()
	{
		return clanName != null && !clanName.isEmpty();
	}

	public String getClanName()
	{
		return clanName;
	}

	public String getLocalRankTitle()
	{
		return localRankTitle;
	}

	public int getTotalMembers()
	{
		return totalMembers;
	}

	public int getOnlineCount()
	{
		return onlineMembers.size();
	}

	public List<ClanMemberEntry> getOnlineMembers()
	{
		return onlineMembers;
	}

	public List<ClanMemberEntry> getRoster()
	{
		return roster;
	}
}
