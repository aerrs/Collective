package cc.clancollective.plugin.playtime;

import cc.clancollective.plugin.CollectiveConfig;
import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlaytimeServiceTest
{
	private OkHttpClient httpClient;
	private Call call;
	private PlaytimeService service;

	@Before
	public void setUp()
	{
		httpClient = mock(OkHttpClient.class);
		final CollectiveConfig config = mock(CollectiveConfig.class);
		when(config.playtimeBackendUrl()).thenReturn("https://clancollective.cc");
		call = mock(Call.class);
		when(httpClient.newCall(any())).thenReturn(call);
		service = new PlaytimeService(httpClient, new Gson(), config);
	}

	private void respond(final int code, final String body)
	{
		doAnswer(inv ->
		{
			final Callback cb = inv.getArgument(0);
			final Request req = new Request.Builder().url("https://clancollective.cc/x").build();
			final Response resp = new Response.Builder()
				.request(req).protocol(Protocol.HTTP_1_1).code(code).message("m")
				.body(ResponseBody.create(MediaType.parse("application/json"), body == null ? "" : body))
				.build();
			cb.onResponse(call, resp);
			return null;
		}).when(call).enqueue(any());
	}

	private List<PlaytimeEntry> requestOnce(final String clan)
	{
		final AtomicReference<List<PlaytimeEntry>> got = new AtomicReference<>();
		service.request(clan, "", got::set);
		return got.get();
	}

	@Test
	public void parsesAndOrdersEntries()
	{
		respond(200, "{\"ok\":true,\"entries\":[{\"rsn\":\"Alice\",\"seconds\":900},{\"rsn\":\"Bob\",\"seconds\":100}]}");
		final List<PlaytimeEntry> entries = requestOnce("The Highlanders");
		assertNotNull(entries);
		assertEquals(2, entries.size());
		assertEquals("Alice", entries.get(0).getRsn());
		assertEquals(900, entries.get(0).getSeconds());
	}

	@Test
	public void cachesWithinRefreshWindow()
	{
		respond(200, "{\"ok\":true,\"entries\":[{\"rsn\":\"A\",\"seconds\":1}]}");
		service.request("Clan", "", e -> { });
		service.request("Clan", "", e -> { });
		verify(httpClient, times(1)).newCall(any());
	}

	@Test
	public void refetchesWhenClanChanges()
	{
		respond(200, "{\"ok\":true,\"entries\":[]}");
		service.request("Clan A", "", e -> { });
		service.request("Clan B", "", e -> { });
		verify(httpClient, times(2)).newCall(any());
	}

	@Test
	public void errorResponseYieldsNoData()
	{
		respond(500, "");
		assertNull(requestOnce("Clan"));
	}

	@Test
	public void malformedJsonYieldsNoData()
	{
		respond(200, "not json");
		assertNull(requestOnce("Clan"));
	}

	@Test
	public void notOkYieldsNoData()
	{
		respond(200, "{\"ok\":false}");
		assertNull(requestOnce("Clan"));
	}

	@Test
	public void blankClanAndSlugDoesNothing()
	{
		respond(200, "{\"ok\":true,\"entries\":[]}");
		service.request("", "", e -> { });
		verify(httpClient, times(0)).newCall(any());
	}
}
