package cc.clancollective.plugin.events;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;

public class EventRecorder
{
	private final Set<String> attendees = new LinkedHashSet<>();
	private boolean recording;
	private boolean needsSeed;
	private Instant startedAt;
	private Duration frozenElapsed = Duration.ZERO;

	public void start()
	{
		attendees.clear();
		frozenElapsed = Duration.ZERO;
		startedAt = Instant.now();
		recording = true;
		needsSeed = true;
	}

	public void stop()
	{
		if (recording)
		{
			frozenElapsed = elapsed();
			recording = false;
			needsSeed = false;
			startedAt = null;
		}
	}

	public void reset()
	{
		attendees.clear();
		frozenElapsed = Duration.ZERO;
		recording = false;
		needsSeed = false;
		startedAt = null;
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

		final int before = attendees.size();

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

		return attendees.size() != before;
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
		return attendees.add(name);
	}

	public boolean isRecording()
	{
		return recording;
	}

	public int count()
	{
		return attendees.size();
	}

	public Duration elapsed()
	{
		if (recording && startedAt != null)
		{
			return Duration.between(startedAt, Instant.now());
		}
		return frozenElapsed;
	}

	public List<String> names()
	{
		return Collections.unmodifiableList(new ArrayList<>(attendees));
	}

	public String toClipboard()
	{
		return String.join(System.lineSeparator(), attendees);
	}
}
