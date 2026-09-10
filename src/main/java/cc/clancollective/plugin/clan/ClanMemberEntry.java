package cc.clancollective.plugin.clan;

import java.awt.image.BufferedImage;
import java.util.Comparator;

public final class ClanMemberEntry
{
	public static final Comparator<ClanMemberEntry> RANK_THEN_NAME =
		Comparator.comparingInt(ClanMemberEntry::getRankOrder).reversed()
			.thenComparing(e -> e.getName().toLowerCase());

	private final String name;
	private final String rankTitle;
	private final int rankOrder;
	private final int world;
	private final boolean online;
	private final BufferedImage rankIcon;

	public ClanMemberEntry(final String name, final String rankTitle, final int rankOrder, final int world)
	{
		this(name, rankTitle, rankOrder, world, false, null);
	}

	public ClanMemberEntry(final String name, final String rankTitle, final int rankOrder, final int world,
		final boolean online, final BufferedImage rankIcon)
	{
		this.name = name;
		this.rankTitle = rankTitle;
		this.rankOrder = rankOrder;
		this.world = world;
		this.online = online;
		this.rankIcon = rankIcon;
	}

	public String getName()
	{
		return name;
	}

	public String getRankTitle()
	{
		return rankTitle;
	}

	public int getRankOrder()
	{
		return rankOrder;
	}

	public int getWorld()
	{
		return world;
	}

	public boolean isOnline()
	{
		return online;
	}

	public BufferedImage getRankIcon()
	{
		return rankIcon;
	}
}
