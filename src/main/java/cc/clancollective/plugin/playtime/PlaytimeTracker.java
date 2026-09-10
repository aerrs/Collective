package cc.clancollective.plugin.playtime;

import cc.clancollective.plugin.CollectiveConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
	private long lastFlushMs;
	private long lastPersistMs;
	private boolean inFlight;
	private Bucket flushing;

	private final Map<Bucket, Accum> buckets = new LinkedHashMap<>();

	@Inject
	public PlaytimeTracker(final Client client, final CollectiveConfig config,
		final ConfigManager configManager, final OkHttpClient runeliteClient, final Gson gson)
	{
		this.client = client;
		this.config = config;
		this.configManager = configManager;
		this.httpClient = runeliteClient.newBuilder()
			.followRedirects(false)
			.followSslRedirects(false)
			.build();
		this.gson = gson;
		loadPending();
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
		if (!config.playtimeEnabled())
		{
			lastAccrualMs = -1;
			return;
		}
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

		recordElapsed(delta, clan, rsn, client.getAccountHash());

		if (now - lastPersistMs >= PERSIST_INTERVAL_MS)
		{
			persist();
		}
	}

	synchronized void recordElapsed(final long deltaMs, final String clan, final String rsn, final long accountHash)
	{
		final Bucket bucket = new Bucket(clan, rsn);
		final Accum accum = buckets.computeIfAbsent(bucket, b -> new Accum());
		accum.carryMs += deltaMs;
		final long whole = accum.carryMs / 1000L;
		if (whole > 0)
		{
			accum.seconds += whole;
			accum.carryMs -= whole * 1000L;
		}
		accum.accountHash = accountHash;
	}

	private void maybeFlush(final boolean force)
	{
		final long now = System.currentTimeMillis();
		final Bucket target;
		final int toSend;
		final long accountHash;
		synchronized (this)
		{
			if (inFlight)
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
			target = firstPending();
			if (target == null)
			{
				return;
			}
			lastFlushMs = now;
			toSend = (int) Math.min(buckets.get(target).seconds, Integer.MAX_VALUE);
			accountHash = buckets.get(target).accountHash;
			flushing = target;
			inFlight = true;
		}
		post(target, accountHash, toSend);
	}

	private synchronized Bucket firstPending()
	{
		for (final Map.Entry<Bucket, Accum> entry : buckets.entrySet())
		{
			if (entry.getValue().seconds > 0)
			{
				return entry.getKey();
			}
		}
		return null;
	}

	private void post(final Bucket bucket, final long accountHash, final int seconds)
	{
		final HttpUrl base = HttpUrl.parse(config.playtimeBackendUrl().trim());
		if (base == null || !isSecure(base))
		{
			onSendResult(false, bucket, seconds);
			return;
		}
		final HttpUrl url = base.newBuilder().addPathSegments("api/clan/playtime/heartbeat").build();

		final JsonObject body = new JsonObject();
		final String slug = config.clanCollectiveSlug().trim();
		if (!slug.isEmpty())
		{
			body.addProperty("slug", slug);
		}
		body.addProperty("cc", bucket.clan);
		body.addProperty("rsn", bucket.rsn);
		body.addProperty("accountHash", accountHash);
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
				onSendResult(false, bucket, seconds);
			}

			@Override
			public void onResponse(final Call call, final Response response)
			{
				try (Response r = response)
				{
					onSendResult(r.isSuccessful(), bucket, seconds);
				}
			}
		});
	}

	synchronized void onSendResult(final boolean ok, final Bucket bucket, final int sent)
	{
		if (ok)
		{
			final Accum accum = buckets.get(bucket);
			if (accum != null)
			{
				accum.seconds = Math.max(0, accum.seconds - sent);
				if (accum.seconds == 0 && accum.carryMs == 0)
				{
					buckets.remove(bucket);
				}
			}
			persist();
		}
		flushing = null;
		inFlight = false;
	}

	static boolean isSecure(final HttpUrl url)
	{
		if (url == null)
		{
			return false;
		}
		if (url.isHttps())
		{
			return true;
		}
		final String host = url.host();
		return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
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

	private synchronized void loadPending()
	{
		final String raw = configManager.getConfiguration(CollectiveConfig.GROUP, UNSENT_KEY);
		if (raw == null || raw.trim().isEmpty())
		{
			return;
		}
		try
		{
			final Type type = new TypeToken<List<StoredBucket>>()
			{
			}.getType();
			final List<StoredBucket> stored = gson.fromJson(raw, type);
			if (stored == null)
			{
				return;
			}
			for (final StoredBucket sb : stored)
			{
				if (sb == null || sb.clan == null || sb.rsn == null || sb.seconds <= 0)
				{
					continue;
				}
				final Accum accum = new Accum();
				accum.seconds = sb.seconds;
				accum.accountHash = sb.accountHash;
				buckets.put(new Bucket(sb.clan, sb.rsn), accum);
			}
		}
		catch (JsonSyntaxException e)
		{
			log.debug("Collective: bad stored playtime value", e);
		}
	}

	private synchronized void persist()
	{
		lastPersistMs = System.currentTimeMillis();
		final List<StoredBucket> stored = new ArrayList<>();
		for (final Map.Entry<Bucket, Accum> entry : buckets.entrySet())
		{
			if (entry.getValue().seconds > 0)
			{
				stored.add(new StoredBucket(entry.getKey().clan, entry.getKey().rsn,
					entry.getValue().seconds, entry.getValue().accountHash));
			}
		}
		configManager.setConfiguration(CollectiveConfig.GROUP, UNSENT_KEY, gson.toJson(stored));
	}

	synchronized long pendingSeconds()
	{
		long total = 0;
		for (final Accum accum : buckets.values())
		{
			total += accum.seconds;
		}
		return total;
	}

	synchronized long pendingSeconds(final String clan, final String rsn)
	{
		final Accum accum = buckets.get(new Bucket(clan, rsn));
		return accum != null ? accum.seconds : 0;
	}

	static final class Bucket
	{
		private final String clan;
		private final String rsn;

		Bucket(final String clan, final String rsn)
		{
			this.clan = clan;
			this.rsn = rsn;
		}

		@Override
		public boolean equals(final Object o)
		{
			if (this == o)
			{
				return true;
			}
			if (!(o instanceof Bucket))
			{
				return false;
			}
			final Bucket other = (Bucket) o;
			return Objects.equals(clan, other.clan) && Objects.equals(rsn, other.rsn);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(clan, rsn);
		}
	}

	private static final class Accum
	{
		private long carryMs;
		private long seconds;
		private long accountHash;
	}

	private static final class StoredBucket
	{
		private final String clan;
		private final String rsn;
		private final long seconds;
		private final long accountHash;

		private StoredBucket(final String clan, final String rsn, final long seconds, final long accountHash)
		{
			this.clan = clan;
			this.rsn = rsn;
			this.seconds = seconds;
			this.accountHash = accountHash;
		}
	}
}
