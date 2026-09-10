package cc.clancollective.plugin.clan;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.Text;

@Singleton
public class ClanInfoService
{
	private final Client client;
	private final ChatIconManager chatIconManager;

	@Inject
	public ClanInfoService(final Client client, final ChatIconManager chatIconManager)
	{
		this.client = client;
		this.chatIconManager = chatIconManager;
	}

	public ClanSnapshot snapshot()
	{
		final ClanChannel channel = client.getClanChannel();
		if (channel == null)
		{
			return ClanSnapshot.EMPTY;
		}

		final String clanName = channel.getName();
		if (clanName == null || clanName.isEmpty())
		{
			return ClanSnapshot.EMPTY;
		}

		final ClanSettings settings = client.getClanSettings();
		final int totalMembers = settings != null && settings.getMembers() != null
			? settings.getMembers().size()
			: channel.getMembers().size();

		final List<ClanMemberEntry> online = new ArrayList<>();
		for (final ClanChannelMember member : channel.getMembers())
		{
			if (member == null || member.getName() == null)
			{
				continue;
			}
			final ClanRank rank = member.getRank();
			online.add(new ClanMemberEntry(
				Text.toJagexName(member.getName()),
				titleFor(settings, rank),
				rank != null ? rank.getRank() : Integer.MIN_VALUE,
				member.getWorld()));
		}

		return ClanSnapshot.of(clanName, localRankTitle(channel, settings), totalMembers, online,
			buildRoster(channel, settings));
	}

	private List<ClanMemberEntry> buildRoster(final ClanChannel channel, final ClanSettings settings)
	{
		final List<ClanMemberEntry> roster = new ArrayList<>();
		if (settings == null || settings.getMembers() == null)
		{
			return roster;
		}

		for (final ClanMember member : settings.getMembers())
		{
			if (member == null || member.getName() == null)
			{
				continue;
			}
			final ClanRank rank = member.getRank();
			final String name = Text.toJagexName(member.getName());
			roster.add(new ClanMemberEntry(
				name,
				titleFor(settings, rank),
				rank != null ? rank.getRank() : Integer.MIN_VALUE,
				-1,
				channel.findMember(member.getName()) != null,
				rankIcon(settings, rank)));
		}
		return roster;
	}

	private BufferedImage rankIcon(final ClanSettings settings, final ClanRank rank)
	{
		if (settings == null || rank == null)
		{
			return null;
		}
		final ClanTitle title = settings.titleForRank(rank);
		if (title == null)
		{
			return null;
		}
		try
		{
			return chatIconManager.getRankImage(title);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private String localRankTitle(final ClanChannel channel, final ClanSettings settings)
	{
		final Player self = client.getLocalPlayer();
		if (self == null || self.getName() == null)
		{
			return null;
		}
		final ClanChannelMember me = channel.findMember(self.getName());
		if (me == null)
		{
			return null;
		}
		return titleFor(settings, me.getRank());
	}

	private String titleFor(final ClanSettings settings, final ClanRank rank)
	{
		if (rank == null)
		{
			return "Member";
		}
		if (settings != null)
		{
			final ClanTitle title = settings.titleForRank(rank);
			if (title != null && title.getName() != null && !title.getName().isEmpty())
			{
				return title.getName();
			}
		}
		return "Member";
	}
}
