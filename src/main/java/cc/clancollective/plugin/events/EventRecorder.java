package cc.clancollective.plugin.events;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;

public class EventRecorder
{
	private final Map<String, Instant> firstSeen = new LinkedHashMap<>();
	private boolean recording;
	private boolean needsSeed;
	private Instant startedAt;
	private Instant stoppedAt;

	public void start()
	{
		firstSeen.clear();
		stoppedAt = null;
		startedAt = Instant.now();
		recording = true;
		needsSeed = true;
	}

	public void stop()
	{
		if (recording)
		{
			stoppedAt = Instant.now();
			recording = false;
			needsSeed = false;
		}
	}

	public void reset()
	{
		firstSeen.clear();
		recording = false;
		needsSeed = false;
		startedAt = null;
		stoppedAt = null;
	}

	public boolean seedIfNeeded(final Client client)
	{
		if (!recording || !needsSeed || client == null)
		{
			return false;
		}

		final ClanChannel channel = client.getClanChannel();
		if (channel == null)
		{
			return false;
		}
		needsSeed = false;

		final int before = firstSeen.size();

		final Player self = client.getLocalPlayer();
		if (self != null)
		{
			addIfMember(channel, self.getName());
		}

		for (final Player player : client.getTopLevelWorldView().players())
		{
			if (player != null)
			{
				addIfMember(channel, player.getName());
			}
		}

		return firstSeen.size() != before;
	}

	public boolean onPlayerSpawned(final Player player, final ClanChannel channel)
	{
		if (!recording || player == null || channel == null)
		{
			return false;
		}
		return addIfMember(channel, player.getName());
	}

	private boolean addIfMember(final ClanChannel channel, final String name)
	{
		if (name == null || name.isEmpty())
		{
			return false;
		}
		if (channel.findMember(name) == null)
		{
			return false;
		}
		if (firstSeen.containsKey(name))
		{
			return false;
		}
		firstSeen.put(name, Instant.now());
		return true;
	}

	public boolean isRecording()
	{
		return recording;
	}

	public int count()
	{
		return firstSeen.size();
	}

	public Duration elapsed()
	{
		if (startedAt == null)
		{
			return Duration.ZERO;
		}
		return Duration.between(startedAt, endReference());
	}

	private Instant endReference()
	{
		if (recording)
		{
			return Instant.now();
		}
		return stoppedAt != null ? stoppedAt : startedAt;
	}

	public List<String> names()
	{
		return Collections.unmodifiableList(new ArrayList<>(firstSeen.keySet()));
	}

	public List<String> lines()
	{
		final Instant end = endReference();
		final List<String> out = new ArrayList<>();
		for (final Map.Entry<String, Instant> entry : firstSeen.entrySet())
		{
			out.add(entry.getKey() + " - " + formatDuration(Duration.between(entry.getValue(), end)));
		}
		return out;
	}

	public String toClipboard()
	{
		return String.join(System.lineSeparator(), lines());
	}

	static String formatDuration(final Duration duration)
	{
		final long total = Math.max(0, duration.getSeconds());
		final long minutes = total / 60;
		final long seconds = total % 60;
		return String.format("%d:%02d", minutes, seconds);
	}
}
