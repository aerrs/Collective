package cc.clancollective.plugin.bingo;

import cc.clancollective.plugin.net.CollectiveApi;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

@Singleton
public class BingoService
{
	public static final String DROP = "drop";
	public static final String CLOG = "clog";
	public static final String PET = "pet";
	public static final String XP = "xp";
	public static final String LEVEL = "level";
	public static final String KC = "kc";
	public static final String PB = "pb";
	public static final String CLUE = "clue";

	private static final long REFRESH_MS = 900_000L;
	private static final long ERROR_RETRY_MS = 300_000L;
	private static final long BATCH_MS = 300_000L;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final Object lock = new Object();
	private final Map<String, Integer> pendingXp = new LinkedHashMap<>();
	private final Map<String, BufferedImage> images = new ConcurrentHashMap<>();
	private String key;
	private Watch watch = Watch.NONE;
	private long nextAllowedMs;
	private long fetchedMs;
	private long batchSentMs;
	private boolean inFlight;
	private volatile Runnable onChange = () -> { };

	@Inject
	public BingoService(final OkHttpClient runeliteClient, final Gson gson)
	{
		this.gson = gson;
		this.httpClient = CollectiveApi.client(runeliteClient);
	}

	public void refresh(final String clan, final String rsn, final long accountHash)
	{
		if (clan == null || clan.isEmpty() || rsn == null || rsn.isEmpty())
		{
			return;
		}
		final String lookup = (clan + "|" + rsn).toLowerCase(Locale.ROOT);
		synchronized (lock)
		{
			if (!lookup.equals(key))
			{
				key = lookup;
				watch = Watch.NONE;
				nextAllowedMs = 0L;
			}
			if (inFlight || System.currentTimeMillis() < nextAllowedMs)
			{
				return;
			}
			inFlight = true;
		}
		final Request.Builder request = CollectiveApi.request(CollectiveApi.url("api/bingo/watch")
			.addQueryParameter("cc", clan).addQueryParameter("rsn", rsn).build()).get();
		if (accountHash != -1)
		{
			request.header("X-Collective-Account", scramble(accountHash));
		}
		CollectiveApi.send(httpClient, gson, request.build(), Watch.class, result -> finish(lookup, result));
	}

	public void reset()
	{
		synchronized (lock)
		{
			key = null;
			watch = Watch.NONE;
			nextAllowedMs = 0L;
			pendingXp.clear();
		}
		onChange.run();
	}

	public void onChange(final Runnable listener)
	{
		onChange = listener;
	}

	public BingoBoard board()
	{
		synchronized (lock)
		{
			return watch.active && watch.board != null ? watch.board.named(watch.event, watch.team) : null;
		}
	}

	public void refreshIfOlderThan(final long ageMs)
	{
		synchronized (lock)
		{
			if (System.currentTimeMillis() - fetchedMs > ageMs)
			{
				nextAllowedMs = 0L;
			}
		}
	}

	public boolean wants(final String kind, final String name)
	{
		synchronized (lock)
		{
			return watch.wants(kind, name);
		}
	}

	public boolean addXp(final String skill, final int gained)
	{
		if (gained <= 0 || !wants(XP, skill))
		{
			return false;
		}
		synchronized (lock)
		{
			final int total = pendingXp.merge(skill, gained, Integer::sum);
			return watch.completes(XP, Collections.singletonMap(skill, total), false);
		}
	}

	public Map<String, Integer> takeXp(final boolean force)
	{
		synchronized (lock)
		{
			final long now = System.currentTimeMillis();
			if (pendingXp.isEmpty() || !force && now - batchSentMs < BATCH_MS)
			{
				return Collections.emptyMap();
			}
			batchSentMs = now;
			final Map<String, Integer> out = new LinkedHashMap<>(pendingXp);
			pendingXp.clear();
			return out;
		}
	}

	public Map<String, Integer> matching(final String kind, final Map<String, Integer> items)
	{
		if (PET.equals(kind))
		{
			return wants(PET, "") ? Collections.singletonMap("", 1) : Collections.emptyMap();
		}
		final Map<String, Integer> out = new LinkedHashMap<>();
		items.forEach((name, qty) -> out.computeIfAbsent(name, n -> wants(kind, n) ? qty : null));
		return out;
	}

