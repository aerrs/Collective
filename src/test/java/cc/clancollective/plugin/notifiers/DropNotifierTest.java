package cc.clancollective.plugin.notifiers;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.net.WebhookClient;
import cc.clancollective.plugin.net.WebhookPayload;
import cc.clancollective.plugin.util.Screenshot;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DropNotifierTest
{
	private Client client;
	private CollectiveConfig config;
	private ItemManager itemManager;
	private WebhookClient webhookClient;
	private DropNotifier notifier;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		config = mock(CollectiveConfig.class);
		itemManager = mock(ItemManager.class);
		webhookClient = mock(WebhookClient.class);

		notifier = new DropNotifier(client, config, itemManager, webhookClient, mock(cc.clancollective.plugin.util.ScreenshotUtil.class));

		when(config.notifyDrops()).thenReturn(true);
		when(config.dropsWebhook()).thenReturn("https://discord.com/api/webhooks/1/tok");
		when(config.dropsScreenshot()).thenReturn(false);

		final Player local = mock(Player.class);
		when(local.getName()).thenReturn("Tester");
		when(client.getLocalPlayer()).thenReturn(local);

		final ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Dragon claws");
		when(itemManager.getItemComposition(anyInt())).thenReturn(comp);
	}

	private NpcLootReceived npcLoot(final String npcName, final List<ItemStack> items)
	{
		final NPC npc = mock(NPC.class);
		when(npc.getName()).thenReturn(npcName);
		return new NpcLootReceived(npc, items);
	}

	@Test
	public void postsWhenValueMeetsThreshold()
	{
		when(config.dropsMinValue()).thenReturn(50_000);
		when(itemManager.getItemPrice(anyInt())).thenReturn(100_000);

		notifier.onNpcLootReceived(npcLoot("Boss", Collections.singletonList(new ItemStack(1, 1))));

		verify(webhookClient).send(eq(config.dropsWebhook()), any(WebhookPayload.class));
	}

	@Test
	public void postsWhenValueExactlyAtThreshold()
	{
		when(config.dropsMinValue()).thenReturn(50_000);
		when(itemManager.getItemPrice(anyInt())).thenReturn(50_000);

		notifier.onNpcLootReceived(npcLoot("Boss", Collections.singletonList(new ItemStack(1, 1))));

		verify(webhookClient).send(eq(config.dropsWebhook()), any(WebhookPayload.class));
	}

	@Test
	public void suppressesWhenBelowThreshold()
	{
		when(config.dropsMinValue()).thenReturn(50_000);
		when(itemManager.getItemPrice(anyInt())).thenReturn(49_999);

		notifier.onNpcLootReceived(npcLoot("Boss", Collections.singletonList(new ItemStack(1, 1))));

		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class), any(Screenshot.class));
	}

	@Test
	public void skipsEntirelyWhenWebhookBlank()
	{
		when(config.dropsWebhook()).thenReturn("");
		when(config.dropsMinValue()).thenReturn(0);
		when(itemManager.getItemPrice(anyInt())).thenReturn(1_000_000);

		notifier.onNpcLootReceived(npcLoot("Boss", Collections.singletonList(new ItemStack(1, 1))));

		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}

	@Test
	public void aggregatesMultipleItemsForThreshold()
	{
		when(config.dropsMinValue()).thenReturn(50_000);
		when(itemManager.getItemPrice(anyInt())).thenReturn(30_000);

		notifier.onNpcLootReceived(npcLoot("Boss",
			java.util.Arrays.asList(new ItemStack(1, 1), new ItemStack(2, 1))));

		verify(webhookClient).send(eq(config.dropsWebhook()), any(WebhookPayload.class));
	}

	@Test
	public void quantityMultipliesValue()
	{
		when(config.dropsMinValue()).thenReturn(50_000);
		when(itemManager.getItemPrice(anyInt())).thenReturn(10_000);

		notifier.onNpcLootReceived(npcLoot("Boss", Collections.singletonList(new ItemStack(1, 6))));

		verify(webhookClient).send(eq(config.dropsWebhook()), any(WebhookPayload.class));
	}

	@Test
	public void emptyLootDoesNothing()
	{
		when(config.dropsMinValue()).thenReturn(0);
		notifier.onNpcLootReceived(npcLoot("Boss", Collections.emptyList()));
		verify(webhookClient, never()).send(any(), any(WebhookPayload.class));
	}
}
