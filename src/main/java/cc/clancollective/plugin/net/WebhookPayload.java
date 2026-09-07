package cc.clancollective.plugin.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

public final class WebhookPayload
{
	private static final int MAX_TITLE = 256;
	private static final int MAX_DESCRIPTION = 4096;
	private static final int MAX_FIELD_NAME = 256;
	private static final int MAX_FIELD_VALUE = 1024;
	private static final int MAX_FIELDS = 25;

	private final String kind;
	private final String rsn;

	private String title;
	private String description;
	private Color color;
	private String thumbnailUrl;
	private String authorName;
	private String authorIconUrl;
	private String imageAttachmentName;
	private boolean timestamp = true;
	private final List<Field> fields = new ArrayList<>();

	private WebhookPayload(final String kind, final String rsn)
	{
		this.kind = kind;
		this.rsn = rsn;
	}

	public static WebhookPayload of(final String kind, @Nullable final String rsn)
	{
		return new WebhookPayload(kind, rsn);
	}

	public WebhookPayload title(final String title)
	{
		this.title = title;
		return this;
	}

	public WebhookPayload description(final String description)
	{
		this.description = description;
		return this;
	}

	public WebhookPayload color(final Color color)
	{
		this.color = color;
		return this;
	}

	public WebhookPayload thumbnail(final String url)
	{
		this.thumbnailUrl = url;
		return this;
	}

	public WebhookPayload author(final String name, @Nullable final String iconUrl)
	{
		this.authorName = name;
		this.authorIconUrl = iconUrl;
		return this;
	}

	public WebhookPayload image(final String attachmentName)
	{
		this.imageAttachmentName = attachmentName;
		return this;
	}

	public WebhookPayload field(final String name, final String value, final boolean inline)
	{
		if (fields.size() < MAX_FIELDS)
		{
			fields.add(new Field(truncate(name, MAX_FIELD_NAME), truncate(value, MAX_FIELD_VALUE), inline));
		}
		return this;
	}

	public WebhookPayload timestamp(final boolean enabled)
	{
		this.timestamp = enabled;
		return this;
	}

	public String getKind()
	{
		return kind;
	}

	public String toJson(final Gson gson)
	{
		final JsonObject embed = new JsonObject();

		if (title != null)
		{
			embed.addProperty("title", truncate(title, MAX_TITLE));
		}
		if (description != null)
		{
			embed.addProperty("description", truncate(description, MAX_DESCRIPTION));
		}
		if (color != null)
		{

			embed.addProperty("color", color.getRGB() & 0xFFFFFF);
		}
		if (authorName != null)
		{
			final JsonObject author = new JsonObject();
			author.addProperty("name", authorName);
			if (authorIconUrl != null)
			{
				author.addProperty("icon_url", authorIconUrl);
			}
			embed.add("author", author);
		}
		if (thumbnailUrl != null)
		{
			final JsonObject thumb = new JsonObject();
			thumb.addProperty("url", thumbnailUrl);
			embed.add("thumbnail", thumb);
		}
		if (imageAttachmentName != null)
		{
			final JsonObject image = new JsonObject();
			image.addProperty("url", "attachment://" + imageAttachmentName);
			embed.add("image", image);
		}
		if (!fields.isEmpty())
		{
			final JsonArray arr = new JsonArray();
			for (final Field f : fields)
			{
				final JsonObject fo = new JsonObject();
				fo.addProperty("name", f.name);
				fo.addProperty("value", f.value);
				fo.addProperty("inline", f.inline);
				arr.add(fo);
			}
			embed.add("fields", arr);
		}

		if (timestamp)
		{
			embed.addProperty("timestamp", Instant.now().toString());
		}

		final JsonArray embeds = new JsonArray();
		embeds.add(embed);

		final JsonObject body = new JsonObject();
		body.add("embeds", embeds);
		return gson.toJson(body);
	}

	public static String bold(final String text)
	{
		return "**" + text + "**";
	}

	public static String quote(final String text)
	{
		final StringBuilder sb = new StringBuilder();
		for (final String line : text.split("\n", -1))
		{
			if (sb.length() > 0)
			{
				sb.append('\n');
			}
			sb.append("> ").append(line);
		}
		return sb.toString();
	}

	public static String codeBlock(final String text)
	{
		return "```\n" + text + "\n```";
	}

	private static String truncate(final String s, final int max)
	{
		if (s == null || s.length() <= max)
		{
			return s;
		}
		if (max <= 1)
		{
			return s.substring(0, max);
		}
		return s.substring(0, max - 1) + "\u2026";
	}

	@Value
	private static class Field
	{
		String name;
		String value;
		boolean inline;
	}
}