	public void submit(final String clan, final String rsn, final long accountHash, final String kind,
		final Map<String, Integer> matched, final byte[] screenshot, final Consumer<String> onClaim)
	{
		if (clan == null || rsn == null || accountHash == -1 || matched.isEmpty())
		{
			return;
		}
		final String json = gson.toJson(ImmutableMap.of("cc", clan, "rsn", rsn, "accountHash", scramble(accountHash),
			"kind", kind, "items", matched.entrySet().stream().filter(e -> !e.getKey().isEmpty())
				.map(e -> ImmutableMap.of("name", e.getKey(), "qty", e.getValue())).collect(Collectors.toList())));
		final RequestBody body = screenshot == null ? RequestBody.create(MediaType.parse("application/json"), json)
			: new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("payload_json", json)
				.addFormDataPart("file", "bingo.jpg", RequestBody.create(MediaType.parse("image/jpeg"), screenshot)).build();
		final boolean quiet = XP.equals(kind) || KC.equals(kind) || CLUE.equals(kind);
		CollectiveApi.send(httpClient, gson, CollectiveApi.request(CollectiveApi.url("api/bingo/event").build())
			.post(body).build(), Claims.class, claims ->
		{
			final List<Claims.Tile> tiles = claims == null || claims.tiles == null ? Collections.emptyList() : claims.tiles;
			synchronized (lock)
			{
				watch.update(tiles);
			}
			if (!tiles.isEmpty())
			{
				onChange.run();
			}
			for (final Claims.Tile t : tiles)
			{
				final boolean open = "open".equals(t.status);
				if (!open || !quiet)
				{
					onClaim.accept(t.message());
				}
				if (!open)
				{
					refreshIfOlderThan(-1);
				}
			}
		});
	}

	public boolean completes(final String kind, final Map<String, Integer> matched)
	{
		synchronized (lock)
		{
			return watch.completes(kind, matched, true);
		}
	}

	public void image(final String path, final Consumer<BufferedImage> done)
	{
		final HttpUrl url = CollectiveApi.image(path);
		final BufferedImage hit = url == null ? null : images.get(path);
		if (url == null || hit != null)
		{
			done.accept(hit);
			return;
		}
		CollectiveApi.fetchImage(httpClient, url, image ->
		{
			if (image != null)
			{
				images.put(path, image);
			}
			done.accept(image);
		});
	}

	static String scramble(final long accountHash)
	{
		return Hashing.sha256().hashString("clancollective-bingo:" + accountHash, StandardCharsets.UTF_8).toString().substring(0, 32);
	}

	private void finish(final String lookup, final Watch result)
	{
		synchronized (lock)
		{
			inFlight = false;
			if (!lookup.equals(key))
			{
				return;
			}
			fetchedMs = System.currentTimeMillis();
			nextAllowedMs = fetchedMs + (result == null ? ERROR_RETRY_MS : REFRESH_MS);
			watch = result == null ? Watch.NONE : result.normalised();
		}
		onChange.run();
	}

	static final class Watch
	{
		static final Watch NONE = new Watch();

		boolean active;
		String event;
		String team;
		BingoBoard board;
		List<String> drop;
		List<String> clog;
		List<String> xp;
		List<String> level;
		List<String> kc;
		List<String> pb;
		List<String> clue;
		boolean pet;
		private transient Map<String, Set<String>> names = Collections.emptyMap();

		Watch normalised()
		{
			names = new ImmutableMap.Builder<String, Set<String>>().put(DROP, lower(drop)).put(CLOG, lower(clog))
				.put(XP, lower(xp)).put(LEVEL, lower(level)).put(KC, lower(kc)).put(PB, lower(pb)).put(CLUE, lower(clue)).build();
			return this;
		}

		boolean wants(final String kind, final String name)
		{
			if (!active)
			{
				return false;
			}
			if (PET.equals(kind))
			{
				return pet;
			}
			return names.getOrDefault(kind, Collections.emptySet()).contains(key(kind, name));
		}

		boolean completes(final String kind, final Map<String, Integer> matched, final boolean whenUnknown)
		{
			boolean found = false;
			for (final Map.Entry<String, Integer> e : matched.entrySet())
			{
				final String key = key(kind, e.getKey());
				for (final BingoBoard.Tile t : board == null ? Collections.<BingoBoard.Tile>emptyList() : board.getTiles())
				{
					if (kind.equals(t.getKind()) && !t.isDone() && !t.isReview()
						&& t.getItems().stream().anyMatch(i -> key(kind, i).equals(key)))
					{
						found = true;
						if (t.getProgress() + e.getValue() >= t.getTarget())
						{
							return true;
						}
					}
				}
			}
			return !found && whenUnknown;
		}

		private static String key(final String kind, final String name)
		{
			final String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
			return KC.equals(kind) || PB.equals(kind) ? bossKey(n) : n;
		}

		void update(final List<Claims.Tile> claims)
		{
			for (final Claims.Tile c : board == null ? Collections.<Claims.Tile>emptyList() : claims)
			{
				board.getTiles().stream().filter(t -> t.getName().equals(c.tile)).forEach(t -> t.update(c.progress, c.status));
			}
		}

		static String bossKey(final String name)
		{
			final String key = name.replace('\u2019', '\'').split(":")[0].trim();
			return key.startsWith("the ") ? key.substring(4) : key;
		}

		private static Set<String> lower(final List<String> names)
		{
			return names == null ? Collections.emptySet()
				: names.stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
		}
	}

	static final class Claims
	{
		List<Tile> tiles;

		static final class Tile
		{
			String tile;
			String status;
			int progress;
			int target;

			String message()
			{
				return tile + ("done".equals(status) ? " done!" : "review".equals(status) ? " sent for review." : " " + progress + "/" + target);
			}
		}
	}
}
