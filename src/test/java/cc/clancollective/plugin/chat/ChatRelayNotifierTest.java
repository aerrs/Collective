package cc.clancollective.plugin.chat;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.net.WebhookClient;
import cc.clancollective.plugin.net.WebhookPayload;
import com.google.gson.Gson;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.ChatMessage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChatRelayNotifierTest
{
	private static final String CHAT_WEBHOOK = "https://discord.com/api/webhooks/1/tok";
	private static final String ADMIN_WEBHOOK = "https://discord.com/api/webhooks/2/tok";

	private Client client;
	private CollectiveConfig config;
	private WebhookClient webhookClient;
	private ChatRelayNotifier notifier;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		config = mock(CollectiveConfig.class);
		webhookClient = mock(WebhookClient.class);
		notifier = new ChatRelayNotifier(client, config, webhookClient);

		when(config.chatWebhook()).thenReturn(CHAT_WEBHOOK);
		when(config.clanAdminWebhook()).thenReturn(ADMIN_WEBHOOK);
		when(config.clanFilter()).thenReturn("");
		when(config.relayMessages()).thenReturn(true);
		when(config.relayBroadcasts()).thenReturn(true);
		when(config.relayApplications()).thenReturn(true);
		when(config.relayInvites()).thenReturn(true);
		when(config.relayJoins()).thenReturn(true);
		when(config.relayLeaves()).thenReturn(true);
		when(config.relayRankChanges()).thenReturn(true);

		final ClanChannel channel = mock(ClanChannel.class);
		when(channel.getName()).thenReturn("The Highlanders");
		when(channel.findMember(any())).thenReturn(null);
		when(client.getClanChannel()).thenReturn(channel);
	}

	private ChatMessage message(final ChatMessageType type, final String name, final String text)
	{
		final ChatMessage event = new ChatMessage();
		event.setType(type);
		event.setName(name);
		event.setMessage(text);
		return event;
	}

	@Test
	public void relaysClanMessage()
	{
		notifier.onChatMessage(message(ChatMessageType.CLAN_CHAT, "AER5", "hello"));
		verify(webhookClient).send(eq(CHAT_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void relaysBroadcastFromClanMessage()
	{
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "", "AER5 has completed a combat achievement."));
		verify(webhookClient).send(eq(CHAT_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void relaysBroadcastFromBroadcastType()
	{
		notifier.onChatMessage(message(ChatMessageType.BROADCAST, "", "AER5 has reached the highest possible combat level."));
		verify(webhookClient).send(eq(CHAT_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void routesJoinToClanAdminWebhook()
	{
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "", "Musty Weenus has joined the clan."));
		verify(webhookClient).send(eq(ADMIN_WEBHOOK), any(WebhookPayload.class));
		verify(webhookClient, never()).send(eq(CHAT_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void routesLeaveToClanAdminWebhook()
	{
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "", "Musty Weenus has left the clan."));
		verify(webhookClient).send(eq(ADMIN_WEBHOOK), any(WebhookPayload.class));
		verify(webhookClient, never()).send(eq(CHAT_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void routesApplicationToClanAdminWebhook()
	{
		notifier.onChatMessage(message(ChatMessageType.GAMEMESSAGE, "", "Sasha12 IM has applied to join your clan."));
		verify(webhookClient).send(eq(ADMIN_WEBHOOK), any(WebhookPayload.class));
		verify(webhookClient, never()).send(eq(CHAT_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void routesInviteToClanAdminWebhook()
	{
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "", "Sasha12 IM has been invited into the clan by AER5."));
		verify(webhookClient).send(eq(ADMIN_WEBHOOK), any(WebhookPayload.class));
		verify(webhookClient, never()).send(eq(CHAT_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void joinSkippedWhenToggleOff()
	{
		when(config.relayJoins()).thenReturn(false);
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "", "Musty Weenus has joined the clan."));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void leaveSkippedWhenToggleOff()
	{
		when(config.relayLeaves()).thenReturn(false);
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "", "Musty Weenus has left the clan."));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void applicationSkippedWhenToggleOff()
	{
		when(config.relayApplications()).thenReturn(false);
		notifier.onChatMessage(message(ChatMessageType.GAMEMESSAGE, "", "Sasha12 IM has applied to join your clan."));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void inviteSkippedWhenToggleOff()
	{
		when(config.relayInvites()).thenReturn(false);
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "", "Sasha12 IM has been invited into the clan by AER5."));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void joinSkippedWhenClanAdminWebhookBlank()
	{
		when(config.clanAdminWebhook()).thenReturn("   ");
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "", "Musty Weenus has joined the clan."));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void memberEventsNotAffectedByBroadcastToggle()
	{
		when(config.relayBroadcasts()).thenReturn(false);
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "", "Musty Weenus has joined the clan."));
		verify(webhookClient).send(eq(ADMIN_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void memberEventsRelayEvenWhenChatWebhookBlank()
	{
		when(config.chatWebhook()).thenReturn("");
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "", "Musty Weenus has left the clan."));
		verify(webhookClient).send(eq(ADMIN_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void ignoresLoginHint()
	{
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "",
			"To talk in your clan's channel, start each line of chat with // or /c."));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void stripsCombatAchievementPrefix()
	{
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "",
			"CA_ID:360|Beggnog has completed an easy combat task: Fire in the Hole!."));

		final ArgumentCaptor<WebhookPayload> captor = ArgumentCaptor.forClass(WebhookPayload.class);
		verify(webhookClient).send(eq(CHAT_WEBHOOK), captor.capture());
		final String json = captor.getValue().toJson(new Gson());
		if (json.contains("CA_ID:"))
		{
			throw new AssertionError("CA_ID prefix leaked into broadcast payload: " + json);
		}
	}

	@Test
	public void ignoresUnrelatedGameMessage()
	{
		notifier.onChatMessage(message(ChatMessageType.GAMEMESSAGE, "", "You have accepted Sasha12 IM into HLDRS."));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void ignoresPublicChat()
	{
		notifier.onChatMessage(message(ChatMessageType.PUBLICCHAT, "AER5", "hello"));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void ignoresPrivateMessage()
	{
		notifier.onChatMessage(message(ChatMessageType.PRIVATECHAT, "AER5", "secret"));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void skipsClanMessageWhenChatWebhookBlank()
	{
		when(config.chatWebhook()).thenReturn("   ");
		notifier.onChatMessage(message(ChatMessageType.CLAN_CHAT, "AER5", "hello"));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void skipsMessagesWhenMessageToggleOff()
	{
		when(config.relayMessages()).thenReturn(false);
		notifier.onChatMessage(message(ChatMessageType.CLAN_CHAT, "AER5", "hello"));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void skipsWhenNotInClanChannel()
	{
		when(client.getClanChannel()).thenReturn(null);
		notifier.onChatMessage(message(ChatMessageType.CLAN_CHAT, "AER5", "hello"));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void relaysCombatTaskBroadcastThroughGenericRelay()
	{
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "",
			"AER5 has completed a combat task: Peach Conjurer."));
		verify(webhookClient).send(eq(CHAT_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void relaysPetBroadcastThroughGenericRelay()
	{
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "",
			"AER5 has a funny feeling like they're being followed."));
		verify(webhookClient).send(eq(CHAT_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void relaysPvpDeathBroadcastThroughGenericRelay()
	{
		notifier.onChatMessage(message(ChatMessageType.CLAN_MESSAGE, "",
			"AER5 has been defeated by Pker and lost (1,200,000 coins)."));
		verify(webhookClient).send(eq(CHAT_WEBHOOK), any(WebhookPayload.class));
	}

	@Test
	public void stillRelaysGenericCombatLevelBroadcast()
	{
		notifier.onChatMessage(message(ChatMessageType.BROADCAST, "",
			"AER5 has reached the highest possible combat level."));
		verify(webhookClient).send(eq(CHAT_WEBHOOK), any(WebhookPayload.class));
	}
}
