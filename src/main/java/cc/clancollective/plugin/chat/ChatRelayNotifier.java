package cc.clancollective.plugin.chat;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.combat.CombatNotifier;
import cc.clancollective.plugin.net.EmbedStyle;
import cc.clancollective.plugin.net.WebhookClient;
import cc.clancollective.plugin.net.WebhookPayload;
import java.awt.Color;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;

@Singleton
public class ChatRelayNotifier
{
	private static final String KIND_MESSAGE = "clanchat";
	private static final String KIND_BROADCAST = "broadcast";
	private static final String KIND_APPLICATION = "application";
	private static final String KIND_INVITE = "invite";
	private static final String KIND_JOIN = "join";
	private static final String KIND_LEAVE = "leave";

	private static final Pattern CA_PREFIX = Pattern.compile("^CA_ID:\\d+\\|");

	private static final String[] IGNORED_PREFIXES = {
		"To talk in your clan's channel",
		"You are now a member of",
		"You are not in a clan chat channel",
	};

	// Broadcasts that a dedicated milestone feed already handles from the first-person game message.
	// Relaying them here as well would post the same milestone twice. Kept deliberately narrow so
	// generic broadcasts (e.g. combat-level or total-level milestones) still relay.
	private static final String[] MILESTONE_BROADCASTS = {
		"has completed a combat task",
		"has a funny feeling",
		"feels something weird sneaking",
		"would have been followed",
	};

	private final Client client;
	private final CollectiveConfig config;
	private final WebhookClient webhookClient;

	@Inject
	public ChatRelayNotifier(final Client client, final CollectiveConfig config,
		final WebhookClient webhookClient)
	{
		this.client = client;
		this.config = config;
		this.webhookClient = webhookClient;
	}

	public void onChatMessage(final ChatMessage event)
	{
		final ClanChannel channel = client.getClanChannel();
		if (channel == null)
		{
			return;
		}

		switch (event.getType())
		{
			case CLAN_CHAT:
				if (config.relayMessages() && chatEnabled())
				{
					relayMessage(channel, event);
				}
				break;
			case CLAN_MESSAGE:
			case BROADCAST:
				relaySystem(channel, Text.removeTags(event.getMessage()));
				break;
			case GAMEMESSAGE:
				relayGameMessage(channel, Text.removeTags(event.getMessage()));
				break;
			default:
				break;
		}
	}

	private void relayGameMessage(final ClanChannel channel, final String message)
	{
		if (isApplication(message) && config.relayApplications() && clanAdminEnabled())
		{
			send(config.clanAdminWebhook(), KIND_APPLICATION, channel.getName(),
				WebhookPayload.quote(message), EmbedStyle.RECRUIT);
		}
	}

	private void relaySystem(final ClanChannel channel, final String message)
	{
		if (isIgnored(message))
		{
			return;
		}

		if (isJoin(message))
		{
			if (config.relayJoins() && clanAdminEnabled())
			{
				send(config.clanAdminWebhook(), KIND_JOIN, channel.getName(),
					WebhookPayload.quote(message), EmbedStyle.JOIN);
			}
			return;
		}

		if (isLeave(message))
		{
			if (config.relayLeaves() && clanAdminEnabled())
			{
				send(config.clanAdminWebhook(), KIND_LEAVE, channel.getName(),
					WebhookPayload.quote(message), EmbedStyle.LEAVE);
			}
			return;
		}

		if (isInvite(message))
		{
			if (config.relayInvites() && clanAdminEnabled())
			{
				send(config.clanAdminWebhook(), KIND_INVITE, channel.getName(),
					WebhookPayload.quote(message), EmbedStyle.RECRUIT);
			}
			return;
		}

		if (isDedicatedFeed(message))
		{
			return;
		}

		if (config.relayBroadcasts() && chatEnabled())
		{
			final String cleaned = CA_PREFIX.matcher(message).replaceFirst("");
			send(config.chatWebhook(), KIND_BROADCAST, channel.getName(),
				WebhookPayload.quote(WebhookPayload.bold(cleaned)), EmbedStyle.BROADCAST);
		}
	}

	private static boolean isDedicatedFeed(final String message)
	{
		if (CombatNotifier.isPvpBroadcast(message))
		{
			return true;
		}
		for (final String marker : MILESTONE_BROADCASTS)
		{
			if (message.contains(marker))
			{
				return true;
			}
		}
		return false;
	}

	private void relayMessage(final ClanChannel channel, final ChatMessage event)
	{
		final String sender = Text.removeTags(Text.toJagexName(event.getName()));
		final String message = Text.removeTags(event.getMessage());
		final String rank = rankTitle(channel, sender);
		final String header = rank != null ? sender + " (" + rank + ")" : sender;

		final WebhookPayload payload = WebhookPayload.of(KIND_MESSAGE, sender)
			.author(header, EmbedStyle.clanRankIcon(rank))
			.description(WebhookPayload.quote(message))
			.color(EmbedStyle.CLAN_CHAT);

		webhookClient.send(config.chatWebhook(), payload);
	}

	private void send(final String webhook, final String kind, final String author,
		final String description, final Color color)
	{
		final WebhookPayload payload = WebhookPayload.of(kind, null)
			.author(author, null)
			.description(description)
			.color(color);

		webhookClient.send(webhook, payload);
	}

	private static boolean isIgnored(final String message)
	{
		for (final String prefix : IGNORED_PREFIXES)
		{
			if (message.startsWith(prefix))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isJoin(final String message)
	{
		return message.contains("has joined the clan");
	}

	private static boolean isLeave(final String message)
	{
		return message.contains("has left the clan");
	}

	private static boolean isApplication(final String message)
	{
		return message.contains("has applied to join your clan");
	}

	private static boolean isInvite(final String message)
	{
		return message.contains("has been invited into the clan");
	}

	private String rankTitle(final ClanChannel channel, final String name)
	{
		final ClanChannelMember member = channel.findMember(name);
		if (member == null)
		{
			return null;
		}
		final ClanSettings settings = client.getClanSettings();
		if (settings == null)
		{
			return null;
		}
		final ClanTitle title = settings.titleForRank(member.getRank());
		return title != null ? title.getName() : null;
	}

	private boolean chatEnabled()
	{
		return !config.chatWebhook().trim().isEmpty();
	}

	private boolean clanAdminEnabled()
	{
		return !config.clanAdminWebhook().trim().isEmpty();
	}
}
