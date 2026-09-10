package cc.clancollective.plugin.clan;

import com.google.gson.Gson;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CollectiveStatsServiceTest
{
	private OkHttpClient client;
	private Call call;
	private CollectiveStatsService service;

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

		service = new CollectiveStatsService(runeliteClient, new Gson());
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

	private ClanStats requestOnce(final String clan)
	{
		final AtomicReference<ClanStats> got = new AtomicReference<>();
		service.request(clan, "", got::set);
		return got.get();
	}

	@Test
	public void parsesOkStats()
	{
		respond(200, "{\"ok\":true,\"source\":\"wom\",\"ehp\":132364,\"ehb\":45778,"
			+ "\"total_xp\":49691464504,\"member_count\":336,\"clan\":{\"name\":\"The Highlanders\"}}");
		final ClanStats stats = requestOnce("The Highlanders");
		assertNotNull(stats);
		assertEquals(ClanStats.State.OK, stats.getState());
		assertEquals(Integer.valueOf(132364), stats.getEhp());
		assertEquals(Integer.valueOf(45778), stats.getEhb());
		assertEquals(Long.valueOf(49691464504L), stats.getTotalXp());
		assertEquals("wom", stats.getSource());
	}

	@Test
	public void parsesWeeklyBlock()
	{
		respond(200, "{\"ok\":true,\"ehp\":1,\"weekly\":{\"ehp_gained\":510.1,\"ehb_gained\":286.1,"
			+ "\"ehp_top\":[{\"rsn\":\"SlySelf\",\"gained\":43.46}]}}");
		final ClanStats stats = requestOnce("Clan");
		assertNotNull(stats.getWeekly());
		assertEquals(Double.valueOf(510.1), stats.getWeekly().getEhpGained());
		assertEquals("SlySelf", stats.getWeekly().getEhpTop().get(0).getRsn());
	}

	@Test
	public void notFoundWhenClanMissing()
	{
		respond(404, "{\"ok\":false}");
		assertEquals(ClanStats.State.NOT_FOUND, requestOnce("Clan").getState());
	}

	@Test
	public void errorOnServerFailure()
	{
		respond(500, "");
		assertEquals(ClanStats.State.ERROR, requestOnce("Clan").getState());
	}

	@Test
	public void cachesWithinRefreshWindow()
	{
		respond(200, "{\"ok\":true,\"ehp\":1}");
		service.request("Clan", "", s -> { });
		service.request("Clan", "", s -> { });
		verify(client, times(1)).newCall(any());
	}
}
