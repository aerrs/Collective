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

/**
 * Tracks which clan members were seen during a recorded event.
 *
 * <p>Attendance is event-driven rather than polled: when recording starts a single seed scan of the
 * currently visible players is taken (on the client thread via {@link #seedIfNeeded(Client)}), and
 * thereafter each {@code PlayerSpawned} is checked individually through
 * {@link #onPlayerSpawned(Player, ClanChannel)}. Nothing walks the full player list every tick.
 */
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

	/**
	 * Seeds attendees from the players currently in view. Runs at most once per recording session,
	 * immediately after {@link #start()}. Must be called on the client thread.
	 *
	 * @return {@code true} if the attendee set changed
	 */
	public boolean seedIfNeeded(final Client client)
	{
		if (!recording || !needsSeed || client == null)
		{
			return false;
		}
		needsSeed = false;

		final ClanChannel channel = client.getClanChannel();
		if (channel == null)
		{
			return false;
		}

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

	/**
	 * Records a single spawned player if they belong to the clan channel. O(1) per spawn.
	 *
	 * @return {@code true} if the attendee set changed
	 */
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
