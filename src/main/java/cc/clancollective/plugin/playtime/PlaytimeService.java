package cc.clancollective.plugin.playtime;

import cc.clancollective.plugin.CollectiveConfig;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class PlaytimeService
{
	private static final String USER_AGENT = "Collective RuneLite Plugin";
	private static final long REFRESH_MS = 300_000L;
	private static final long ERROR_RETRY_MS = 60_000L;
	private static final int WINDOW_DAYS = 7;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final CollectiveConfig config;

	private final Object lock = new Object();
	private String key;
	private List<PlaytimeEntry> cached;
	private long nextAllowedMs;
	private boolean inFlight;

	@Inject
	public PlaytimeService(final OkHttpClient runeliteClient, final Gson gson, final CollectiveConfig config)
	{
		this.httpClient = runeliteClient.newBuilder()
			.followRedirects(false)
			.followSslRedirects(false)
			.build();
		this.gson = gson;
		this.config = config;
	}

	public void request(final String clanName, final String slug, final Consumer<List<PlaytimeEntry>> callback)
	{
		final String slugTrim = slug == null ? "" : slug.trim();
		final boolean useSlug = !slugTrim.isEmpty();
		final String nameTrim = clanName == null ? "" : clanName.trim();
		if (!useSlug && nameTrim.isEmpty())
		{
			return;
		}

		final String value = useSlug ? slugTrim : nameTrim;
		final String lookup = (useSlug ? "slug=" : "cc=") + value.toLowerCase();

		final List<PlaytimeEntry> toServe;
		final boolean shouldFetch;
		synchronized (lock)
		{
			if (!lookup.equals(key))
			{
				key = lookup;
				cached = null;
				nextAllowedMs = 0L;
			}
			toServe = cached;
			shouldFetch = !inFlight && System.currentTimeMillis() >= nextAllowedMs;
			if (shouldFetch)
			{
				inFlight = true;
			}
		}

		if (toServe != null)
		{
			callback.accept(toServe);
		}
		if (shouldFetch)
		{
			fetch(lookup, useSlug, value, callback);
		}
	}

	private void fetch(final String lookup, final boolean useSlug, final String value,
		final Consumer<List<PlaytimeEntry>> callback)
	{
		final HttpUrl base = HttpUrl.parse(config.playtimeBackendUrl().trim());
		if (base == null || !PlaytimeTracker.isSecure(base))
		{
			finish(lookup, null, callback);
			return;
		}
		final HttpUrl url = base.newBuilder()
			.addPathSegments("api/clan/playtime/leaderboard")
			.addQueryParameter(useSlug ? "slug" : "cc", value)
			.addQueryParameter("days", Integer.toString(WINDOW_DAYS))
			.build();

		final Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.get()
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(final Call call, final IOException e)
			{
				log.debug("Collective: playtime leaderboard request failed", e);
				finish(lookup, null, callback);
			}

			@Override
			public void onResponse(final Call call, final Response response)
			{
				finish(lookup, parse(response), callback);
			}
		});
	}

	private List<PlaytimeEntry> parse(final Response response)
	{
		try (Response r = response)
		{
			if (!r.isSuccessful() || r.body() == null)
			{
				return null;
			}
			final LeaderboardResponse dto = gson.fromJson(r.body().charStream(), LeaderboardResponse.class);
			if (dto == null || !dto.ok || dto.entries == null)
			{
				return null;
			}
			final List<PlaytimeEntry> out = new ArrayList<>();
			for (final EntryDto e : dto.entries)
			{
				if (e != null && e.rsn != null)
				{
					out.add(new PlaytimeEntry(Text.toJagexName(e.rsn), Math.max(0, e.seconds)));
				}
			}
			return Collections.unmodifiableList(out);
		}
		catch (JsonSyntaxException | IllegalStateException e)
		{
			log.debug("Collective: playtime leaderboard parse failed", e);
			return null;
		}
	}

	private void finish(final String lookup, final List<PlaytimeEntry> result,
		final Consumer<List<PlaytimeEntry>> callback)
	{
		synchronized (lock)
		{
			inFlight = false;
			if (lookup.equals(key))
			{
				nextAllowedMs = System.currentTimeMillis() + (result == null ? ERROR_RETRY_MS : REFRESH_MS);
				if (result != null)
				{
					cached = result;
				}
			}
		}
		if (result != null)
		{
			callback.accept(result);
		}
	}

	private static final class LeaderboardResponse
	{
		boolean ok;
		List<EntryDto> entries;
	}

	private static final class EntryDto
	{
		String rsn;
		long seconds;
	}
}
