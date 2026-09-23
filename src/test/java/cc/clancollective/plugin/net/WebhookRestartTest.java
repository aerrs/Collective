package cc.clancollective.plugin.net;

import cc.clancollective.plugin.CollectiveConfig;
import com.google.gson.Gson;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WebhookRestartTest
{
	private static final String URL = "https://discord.com/api/webhooks/123456789/tokenvalue";

	private OkHttpClient client;
	private Call call;
	private ScheduledExecutorService executor;
	private CollectiveConfig config;
	private WebhookClient webhookClient;

	@Before
	public void setUp()
	{
		final OkHttpClient runeliteClient = mock(OkHttpClient.class);
		final OkHttpClient.Builder builder = mock(OkHttpClient.Builder.class, RETURNS_SELF);
		client = mock(OkHttpClient.class);
		when(runeliteClient.newBuilder()).thenReturn(builder);
		when(builder.build()).thenReturn(client);

		call = mock(Call.class);
		when(client.newCall(any())).thenReturn(call);
		doAnswer(inv ->
		{
			final Callback cb = inv.getArgument(0);
			cb.onResponse(call, error500());
			return null;
		}).when(call).enqueue(any());

		executor = mock(ScheduledExecutorService.class);
		doAnswer(inv ->
		{
			((Runnable) inv.getArgument(0)).run();
			return null;
		}).when(executor).execute(any());
		when(executor.schedule(any(Runnable.class), anyLong(), any()))
			.thenReturn(mock(ScheduledFuture.class));

		config = mock(CollectiveConfig.class);
		when(config.maxRetries()).thenReturn(3);
		when(config.baseRetryDelay()).thenReturn(100);
		when(config.networkTimeout()).thenReturn(5);

		webhookClient = new WebhookClient(runeliteClient, config, executor, new Gson());
	}

	private static Response error500()
	{
		final Request req = new Request.Builder().url("https://discord.com/x").build();
		return new Response.Builder()
			.request(req).protocol(Protocol.HTTP_1_1).code(500).message("err")
			.body(ResponseBody.create(MediaType.parse("text/plain"), ""))
			.build();
	}

	private WebhookPayload payload()
	{
		return WebhookPayload.of("test", null);
	}

	@Test
	public void retriesAreScheduledWhenLive()
	{
		webhookClient.send(URL, payload());
		verify(executor, times(1)).schedule(any(Runnable.class), anyLong(), any());
	}

	@Test
	public void shutdownStopsRetries()
	{
		webhookClient.cancelPendingRetries();
		webhookClient.send(URL, payload());
		verify(executor, never()).schedule(any(Runnable.class), anyLong(), any());
	}

	@Test
	public void restartReenablesRetries()
	{
		webhookClient.cancelPendingRetries();
		webhookClient.resume();
		webhookClient.send(URL, payload());
		verify(executor, times(1)).schedule(any(Runnable.class), anyLong(), any());
	}
}
