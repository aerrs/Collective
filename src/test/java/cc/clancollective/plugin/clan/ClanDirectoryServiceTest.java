package cc.clancollective.plugin.clan;

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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClanDirectoryServiceTest
{
	private OkHttpClient httpClient;
	private Call call;
	private ClanDirectoryService service;

	@Before
	public void setUp()
	{
		httpClient = mock(OkHttpClient.class);
		final OkHttpClient.Builder builder = mock(OkHttpClient.Builder.class);
		when(httpClient.newBuilder()).thenReturn(builder);
		when(builder.followRedirects(anyBoolean())).thenReturn(builder);
		when(builder.followSslRedirects(anyBoolean())).thenReturn(builder);
		when(builder.build()).thenReturn(httpClient);
		call = mock(Call.class);
		when(httpClient.newCall(any())).thenReturn(call);
		service = new ClanDirectoryService(httpClient, new Gson());
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

	private List<ClanDirectoryEntry> requestOnce(final String query)
	{
		final AtomicReference<List<ClanDirectoryEntry>> got = new AtomicReference<>();
		service.request(query, got::set);
		return got.get();
	}

	@Test
	public void parsesClans()
	{
		respond(200, "{\"ok\":true,\"clans\":[{\"name\":\"Alpha\",\"slug\":\"alpha\",\"blurb\":\"hi\","
			+ "\"region\":\"EU\",\"recruitment\":\"open\",\"members\":120,\"cc\":\"Alpha CC\"}]}");
		final List<ClanDirectoryEntry> out = requestOnce("");
		assertNotNull(out);
		assertEquals(1, out.size());
		assertEquals("Alpha", out.get(0).getName());
		assertEquals("alpha", out.get(0).getSlug());
		assertEquals(120, out.get(0).getMembers());
		assertEquals("Alpha CC", out.get(0).getCc());
	}

	@Test
	public void notOkYieldsNoData()
	{
		respond(200, "{\"ok\":false}");
		assertNull(requestOnce(""));
	}

	@Test
	public void errorResponseYieldsNoData()
	{
		respond(500, "");
		assertNull(requestOnce(""));
	}

	@Test
	public void cachesWithinRefreshWindow()
	{
		respond(200, "{\"ok\":true,\"clans\":[]}");
		service.request("", e -> { });
		service.request("", e -> { });
		verify(httpClient, times(1)).newCall(any());
	}

	@Test
	public void refetchesWhenQueryChanges()
	{
		respond(200, "{\"ok\":true,\"clans\":[]}");
		service.request("a", e -> { });
		service.request("b", e -> { });
		verify(httpClient, times(2)).newCall(any());
	}
}
