package cc.clancollective.plugin.clan;

import java.util.Collections;
import java.util.List;

/**
 * Immutable clan stats fetched from Clan Collective (which sources them from
 * Wise Old Man / TempleOSRS). Numeric fields may be null when the clan is
 * listed but has no synced tracker data yet.
 */
public final class ClanStats
{
	public enum State
	{
		/** Stats were returned (fields may still be null if not yet synced). */
		OK,
		/** The clan is not listed on Clan Collective. */
		NOT_FOUND,
		/** The request failed (network, server, or parse error). */
		ERROR
	}

	private final State state;
	private final String clanName;
	private final String source;
	private final Integer ehp;
	private final Integer ehb;
	private final Long totalXp;
	private final Integer memberCount;
	private final String syncedAt;
	private final Weekly weekly;

	private ClanStats(final State state, final String clanName, final String source,
		final Integer ehp, final Integer ehb, final Long totalXp, final Integer memberCount,
		final String syncedAt, final Weekly weekly)
	{
		this.state = state;
		this.clanName = clanName;
		this.source = source;
		this.ehp = ehp;
		this.ehb = ehb;
		this.totalXp = totalXp;
		this.memberCount = memberCount;
		this.syncedAt = syncedAt;
		this.weekly = weekly;
	}

	public static ClanStats ok(final String clanName, final String source, final Integer ehp,
		final Integer ehb, final Long totalXp, final Integer memberCount, final String syncedAt,
		final Weekly weekly)
	{
		return new ClanStats(State.OK, clanName, source, ehp, ehb, totalXp, memberCount, syncedAt, weekly);
	}

	public static ClanStats notFound()
	{
		return new ClanStats(State.NOT_FOUND, null, null, null, null, null, null, null, null);
	}

	public static ClanStats error()
	{
		return new ClanStats(State.ERROR, null, null, null, null, null, null, null, null);
	}

	public State getState()
	{
		return state;
	}

	public String getClanName()
	{
		return clanName;
	}

	public String getSource()
	{
		return source;
	}

	public Integer getEhp()
	{
		return ehp;
	}

	public Integer getEhb()
	{
		return ehb;
	}

	public Long getTotalXp()
	{
		return totalXp;
	}

	public Integer getMemberCount()
	{
		return memberCount;
	}

	public String getSyncedAt()
	{
		return syncedAt;
	}

	public Weekly getWeekly()
	{
		return weekly;
	}

	public boolean hasAnyStats()
	{
		return ehp != null || ehb != null || totalXp != null;
	}

	/** Weekly efficiency gains for the clan: totals plus top contributors. */
	public static final class Weekly
	{
		private final Double ehpGained;
		private final Double ehbGained;
		private final List<Contributor> ehpTop;
		private final List<Contributor> ehbTop;

		public Weekly(final Double ehpGained, final Double ehbGained,
			final List<Contributor> ehpTop, final List<Contributor> ehbTop)
		{
			this.ehpGained = ehpGained;
			this.ehbGained = ehbGained;
			this.ehpTop = ehpTop != null ? ehpTop : Collections.emptyList();
			this.ehbTop = ehbTop != null ? ehbTop : Collections.emptyList();
		}

		public Double getEhpGained()
		{
			return ehpGained;
		}

		public Double getEhbGained()
		{
			return ehbGained;
		}

		public List<Contributor> getEhpTop()
		{
			return ehpTop;
		}

		public List<Contributor> getEhbTop()
		{
			return ehbTop;
		}

		public boolean hasData()
		{
			return (ehpGained != null && ehpGained > 0) || (ehbGained != null && ehbGained > 0);
		}
	}

	/** A single member's contribution to a weekly gains leaderboard. */
	public static final class Contributor
	{
		private final String rsn;
		private final double gained;

		public Contributor(final String rsn, final double gained)
		{
			this.rsn = rsn;
			this.gained = gained;
		}

		public String getRsn()
		{
			return rsn;
		}

		public double getGained()
		{
			return gained;
		}
	}
}
