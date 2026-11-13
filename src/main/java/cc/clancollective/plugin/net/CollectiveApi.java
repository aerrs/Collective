package cc.clancollective.plugin.net;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public final class CollectiveApi
{
	public static final HttpUrl BASE_URL = HttpUrl.get("https://clancollective.cc");
	private static final String IMAGES = "/static/img/";
	private static final String THUMBS = "/img/";

	private CollectiveApi()
	{
	}

	public static OkHttpClient client(final OkHttpClient runeliteClient)
	{
		return runeliteClient.newBuilder().followRedirects(false).followSslRedirects(false).build();
	}

	public static HttpUrl.Builder url(final String path)
	{
		return BASE_URL.newBuilder().addPathSegments(path);
	}

	public static HttpUrl image(final String path)
	{
		if (path == null || !(path.startsWith(IMAGES) || path.startsWith(THUMBS) && path.endsWith(".png")) || path.contains(".."))
		{
			return null;
		}
		final HttpUrl url = BASE_URL.resolve(path);
		return url != null && url.host().equals(BASE_URL.host()) ? url : null;
	}

	public static HttpUrl page(final String raw)
	{
		final HttpUrl url = raw == null ? null : HttpUrl.parse(raw);
		return url != null && url.isHttps() && url.host().equals(BASE_URL.host()) ? url : null;
	}

	public static Request.Builder request(final HttpUrl url)
	{
		return new Request.Builder().url(url).header("User-Agent", "Collective RuneLite Plugin");
	}

	public static void fetchImage(final OkHttpClient client, final HttpUrl url, final Consumer<BufferedImage> done)
	{
		client.newCall(request(url).get().build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(final Call call, final IOException e)
			{
				log.debug("Collective image request failed: {}", url.encodedPath(), e);
				done.accept(null);
			}

			@Override
			public void onResponse(final Call call, final Response response)
			{
				BufferedImage image = null;
				try (Response r = response)
				{
					if (r.isSuccessful() && r.body() != null)
					{
						image = ImageIO.read(r.body().byteStream());
					}
				}
				catch (IOException e)
				{
					log.debug("Collective image could not be read: {}", url.encodedPath(), e);
				}
				done.accept(image);
			}
		});
	}

	public static <T> void send(final OkHttpClient client, final Gson gson, final Request request, final Class<T> type,
		final Consumer<T> done)
	{
		send(client, gson, request, type, false, done);
	}

	public static <T> void send(final OkHttpClient client, final Gson gson, final Request request, final Class<T> type,
		final boolean readNotFound, final Consumer<T> done)
	{
		client.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(final Call call, final IOException e)
			{
				log.debug("Collective request failed: {}", request.url().encodedPath(), e);
				done.accept(null);
			}

			@Override
			public void onResponse(final Call call, final Response response)
			{
				T body = null;
				try (Response r = response)
				{
					if ((r.isSuccessful() || readNotFound && r.code() == 404) && r.body() != null)
					{
						body = gson.fromJson(r.body().charStream(), type);
					}
					else
					{
						log.debug("Collective request {} returned {}", request.url().encodedPath(), r.code());
					}
				}
				catch (JsonParseException | IllegalStateException e)
				{
					log.debug("Collective response could not be read: {}", request.url().encodedPath(), e);
				}
				done.accept(body);
			}
		});
	}
}
