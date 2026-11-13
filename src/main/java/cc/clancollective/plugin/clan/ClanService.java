package cc.clancollective.plugin.clan;

import cc.clancollective.plugin.net.CollectiveApi;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.client.util.ImageUtil;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

@Singleton
public class ClanService
{
	public static final int AVATAR = 32;
	private static final int MAX_AVATARS = 200;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final Map<String, BufferedImage> avatars = Collections.synchronizedMap(new LinkedHashMap<String, BufferedImage>(64, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(final Map.Entry<String, BufferedImage> eldest)
		{
			return size() > MAX_AVATARS;
		}
	});

	@Inject
	public ClanService(final OkHttpClient runeliteClient, final Gson gson)
	{
		this.httpClient = CollectiveApi.client(runeliteClient);
		this.gson = gson;
	}

	public void mine(final String clan, final Consumer<Mine> done)
	{
		get(CollectiveApi.url("api/clan/stats").addQueryParameter("cc", clan).build(), Mine.class, true, done);
	}

	public void directory(final String type, final Consumer<List<Clan>> done)
	{
		final HttpUrl.Builder url = CollectiveApi.url("api/clans").addQueryParameter("game", "osrs").addQueryParameter("limit", "50");
		if (type != null)
		{
			url.addQueryParameter("type", type);
		}
		get(url.build(), Directory.class, false, d -> done.accept(d == null ? null : d.clans));
	}

	public void avatar(final String path, final Consumer<BufferedImage> done)
	{
		final HttpUrl url = CollectiveApi.image(path);
		final BufferedImage hit = url == null ? null : avatars.get(path);
		if (url == null || hit != null)
		{
			done.accept(hit);
			return;
		}
		CollectiveApi.fetchImage(httpClient, url, image ->
		{
			final BufferedImage small = image == null ? null : ImageUtil.resizeImage(image, AVATAR, AVATAR);
			if (small != null)
			{
				avatars.put(path, small);
			}
			done.accept(small);
		});
	}

	private <T> void get(final HttpUrl url, final Class<T> type, final boolean readNotFound, final Consumer<T> done)
	{
		CollectiveApi.send(httpClient, gson, CollectiveApi.request(url).get().build(), type, readNotFound, done);
	}

	@Getter
	public static class Clan
	{
		private String name;
		private String slug;
		private String blurb;
		private String avatar;
		private List<String> types;
		private int members;

		public List<String> getTypes()
		{
			return types == null ? Collections.emptyList() : types;
		}
	}

	@Getter
	public static class Mine
	{
		private Clan card;
		private Double ehp;
		private Double ehb;
	}

	private static class Directory
	{
		private List<Clan> clans;
	}
}
