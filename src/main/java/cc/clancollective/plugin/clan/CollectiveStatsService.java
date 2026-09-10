package cc.clancollective.plugin.clan;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.util.ArrayList;
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

/**
 * Fetches clan stats (EHP/EHB/XP/members) from Clan Collective's read-only API.
 * Results are cached per lookup key and refreshed at most every {@link #REFRESH_MS};
 * failures back off for {@link #ERROR_RETRY_MS}. Network runs on the OkHttp pool;
 * the callback is invoked off the client/EDT thread, so callers must marshal to Swing.
 */
@Slf4j
@Singleton
public class CollectiveStatsService
{
	private static final String BASE_URL = "https://clancollective.cc";
	private static final String USER_AGENT = "Collective RuneLite Plugin";
	private static final long REFRESH_MS = 600_000L;
	private static final long ERROR_RETRY_MS = 60_000L;

	private final OkHttpClient httpClient;
	private final Gson gson;

	private final Object lock = new Object();
	private String key;
	private ClanStats cached;
	private long nextAllowedMs;
	private boolean inFlight;

	@Inject
	public CollectiveStatsService(final OkHttpClient runeliteClient, final Gson gson)
	{
		this.gson = gson;
		this.httpClient = runeliteClient.newBuilder()
			.followRedirects(false)
			.followSslRedirects(false)
			.build();
	}

	/**
	 * Requests stats for the given clan. Prefers {@code slug} when non-blank, else looks
	 * up by in-game {@code clanName}. Serves cached data immediately when available and
	 * fetches in the background when stale.
	 */
	public void request(final String clanName, final String slug, final Consumer<ClanStats> callback)
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

		final ClanStats toServe;
		final boolean shouldFetch;
		synchronized (lock)
		{
			if (!lookup.equals(key))
			{
				key = lookup;
				cached = null;
				nextAllowedMs = 0L;
			}
			final long now = System.currentTimeMillis();
			toServe = cached;
			shouldFetch = !inFlight && now >= nextAllowedMs;
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
		final Consumer<ClanStats> callback)
	{
		final HttpUrl base = HttpUrl.parse(BASE_URL);
		if (base == null)
		{
			finish(lookup, ClanStats.error(), callback);
			return;
		}
		final HttpUrl url = base.newBuilder()
			.addPathSegments("api/clan/stats")
			.addQueryParameter(useSlug ? "slug" : "cc", value)
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
				log.debug("Collective: clan stats request failed", e);
				finish(lookup, ClanStats.error(), callback);
			}

			@Override
			public void onResponse(final Call call, final Response response)
			{
				finish(lookup, parse(response), callback);
			}
		});
	}

	private ClanStats parse(final Response response)
	{
		try (Response r = response)
		{
			if (r.code() == 404)
			{
				return ClanStats.notFound();
			}
			if (!r.isSuccessful() || r.body() == null)
			{
				return ClanStats.error();
			}
			final StatsResponse dto = gson.fromJson(r.body().charStream(), StatsResponse.class);
			if (dto == null || !dto.ok)
			{
				return ClanStats.notFound();
			}
			final String name = dto.clan != null ? dto.clan.name : null;
			return ClanStats.ok(name, dto.source, dto.ehp, dto.ehb, dto.totalXp, dto.memberCount,
				dto.syncedAt, toWeekly(dto.weekly));
		}
		catch (JsonSyntaxException | IllegalStateException e)
		{
			log.debug("Collective: clan stats parse failed", e);
			return ClanStats.error();
		}
	}

	private void finish(final String lookup, final ClanStats result, final Consumer<ClanStats> callback)
	{
		synchronized (lock)
		{
			inFlight = false;
			if (lookup.equals(key))
			{
				final long backoff = result.getState() == ClanStats.State.ERROR
					? ERROR_RETRY_MS : REFRESH_MS;
				nextAllowedMs = System.currentTimeMillis() + backoff;
				// Keep serving the last good result on transient errors.
				if (result.getState() != ClanStats.State.ERROR || cached == null)
				{
					cached = result;
				}
			}
		}
		callback.accept(result);
	}

	private static ClanStats.Weekly toWeekly(final WeeklyDto dto)
	{
		if (dto == null)
		{
			return null;
		}
		return new ClanStats.Weekly(dto.ehpGained, dto.ehbGained,
			toContributors(dto.ehpTop), toContributors(dto.ehbTop));
	}

	private static List<ClanStats.Contributor> toContributors(final List<ContributorDto> rows)
	{
		final List<ClanStats.Contributor> out = new ArrayList<>();
		if (rows == null)
		{
			return out;
		}
		for (final ContributorDto row : rows)
		{
			if (row != null && row.rsn != null && row.gained != null)
			{
				out.add(new ClanStats.Contributor(row.rsn, row.gained));
			}
		}
		return out;
	}

	private static final class StatsResponse
	{
		boolean ok;
		String source;
		Integer ehp;
		Integer ehb;
		@SerializedName("total_xp")
		Long totalXp;
		@SerializedName("member_count")
		Integer memberCount;
		@SerializedName("synced_at")
		String syncedAt;
		ClanRef clan;
		WeeklyDto weekly;

		static final class ClanRef
		{
			String name;
			String slug;
		}
	}

	private static final class WeeklyDto
	{
		@SerializedName("ehp_gained")
		Double ehpGained;
		@SerializedName("ehb_gained")
		Double ehbGained;
		@SerializedName("ehp_top")
		List<ContributorDto> ehpTop;
		@SerializedName("ehb_top")
		List<ContributorDto> ehbTop;
	}

	private static final class ContributorDto
	{
		String rsn;
		Double gained;
	}
}
