package cc.clancollective.plugin.clan;

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
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class ClanDirectoryService
{
	private static final String BASE_URL = "https://clancollective.cc";
	private static final String USER_AGENT = "Collective RuneLite Plugin";
	private static final long REFRESH_MS = 300_000L;
	private static final long ERROR_RETRY_MS = 30_000L;
	private static final int LIMIT = 100;

	private final OkHttpClient httpClient;
	private final Gson gson;

	private final Object lock = new Object();
	private String key;
	private List<ClanDirectoryEntry> cached;
	private long nextAllowedMs;
	private boolean inFlight;

	@Inject
	public ClanDirectoryService(final OkHttpClient runeliteClient, final Gson gson)
	{
		this.gson = gson;
		this.httpClient = runeliteClient.newBuilder()
			.followRedirects(false)
			.followSslRedirects(false)
			.build();
	}

	public void request(final String query, final Consumer<List<ClanDirectoryEntry>> callback)
	{
		final String q = query == null ? "" : query.trim();
		final String lookup = "q=" + q.toLowerCase();

		final List<ClanDirectoryEntry> toServe;
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
			fetch(lookup, q, callback);
		}
	}

	private void fetch(final String lookup, final String query,
		final Consumer<List<ClanDirectoryEntry>> callback)
	{
		final HttpUrl base = HttpUrl.parse(BASE_URL);
		if (base == null)
		{
			finish(lookup, null, callback);
			return;
		}
		final HttpUrl.Builder builder = base.newBuilder()
			.addPathSegments("api/clans")
			.addQueryParameter("limit", Integer.toString(LIMIT));
		if (!query.isEmpty())
		{
			builder.addQueryParameter("q", query);
		}

		final Request request = new Request.Builder()
			.url(builder.build())
			.header("User-Agent", USER_AGENT)
			.get()
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(final Call call, final IOException e)
			{
				log.debug("Collective: clan directory request failed", e);
				finish(lookup, null, callback);
			}

			@Override
			public void onResponse(final Call call, final Response response)
			{
				finish(lookup, parse(response), callback);
			}
		});
	}

	private List<ClanDirectoryEntry> parse(final Response response)
	{
		try (Response r = response)
		{
			if (!r.isSuccessful() || r.body() == null)
			{
				return null;
			}
			final DirectoryResponse dto = gson.fromJson(r.body().charStream(), DirectoryResponse.class);
			if (dto == null || !dto.ok || dto.clans == null)
			{
				return null;
			}
			final List<ClanDirectoryEntry> out = new ArrayList<>();
			for (final ClanDto c : dto.clans)
			{
				if (c != null && c.name != null && c.slug != null)
				{
					out.add(new ClanDirectoryEntry(c.name, c.slug, c.blurb, c.type,
						c.region, c.recruitment, Math.max(0, c.members), c.cc));
				}
			}
			return Collections.unmodifiableList(out);
		}
		catch (JsonSyntaxException | IllegalStateException e)
		{
			log.debug("Collective: clan directory parse failed", e);
			return null;
		}
	}

	private void finish(final String lookup, final List<ClanDirectoryEntry> result,
		final Consumer<List<ClanDirectoryEntry>> callback)
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

	private static final class DirectoryResponse
	{
		boolean ok;
		List<ClanDto> clans;
	}

	private static final class ClanDto
	{
		String name;
		String slug;
		String blurb;
		String type;
		String region;
		String recruitment;
		int members;
		String cc;
	}
}
