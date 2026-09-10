package cc.clancollective.plugin.playtime;

import cc.clancollective.plugin.CollectiveConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Singleton
public class PlaytimeTracker
{
	static final String UNSENT_KEY = "playtimeUnsent";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final String USER_AGENT = "Collective RuneLite Plugin";

	private static final long FLUSH_INTERVAL_MS = 5 * 60 * 1000L;
	private static final long MAX_TICK_GAP_MS = 60 * 1000L;
	private static final long PERSIST_INTERVAL_MS = 30 * 1000L;

	private final Client client;
	private final CollectiveConfig config;
	private final ConfigManager configManager;
	private final OkHttpClient httpClient;
	private final Gson gson;

	private long lastAccrualMs = -1;
	private long carryMs;
	private long pendingSeconds;
	private long lastFlushMs;
	private long lastPersistMs;
	private String lastClan;
	private String lastRsn;
	private boolean inFlight;

	@Inject
	public PlaytimeTracker(final Client client, final CollectiveConfig config,
		final ConfigManager configManager, final OkHttpClient runeliteClient, final Gson gson)
	{
		this.client = client;
		this.config = config;
		this.configManager = configManager;
		this.httpClient = runeliteClient;
		this.gson = gson;
		this.pendingSeconds = loadPending();
	}

	public void onLogin()
	{
		if (lastAccrualMs < 0)
		{
			final long now = System.currentTimeMillis();
			lastAccrualMs = now;
			lastFlushMs = now;
		}
	}

	public void onLogout()
	{
		tick();
		lastAccrualMs = -1;
		persist();
		maybeFlush(true);
	}

	public void onGameTick()
	{
		if (!config.playtimeEnabled())
		{
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		tick();
		maybeFlush(false);
	}

	public void shutDown()
	{
		tick();
		persist();
		maybeFlush(true);
	}

	private void tick()
	{
		final long now = System.currentTimeMillis();
		if (lastAccrualMs < 0)
		{
			lastAccrualMs = now;
			return;
		}
		final long delta = Math.min(MAX_TICK_GAP_MS, now - lastAccrualMs);
		lastAccrualMs = now;
		if (delta <= 0)
		{
			return;
		}

		final String clan = currentClan();
		final String rsn = currentRsn();
		if (clan == null || rsn == null)
		{
			return;
		}
		if (!clanMatchesFilter(clan))
		{
			return;
		}

		recordElapsed(delta, clan, rsn);

		if (now - lastPersistMs >= PERSIST_INTERVAL_MS)
		{
			persist();
		}
	}

	synchronized void recordElapsed(final long deltaMs, final String clan, final String rsn)
	{
		carryMs += deltaMs;
		final long whole = carryMs / 1000L;
		if (whole > 0)
		{
			pendingSeconds += whole;
			carryMs -= whole * 1000L;
		}
		lastClan = clan;
		lastRsn = rsn;
	}

	private void maybeFlush(final boolean force)
	{
		final long now = System.currentTimeMillis();
		final int toSend;
		synchronized (this)
		{
			if (inFlight || pendingSeconds <= 0 || lastRsn == null || lastClan == null)
			{
				return;
			}
			if (!force && now - lastFlushMs < FLUSH_INTERVAL_MS)
			{
				return;
			}
			if (!config.playtimeEnabled() || config.playtimeToken().trim().isEmpty())
			{
				return;
			}
			lastFlushMs = now;
			toSend = (int) Math.min(pendingSeconds, Integer.MAX_VALUE);
			inFlight = true;
		}
		post(lastClan, lastRsn, toSend);
	}

	private void post(final String clan, final String rsn, final int seconds)
	{
		final HttpUrl base = HttpUrl.parse(config.playtimeBackendUrl().trim());
		if (base == null)
		{
			onSendResult(false, seconds);
			return;
		}
		final HttpUrl url = base.newBuilder().addPathSegments("api/clan/playtime/heartbeat").build();

		final JsonObject body = new JsonObject();
		final String slug = config.clanCollectiveSlug().trim();
		if (!slug.isEmpty())
		{
			body.addProperty("slug", slug);
		}
		body.addProperty("cc", clan);
		body.addProperty("rsn", rsn);
		body.addProperty("seconds", seconds);
		body.addProperty("token", config.playtimeToken().trim());

		final Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(final Call call, final IOException e)
			{
				log.debug("Collective: playtime heartbeat failed", e);
				onSendResult(false, seconds);
			}

			@Override
			public void onResponse(final Call call, final Response response)
			{
				try (Response r = response)
				{
					onSendResult(r.isSuccessful(), seconds);
				}
			}
		});
	}

	synchronized void onSendResult(final boolean ok, final int sent)
	{
		if (ok)
		{
			pendingSeconds = Math.max(0, pendingSeconds - sent);
			persist();
		}
		inFlight = false;
	}

	private boolean clanMatchesFilter(final String clan)
	{
		final String filter = config.clanFilter().trim();
		return filter.isEmpty() || filter.equalsIgnoreCase(clan);
	}

	private String currentClan()
	{
		final ClanChannel channel = client.getClanChannel();
		return channel != null ? channel.getName() : null;
	}

	private String currentRsn()
	{
		final Player local = client.getLocalPlayer();
		return local != null && local.getName() != null ? Text.toJagexName(local.getName()) : null;
	}

	private long loadPending()
	{
		try
		{
			final String raw = configManager.getConfiguration(CollectiveConfig.GROUP, UNSENT_KEY);
			if (raw != null && !raw.isEmpty())
			{
				return Math.max(0, Long.parseLong(raw.trim()));
			}
		}
		catch (NumberFormatException e)
		{
			log.debug("Collective: bad stored playtime value", e);
		}
		return 0;
	}

	private synchronized void persist()
	{
		lastPersistMs = System.currentTimeMillis();
		configManager.setConfiguration(CollectiveConfig.GROUP, UNSENT_KEY, Long.toString(pendingSeconds));
	}

	synchronized long pendingSeconds()
	{
		return pendingSeconds;
	}
}
