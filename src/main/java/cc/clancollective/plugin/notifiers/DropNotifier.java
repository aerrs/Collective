package cc.clancollective.plugin.notifiers;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.net.EmbedStyle;
import cc.clancollective.plugin.net.WebhookClient;
import cc.clancollective.plugin.net.WebhookPayload;
import cc.clancollective.plugin.util.ScreenshotUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.util.QuantityFormatter;

@Slf4j
@Singleton
public class DropNotifier
{
	private static final String KIND = "drop";

	private final Client client;
	private final CollectiveConfig config;
	private final ItemManager itemManager;
	private final WebhookClient webhookClient;
	private final ScreenshotUtil screenshotUtil;

	@Inject
	public DropNotifier(final Client client, final CollectiveConfig config, final ItemManager itemManager,
		final WebhookClient webhookClient, final ScreenshotUtil screenshotUtil)
	{
		this.client = client;
		this.config = config;
		this.itemManager = itemManager;
		this.webhookClient = webhookClient;
		this.screenshotUtil = screenshotUtil;
	}

	public void onNpcLootReceived(final NpcLootReceived event)
	{
		if (!enabled())
		{
			return;
		}
		final NPC npc = event.getNpc();
		final String source = npc != null && npc.getName() != null ? npc.getName() : "NPC";
		process(source, event.getItems());
	}

	public void onPlayerLootReceived(final PlayerLootReceived event)
	{
		if (!enabled())
		{
			return;
		}
		final Player player = event.getPlayer();
		final String source = player != null && player.getName() != null
			? player.getName()
			: "another player";
		process(source, event.getItems());
	}

	private boolean enabled()
	{
		return config.notifyDrops() && !config.dropsWebhook().trim().isEmpty();
	}

	private void process(final String source, final Collection<ItemStack> items)
	{
		if (items == null || items.isEmpty())
		{
			return;
		}

		final List<Entry> entries = new ArrayList<>();
		long total = 0;
		int topId = -1;
		long topValue = -1;
		for (final ItemStack stack : items)
		{
			final int price = itemManager.getItemPrice(stack.getId());
			final long value = (long) price * stack.getQuantity();
			total += value;
			if (value > topValue)
			{
				topValue = value;
				topId = stack.getId();
			}
			entries.add(new Entry(itemName(stack.getId()), stack.getQuantity(), value));
		}

		if (total < config.dropsMinValue())
		{
			return;
		}

		final String rsn = localRsn();
		final String author = rsn != null ? rsn : "A clan member";

		final WebhookPayload payload = WebhookPayload.of(KIND, rsn)
			.author(author, EmbedStyle.dropIcon())
			.description(WebhookPayload.quote(WebhookPayload.bold(author) + " has received a drop:")
				+ "\n" + WebhookPayload.codeBlock(buildItemList(entries)))
			.color(EmbedStyle.DROP)
			.field("From", source, true)
			.field("Value", QuantityFormatter.quantityToStackSize(total) + " gp", true);

		if (topId >= 0)
		{
			payload.thumbnail(EmbedStyle.itemIcon(topId));
		}

		if (config.dropsScreenshot())
		{
			screenshotUtil.capture(shot ->
			{
				payload.image(shot.getFilename());
				webhookClient.send(config.dropsWebhook(), payload, shot);
			});
		}
		else
		{
			webhookClient.send(config.dropsWebhook(), payload);
		}
	}

	private String buildItemList(final List<Entry> entries)
	{
		final StringBuilder sb = new StringBuilder();
		for (final Entry e : entries)
		{
			if (sb.length() > 0)
			{
				sb.append('\n');
			}
			if (e.quantity > 1)
			{
				sb.append(QuantityFormatter.formatNumber(e.quantity)).append(" x ");
			}
			sb.append(e.name);
			if (e.value > 0)
			{
				sb.append(" (").append(QuantityFormatter.quantityToStackSize(e.value)).append(" gp)");
			}
		}
		return sb.toString();
	}

	private String itemName(final int itemId)
	{
		final ItemComposition comp = itemManager.getItemComposition(itemId);
		return comp != null ? comp.getName() : "Item " + itemId;
	}

	private String localRsn()
	{
		final Player local = client.getLocalPlayer();
		return local != null ? local.getName() : null;
	}

	private static final class Entry
	{
		private final String name;
		private final int quantity;
		private final long value;

		private Entry(final String name, final int quantity, final long value)
		{
			this.name = name;
			this.quantity = quantity;
			this.value = value;
		}
	}
}
