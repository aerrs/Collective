package cc.clancollective.plugin.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebhookPayloadTest
{
	private static JsonObject embedOf(final WebhookPayload payload)
	{
		final JsonObject root = new JsonParser().parse(payload.toJson()).getAsJsonObject();
		return root.getAsJsonArray("embeds").get(0).getAsJsonObject();
	}

	@Test
	public void omitsFooter()
	{
		assertFalse(embedOf(WebhookPayload.of("drop", "Zezima")).has("footer"));
	}

	@Test
	public void boldWrapsText()
	{
		assertEquals("**AER5**", WebhookPayload.bold("AER5"));
	}

	@Test
	public void quotePrefixesEveryLine()
	{
		assertEquals("> a\n> b", WebhookPayload.quote("a\nb"));
	}

	@Test
	public void codeBlockWrapsText()
	{
		assertEquals("```\nx\n```", WebhookPayload.codeBlock("x"));
	}

	@Test
	public void encodesColorAsRgbInt()
	{
		final JsonObject embed = embedOf(WebhookPayload.of("drop", "x").color(new Color(0x60, 0x78, 0xF8)));
		assertEquals(0x6078F8, embed.get("color").getAsInt());
	}

	@Test
	public void truncatesOverlongTitle()
	{
		final StringBuilder big = new StringBuilder();
		for (int i = 0; i < 500; i++)
		{
			big.append('a');
		}
		final JsonObject embed = embedOf(WebhookPayload.of("drop", "x").title(big.toString()));
		assertEquals(256, embed.get("title").getAsString().length());
	}

	@Test
	public void capsFieldCountAt25()
	{
		final WebhookPayload p = WebhookPayload.of("drop", "x");
		for (int i = 0; i < 40; i++)
		{
			p.field("n" + i, "v" + i, true);
		}
		final JsonObject embed = embedOf(p);
		assertEquals(25, embed.getAsJsonArray("fields").size());
	}

	@Test
	public void setsAttachmentImageUrl()
	{
		final JsonObject embed = embedOf(WebhookPayload.of("drop", "x").image("collective.jpg"));
		assertEquals("attachment://collective.jpg",
				embed.getAsJsonObject("image").get("url").getAsString());
	}

	@Test
	public void includesTimestampByDefaultAndCanDisable()
	{
		assertTrue(embedOf(WebhookPayload.of("drop", "x")).has("timestamp"));
		assertFalse(embedOf(WebhookPayload.of("drop", "x").timestamp(false)).has("timestamp"));
	}

	@Test
	public void kindAccessorReturnsKind()
	{
		assertEquals("death", WebhookPayload.of("death", "x").getKind());
	}
}