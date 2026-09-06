package cc.clancollective.plugin.domain;

/**
 * How often level-up notifications fire.
 */
public enum LevelNotifyMode
{
	EVERY_LEVEL("Every level"),
	INTERVAL("Every N levels"),
	NINETY_NINE_ONLY("Level 99 only");

	private final String label;

	LevelNotifyMode(final String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
