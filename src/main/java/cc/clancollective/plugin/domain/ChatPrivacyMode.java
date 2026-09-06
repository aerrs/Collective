package cc.clancollective.plugin.domain;

public enum ChatPrivacyMode
{

	SHOW_ALL("Show all"),

	HIDE_SPLIT_PM("Hide private chat"),

	HIDE_ALL("Hide all chat");

	private final String displayName;

	ChatPrivacyMode(final String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
