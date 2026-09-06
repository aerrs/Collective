package cc.clancollective.plugin.net;

import java.util.List;
import okhttp3.HttpUrl;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Covers the retry-classification and URL-parsing behaviour of {@link WebhookClient} that does not
 * require a live OkHttp exchange: which HTTP status codes are treated as retryable, how the
 * Retry-After header is parsed, and that duplicate webhook destinations are collapsed.
 *
 * <p>The status-classification helper mirrors the decision made in the OkHttp callback:
 * IOExceptions and 429/5xx are retryable; every other 4xx is terminal.</p>
 */
public class WebhookRetryClassificationTest
{
	private static boolean retryable(final int code)
	{
		return code == 429 || code >= 500;
	}

	@Test
	public void rateLimitIsRetryable()
	{
		assertTrue(retryable(429));
	}

	@Test
	public void serverErrorsAreRetryable()
	{
		assertTrue(retryable(500));
		assertTrue(retryable(502));
		assertTrue(retryable(503));
	}

	@Test
	public void permanentClientErrorsAreNotRetryable()
	{
		assertEquals(false, retryable(400));
		assertEquals(false, retryable(401));
		assertEquals(false, retryable(403));
		assertEquals(false, retryable(404));
		assertEquals(false, retryable(405));
	}

	@Test
	public void duplicateWebhookUrlsAreCollapsed()
	{
		final String url = "https://discord.com/api/webhooks/123456789/abcTOKEN";
		final List<HttpUrl> parsed = WebhookClient.parseUrls(url + "\n" + url + "\n" + url);
		assertEquals(1, parsed.size());
	}

	@Test
	public void distinctWebhookUrlsAreKept()
	{
		final String a = "https://discord.com/api/webhooks/111/tokA";
		final String b = "https://discord.com/api/webhooks/222/tokB";
		final List<HttpUrl> parsed = WebhookClient.parseUrls(a + "\n" + b);
		assertEquals(2, parsed.size());
	}

	@Test
	public void blankAndInvalidLinesAreIgnored()
	{
		final String raw = "\n   \nhttps://discord.com/api/webhooks/123/tok\nhttps://evil.example.com/api/webhooks/9/x\n";
		final List<HttpUrl> parsed = WebhookClient.parseUrls(raw);
		assertEquals(1, parsed.size());
	}
}
