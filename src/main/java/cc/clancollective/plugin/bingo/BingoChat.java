package cc.clancollective.plugin.bingo;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BingoChat
{
	private static final Pattern KILL_COUNT = Pattern.compile(
		"^Your (?:completion count for |subdued |completed )?(.+?) (?:(?:kill|harvest|lap|completion|chest|success) )?(?:count )?is: ?[0-9,]+");
	private static final Pattern RIFTS = Pattern.compile("^Amount of rifts you have closed: ?[0-9,]+");
	private static final Pattern DURATION = Pattern.compile(
		"(?:duration|completion time): ?([0-9]+(?::[0-9]{1,2}){1,2}(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLUE = Pattern.compile(
		"^You have completed [0-9,]+ (beginner|easy|medium|hard|elite|master) Treasure Trails?", Pattern.CASE_INSENSITIVE);

	private BingoChat()
	{
	}

	public static String killCount(final String message)
	{
		if (RIFTS.matcher(message).find())
		{
			return "Guardians of the Rift";
		}
		final Matcher m = KILL_COUNT.matcher(message);
		return m.find() ? m.group(1).trim() : null;
	}

	public static int seconds(final String message)
	{
		final Matcher m = DURATION.matcher(message);
		if (!m.find())
		{
			return -1;
		}
		double total = 0;
		for (final String part : m.group(1).split(":"))
		{
			total = total * 60 + Double.parseDouble(part);
		}
		return (int) Math.round(total);
	}

	public static String clueTier(final String message)
	{
		final Matcher m = CLUE.matcher(message);
		if (!m.find())
		{
			return null;
		}
		final String tier = m.group(1).toLowerCase(Locale.ROOT);
		return Character.toUpperCase(tier.charAt(0)) + tier.substring(1);
	}
}
