package cc.clancollective.plugin.clan;

public final class ClanDirectoryEntry
{
	private final String name;
	private final String slug;
	private final String blurb;
	private final String type;
	private final String region;
	private final String recruitment;
	private final int members;
	private final String cc;

	public ClanDirectoryEntry(final String name, final String slug, final String blurb, final String type,
		final String region, final String recruitment, final int members, final String cc)
	{
		this.name = name;
		this.slug = slug;
		this.blurb = blurb;
		this.type = type;
		this.region = region;
		this.recruitment = recruitment;
		this.members = members;
		this.cc = cc;
	}

	public String getName()
	{
		return name;
	}

	public String getSlug()
	{
		return slug;
	}

	public String getBlurb()
	{
		return blurb;
	}

	public String getType()
	{
		return type;
	}

	public String getRegion()
	{
		return region;
	}

	public String getRecruitment()
	{
		return recruitment;
	}

	public int getMembers()
	{
		return members;
	}

	public String getCc()
	{
		return cc;
	}
}
