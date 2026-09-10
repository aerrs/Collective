package cc.clancollective.plugin.net;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.util.Screenshot;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Singleton
public class WebhookClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final String USER_AGENT = "Collective RuneLite Plugin";
	private static final int MAX_BACKOFF_SHIFT = 16;
	private static final int MAX_IN_FLIGHT = 250;
	private static final long MAX_RETRY_AFTER_MS = 300_000L;

	private static final Set<String> DISCORD_HOSTS = Set.of(
		"discord.com", "discordapp.com",
		"canary.discord.com", "ptb.discord.com");

	private final OkHttpClient httpClient;
	private final CollectiveConfig config;
	private final ScheduledExecutorService executor;
	private final Gson gson;

	private final Map<String, WebhookHealth> health = new ConcurrentHashMap<>();
	private final List<Runnable> healthListeners = new ArrayList<>();
	private final Set<ScheduledFuture<?>> pendingRetries = ConcurrentHashMap.newKeySet();
	private final AtomicInteger inFlight = new AtomicInteger();
	private volatile boolean shuttingDown;

	@Inject
	public WebhookClient(final OkHttpClient runeliteClient, final CollectiveConfig config,
		final ScheduledExecutorService executor, final Gson gson)
	{
		this.config = config;
		this.executor = executor;
		this.gson = gson;
		this.httpClient = runeliteClient.newBuilder()
			.followRedirects(false)
			.followSslRedirects(false)
			.addInterceptor(this::applyRequestSettings)
			.build();
	}

	private Response applyRequestSettings(final Interceptor.Chain chain) throws IOException
	{
		final Request request = chain.request().newBuilder()
			.header("User-Agent", USER_AGENT)
			.build();

		final int timeout = config.networkTimeout();
		Interceptor.Chain updated = chain
			.withConnectTimeout(timeout, TimeUnit.SECONDS)
			.withReadTimeout(timeout, TimeUnit.SECONDS);

		if (request.body() instanceof MultipartBody)
		{
			updated = updated.withWriteTimeout(Math.max(timeout * 3, timeout), TimeUnit.SECONDS);
		}

		return updated.proceed(request);
	}

	public void send(final String webhookUrls, final WebhookPayload payload)
	{
		send(webhookUrls, payload, null);
	}

	public void send(final String webhookUrls, final WebhookPayload payload,
		@Nullable final Screenshot screenshot)
	{
		final List<HttpUrl> urls = parseUrls(webhookUrls);
		if (urls.isEmpty())
		{
			return;
		}

		final String json = payload.toJson(gson);
		RequestBody imageBody = null;
		String imageName = null;
		if (screenshot != null)
		{
			final MediaType type = MediaType.parse(screenshot.getMimeType());
			imageBody = RequestBody.create(type, screenshot.getBytes());
			imageName = screenshot.getFilename();
		}
		final RequestBody image = imageBody;
		final String screenshotName = imageName;

		for (final HttpUrl url : urls)
		{
			if (inFlight.getAndIncrement() >= MAX_IN_FLIGHT)
			{
				inFlight.decrementAndGet();
				log.warn("Collective: webhook delivery queue full ({}); dropping event to {}",
					MAX_IN_FLIGHT, censor(url));
				continue;
			}
			markUnknownIfAbsent(url);
			executor.execute(() -> dispatch(url, json, screenshotName, image, 0));
		}
	}

	private void dispatch(final HttpUrl url, final String json, @Nullable final String screenshotName,
		@Nullable final RequestBody image, final int attempt)
	{
		final RequestBody body;
		if (image != null && screenshotName != null)
		{
			body = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("payload_json", json)
				.addFormDataPart("file", screenshotName, image)
				.build();
		}
		else
		{
			body = RequestBody.create(JSON, json);
		}

		final Request request = new Request.Builder()
			.url(url)
			.post(body)
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(final Call call, final IOException e)
			{
				retryOrDrop(url, json, screenshotName, image, attempt, e.getMessage(), 0L, true);
			}

			@Override
			public void onResponse(final Call call, final Response response)
			{
				try (Response r = response)
				{
					final int code = r.code();
					if (r.isSuccessful())
					{
						inFlight.decrementAndGet();
						updateHealth(url, health(url).withSuccess());
						return;
					}

					long retryAfterMs = 0L;
					final boolean retryable;
					if (code == 429)
					{
						retryAfterMs = parseRetryAfter(r);
						retryable = true;
					}
					else if (code >= 500)
					{
						retryable = true;
					}
					else
					{
						retryable = false;
					}

					retryOrDrop(url, json, screenshotName, image, attempt,
						"HTTP " + code + reason(r), retryAfterMs, retryable);
				}
			}
		});
	}

	private void retryOrDrop(final HttpUrl url, final String json, @Nullable final String screenshotName,
		@Nullable final RequestBody image, final int attempt, final String error, final long retryAfterMs,
		final boolean retryable)
	{
		final int maxRetries = config.maxRetries();
		final long baseDelay = config.baseRetryDelay();

		if (retryable && attempt < maxRetries && baseDelay > 0)
		{
			final long backoff = baseDelay * (1L << Math.min(attempt, MAX_BACKOFF_SHIFT));
			final long delay = Math.max(retryAfterMs, backoff);

			updateHealth(url, health(url).withWarning(error));
			log.debug("Collective: webhook delivery to {} failed ({}), retrying in {}ms (attempt {}/{})",
				censor(url), error, delay, attempt + 1, maxRetries);

			scheduleRetry(url, json, screenshotName, image, attempt, delay);
		}
		else
		{
			inFlight.decrementAndGet();
			updateHealth(url, health(url).withError(error));
			if (!retryable)
			{
				log.warn("Collective: webhook delivery to {} dropped ({}); error is not retryable",
					censor(url), error);
			}
			else
			{
				log.warn("Collective: webhook delivery to {} dropped after {} attempt(s): {}",
					censor(url), attempt + 1, error);
			}
		}
	}

	private void scheduleRetry(final HttpUrl url, final String json, @Nullable final String screenshotName,
		@Nullable final RequestBody image, final int attempt, final long delay)
	{
		if (shuttingDown)
		{
			inFlight.decrementAndGet();
			return;
		}

		final ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];
		final ScheduledFuture<?> future = executor.schedule(() ->
		{
			pendingRetries.remove(holder[0]);
			dispatch(url, json, screenshotName, image, attempt + 1);
		}, delay, TimeUnit.MILLISECONDS);

		holder[0] = future;
		pendingRetries.add(future);

		if (future.isDone())
		{
			pendingRetries.remove(future);
		}
	}

	public void cancelPendingRetries()
	{
		shuttingDown = true;
		for (final ScheduledFuture<?> future : pendingRetries)
		{
			if (future.cancel(false))
			{
				inFlight.decrementAndGet();
			}
		}
		pendingRetries.clear();
	}

	private static String reason(final Response response)
	{
		if (response.isSuccessful())
		{
			return "";
		}
		try
		{
			final okhttp3.ResponseBody responseBody = response.peekBody(512);
			final String text = responseBody.string().trim();
			return text.isEmpty() ? "" : ": " + text;
		}
		catch (IOException | RuntimeException ignored)
		{
			return "";
		}
	}

	private static long parseRetryAfter(final Response response)
	{
		final String header = response.header("Retry-After");
		if (header != null)
		{
			try
			{
				final double seconds = Double.parseDouble(header.trim());
				if (Double.isNaN(seconds) || seconds <= 0)
				{
					return 0L;
				}
				return Math.min((long) (seconds * 1000.0), MAX_RETRY_AFTER_MS);
			}
			catch (NumberFormatException ignored)
			{
				return 0L;
			}
		}
		return 0L;
	}

	public WebhookHealth healthFor(final String urlString)
	{
		final HttpUrl url = HttpUrl.parse(urlString == null ? "" : urlString.trim());
		return url == null ? WebhookHealth.unknown() : health(url);
	}

	public void addHealthListener(final Runnable listener)
	{
		synchronized (healthListeners)
		{
			healthListeners.add(listener);
		}
	}

	public void removeHealthListener(final Runnable listener)
	{
		synchronized (healthListeners)
		{
			healthListeners.remove(listener);
		}
	}

	private WebhookHealth health(final HttpUrl url)
	{
		return health.getOrDefault(url.toString(), WebhookHealth.unknown());
	}

	private void markUnknownIfAbsent(final HttpUrl url)
	{
		health.putIfAbsent(url.toString(), WebhookHealth.unknown());
	}

	private void updateHealth(final HttpUrl url, final WebhookHealth next)
	{
		health.put(url.toString(), next);
		final List<Runnable> snapshot;
		synchronized (healthListeners)
		{
			snapshot = new ArrayList<>(healthListeners);
		}
		for (final Runnable r : snapshot)
		{
			try
			{
				r.run();
			}
			catch (Exception e)
			{
				log.debug("Collective: health listener threw", e);
			}
		}
	}

	static List<HttpUrl> parseUrls(final String raw)
	{
		final List<HttpUrl> out = new ArrayList<>();
		if (raw == null || raw.trim().isEmpty())
		{
			return out;
		}
		final Set<String> seen = new HashSet<>();
		for (final String line : raw.split("\\R"))
		{
			final String trimmed = line.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}
			final HttpUrl url = HttpUrl.parse(trimmed);
			if (isValidDiscordWebhook(url))
			{
				if (seen.add(url.toString()))
				{
					out.add(url);
				}
			}
			else
			{
				log.warn("Collective: ignoring invalid Discord webhook URL: {}", censorRaw(trimmed));
			}
		}
		return out;
	}

	static boolean isValidDiscordWebhook(@Nullable final HttpUrl url)
	{
		if (url == null)
		{
			return false;
		}
		if (!"https".equals(url.scheme()))
		{
			return false;
		}
		if (!DISCORD_HOSTS.contains(url.host().toLowerCase(Locale.ROOT)))
		{
			return false;
		}
		final List<String> segments = url.pathSegments();
		final int n = segments.size();
		if (n != 4 && n != 5)
		{
			return false;
		}
		if (!"api".equals(segments.get(0)) || !"webhooks".equals(segments.get(n - 3)))
		{
			return false;
		}
		if (n == 5 && !segments.get(1).matches("v\\d+"))
		{
			return false;
		}
		final String id = segments.get(n - 2);
		final String token = segments.get(n - 1);
		return !id.isEmpty() && id.chars().allMatch(Character::isDigit) && !token.isEmpty();
	}

	private static String censor(final HttpUrl url)
	{
		final List<String> segments = url.pathSegments();
		if (segments.size() >= 2)
		{
			return url.scheme() + "://" + url.host() + "/.../"
				+ segments.get(segments.size() - 2) + "/\u2022\u2022\u2022";
		}
		return url.host();
	}

	private static String censorRaw(final String raw)
	{
		final int slash = raw.lastIndexOf('/');
		if (slash > 0 && slash < raw.length() - 1)
		{
			return raw.substring(0, slash + 1) + "\u2022\u2022\u2022";
		}
		return raw;
	}
}
