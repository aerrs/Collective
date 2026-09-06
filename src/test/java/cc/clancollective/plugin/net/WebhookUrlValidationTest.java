package cc.clancollective.plugin.net;

import java.util.List;
import okhttp3.HttpUrl;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebhookUrlValidationTest
{
	private static boolean valid(final String url)
	{
		return WebhookClient.isValidDiscordWebhook(HttpUrl.parse(url));
	}

	@Test
	public void acceptsCanonicalDiscordWebhook()
	{
		assertTrue(valid("https://discord.com/api/webhooks/123456789/abcDEF-token_123"));
	}

	@Test
	public void acceptsDiscordappHost()
	{
		assertTrue(valid("https://discordapp.com/api/webhooks/123456789/tok"));
	}

	@Test
	public void acceptsCanaryAndPtbHosts()
	{
		assertTrue(valid("https://canary.discord.com/api/webhooks/123/tok"));
		assertTrue(valid("https://ptb.discord.com/api/webhooks/123/tok"));
	}

	@Test
	public void rejectsPlainHttp()
	{
		assertFalse(valid("http://discord.com/api/webhooks/123456789/tok"));
	}

	@Test
	public void rejectsNonDiscordHost()
	{
		assertFalse(valid("https://evil.example.com/api/webhooks/123456789/tok"));
	}

	@Test
	public void rejectsLookalikeHost()
	{
		assertFalse(valid("https://discord.com.evil.io/api/webhooks/123/tok"));
	}

	@Test
	public void rejectsMissingToken()
	{
		assertFalse(valid("https://discord.com/api/webhooks/123456789"));
	}

	@Test
	public void rejectsNonNumericId()
	{
		assertFalse(valid("https://discord.com/api/webhooks/notanid/tok"));
	}

	@Test
	public void rejectsWrongPathPrefix()
	{
		assertFalse(valid("https://discord.com/foo/bar/123/tok"));
	}

	@Test
	public void rejectsGarbage()
	{
		assertFalse(valid("not a url"));
		assertFalse(WebhookClient.isValidDiscordWebhook(null));
	}

	@Test
	public void parsesMultipleLinesKeepingOnlyValid()
	{
		final String raw = "https://discord.com/api/webhooks/111/tokA\n"
			+ "  \n"
			+ "http://discord.com/api/webhooks/222/tokB\n"
			+ "https://discordapp.com/api/webhooks/333/tokC\n";
		final List<HttpUrl> urls = WebhookClient.parseUrls(raw);
		assertEquals(2, urls.size());
	}

	@Test
	public void emptyInputYieldsNoUrls()
	{
		assertTrue(WebhookClient.parseUrls("").isEmpty());
		assertTrue(WebhookClient.parseUrls(null).isEmpty());
		assertTrue(WebhookClient.parseUrls("   \n  \n").isEmpty());
	}
}
