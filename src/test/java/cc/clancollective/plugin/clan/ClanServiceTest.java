package cc.clancollective.plugin.clan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class ClanServiceTest
{
	@Test
	public void listedClanIsRead() throws Exception
	{
		final ClanService.Mine mine = mine(service(200, "{\"ok\":true,\"ehp\":12.5,\"card\":{\"name\":\"Clan A\",\"types\":[\"PvM\"]}}"));
		assertEquals("Clan A", mine.getCard().getName());
		assertEquals(12.5, mine.getEhp(), 0);
	}

	@Test
	public void unlistedClanIsNotAFailure() throws Exception
	{
		final ClanService.Mine mine = mine(service(404, "{\"ok\":false,\"error\":\"No matching clan on Clan Collective.\"}"));
		assertNotNull(mine);
		assertNull(mine.getCard());
	}

	@Test
	public void serverErrorIsAFailure() throws Exception
	{
		assertNull(mine(service(500, "{\"ok\":false}")));
	}

	@Test
	public void brokenJsonIsAFailure() throws Exception
	{
		assertNull(mine(service(200, "{not json")));
		assertNull(mine(service(404, "<html>Not Found</html>")));
	}

	@Test
	public void networkErrorIsAFailure() throws Exception
	{
		final OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			throw new IOException("offline");
		}).build();
		assertNull(mine(new ClanService(client, new Gson())));
	}

	@Test
	public void directoryReadsClansAndAsksForOsrs() throws Exception
	{
		final String[] url = new String[1];
		final OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			url[0] = chain.request().url().toString();
			return response(chain.request(), 200, "{\"ok\":true,\"clans\":[{\"name\":\"Clan B\"}]}");
		}).build();
		final CompletableFuture<List<ClanService.Clan>> found = new CompletableFuture<>();
		new ClanService(client, new Gson()).directory("PvM", found::complete);
		final List<ClanService.Clan> clans = found.get(5, TimeUnit.SECONDS);
		assertEquals("Clan B", clans.get(0).getName());
		assertTrue(clans.get(0).getTypes().isEmpty());
		assertTrue(url[0].contains("game=osrs") && url[0].contains("type=PvM"));
	}

	@Test
	public void directoryFailureIsNull() throws Exception
	{
		final CompletableFuture<List<ClanService.Clan>> found = new CompletableFuture<>();
		service(503, "").directory(null, found::complete);
		assertNull(found.get(5, TimeUnit.SECONDS));
	}

	private static ClanService service(final int code, final String body)
	{
		return new ClanService(new OkHttpClient.Builder().addInterceptor(chain -> response(chain.request(), code, body)).build(), new Gson());
	}

	private static Response response(final okhttp3.Request request, final int code, final String body)
	{
		return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
			.body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
	}

	private static ClanService.Mine mine(final ClanService service) throws Exception
	{
		final CompletableFuture<ClanService.Mine> mine = new CompletableFuture<>();
		service.mine("Clan A", mine::complete);
		return mine.get(5, TimeUnit.SECONDS);
	}
}
