package cc.clancollective.plugin.playtime;

public final class PlaytimeEntry
{
	private final String rsn;
	private final long seconds;

	public PlaytimeEntry(final String rsn, final long seconds)
	{
		this.rsn = rsn;
		this.seconds = seconds;
	}

	public String getRsn()
	{
		return rsn;
	}

	public long getSeconds()
	{
		return seconds;
	}
}
