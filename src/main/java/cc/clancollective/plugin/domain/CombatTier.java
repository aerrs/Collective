package cc.clancollective.plugin.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Ordered tiers used for minimum-tier gating.
 *
 * <p>Combat achievements run Easy through Grandmaster; clue scrolls run Beginner through
 * Master. They share this single enum so a tier can be compared by {@link #ordinal()} against
 * a configured minimum. Not every value applies to both systems (clues have no Grandmaster,
 * combat tasks have no Beginner) - callers gate on the values relevant to their source.
 */
public enum CombatTier
{
	BEGINNER,
	EASY,
	MEDIUM,
	HARD,
	ELITE,
	MASTER,
	GRANDMASTER;

	private final String displayName = name().charAt(0) + name().substring(1).toLowerCase();

	@Override
	public String toString()
	{
		return displayName;
	}

	private static final Map<String, CombatTier> BY_LOWER_NAME = Arrays.stream(values())
		.collect(Collectors.toMap(t -> t.name().toLowerCase(), Function.identity()));

	@Nullable
	public static CombatTier parse(@Nullable final String name)
	{
		if (name == null)
		{
			return null;
		}
		return BY_LOWER_NAME.get(name.trim().toLowerCase());
	}
}
