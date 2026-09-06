package cc.clancollective.plugin.combat;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.net.EmbedStyle;
import cc.clancollective.plugin.net.WebhookClient;
import cc.clancollective.plugin.net.WebhookPayload;
import cc.clancollective.plugin.util.Screenshot;
import cc.clancollective.plugin.util.ScreenshotUtil;
import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.SkullIcon;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * Posts combat events to the combat webhook.
 *
 * <p>Deaths are detected locally from {@link ActorDeath} on the local player; the value lost is
 * inferred from inventory and equipment minus the items kept on death (3, or 4 with Protect Item,
 * 0 skulled, 0 for Ultimate Ironmen). Region-based danger tables are intentionally not used - the
 * only gate is the value-lost threshold.
 *
 * <p>PvP kills, deaths and loot keys are relayed straight from the clan broadcasts that the game
 * itself produces; those broadcasts are suppressed in the generic chat relay so they arrive here
 * only, routed to the combat webhook.
 */
@Slf4j
@Singleton
public class CombatNotifier
{
	private static final String KIND_DEATH = "death";
	private static final String KIND_PK = "pk";
	private static final String KIND_PKDEATH = "pkdeath";
	private static final String KIND_LOOTKEY = "lootkey";

	private static final Pattern PK_KILL = Pattern.compile("has defeated ", Pattern.CASE_INSENSITIVE);
	private static final Pattern PK_DEATH = Pattern.compile("has been defeated by ", Pattern.CASE_INSENSITIVE);
	private static final Pattern LOOT_KEY = Pattern.compile("has opened a loot key worth ", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final CollectiveConfig config;
	private final ItemManager itemManager;
	private final WebhookClient webhookClient;
	private final ScreenshotUtil screenshotUtil;

	// Last actor the local player interacted with, used to attribute a PvM death to a killer NPC.
	private WeakReference<Actor> lastTarget = new WeakReference<>(null);

	@Inject
	public CombatNotifier(final Client client, final CollectiveConfig config,
		final ItemManager itemManager, final WebhookClient webhookClient,
		final ScreenshotUtil screenshotUtil)
	{
		this.client = client;
		this.config = config;
		this.itemManager = itemManager;
		this.webhookClient = webhookClient;
		this.screenshotUtil = screenshotUtil;
	}

	public void reset()
	{
		lastTarget = new WeakReference<>(null);
	}

	private boolean enabled()
	{
		return !config.combatWebhook().trim().isEmpty();
	}

	public void onInteractingChanged(final InteractingChanged event)
	{
		if (event.getSource() == client.getLocalPlayer()
			&& event.getTarget() != null
			&& event.getTarget().getCombatLevel() > 0)
		{
			lastTarget = new WeakReference<>(event.getTarget());
		}
	}

	public void onActorDeath(final ActorDeath event)
	{
		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}
		if (enabled() && config.notifyDeaths())
		{
			handleDeath();
		}
		lastTarget = new WeakReference<>(null);
	}

	private void handleDeath()
	{
		final List<PricedItem> items = collectItems();
		final long lost = valueLost(items, keepCount());

		if (lost < config.deathMinValue())
		{
			return;
		}

		final Actor killer = identifyKiller();
		final String killerName = killer != null && killer.getName() != null ? killer.getName() : null;

		final String rsn = localRsn();
		final String author = rsn != null ? rsn : "A clan member";

		final WebhookPayload payload = WebhookPayload.of(KIND_DEATH, rsn)
			.author(author, EmbedStyle.deathIcon())
			.description(WebhookPayload.bold(author) + " has died.")
			.color(EmbedStyle.DEATH)
			.field("Value lost", QuantityFormatter.quantityToStackSize(lost) + " gp", true);

		if (killerName != null)
		{
			payload.field("Killed by", killerName, true);
		}

		post(payload);
	}

	public void onClanBroadcast(final String message)
	{
		if (!enabled() || !config.notifyPvp())
		{
			return;
		}

		if (PK_DEATH.matcher(message).find())
		{
			relay(KIND_PKDEATH, message, EmbedStyle.DEATH, EmbedStyle.pkIcon());
		}
		else if (PK_KILL.matcher(message).find())
		{
			relay(KIND_PK, message, EmbedStyle.PK, EmbedStyle.pkIcon());
		}
		else if (LOOT_KEY.matcher(message).find())
		{
			relay(KIND_LOOTKEY, message, EmbedStyle.LOOTKEY, EmbedStyle.lootKeyIcon());
		}
	}

	/**
	 * @return true if the broadcast is a PvP kill, death or loot key - so the chat relay can
	 * exclude it and leave it to this notifier.
	 */
	public static boolean isPvpBroadcast(final String message)
	{
		return PK_DEATH.matcher(message).find()
			|| PK_KILL.matcher(message).find()
			|| LOOT_KEY.matcher(message).find();
	}

	private void relay(final String kind, final String message, final Color color,
		final String icon)
	{
		final String rsn = localRsn();
		final WebhookPayload payload = WebhookPayload.of(kind, rsn)
			.author(rsn != null ? rsn : "Clan", icon)
			.description(WebhookPayload.quote(WebhookPayload.bold(message)))
			.color(color);

		post(payload);
	}

	private List<PricedItem> collectItems()
	{
		final List<PricedItem> items = new ArrayList<>();
		addContainer(items, client.getItemContainer(InventoryID.INV));
		addContainer(items, client.getItemContainer(InventoryID.WORN));
		items.sort(Comparator.comparingLong((PricedItem p) -> p.unitPrice).reversed());
		return items;
	}

	private void addContainer(final List<PricedItem> out, @Nullable final ItemContainer container)
	{
		if (container == null)
		{
			return;
		}
		for (final Item item : container.getItems())
		{
			if (item.getId() < 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			final long unit = itemManager.getItemPrice(item.getId());
			out.add(new PricedItem(item.getId(), item.getQuantity(), unit));
		}
	}

	static long valueLost(final List<PricedItem> itemsByValueDesc, final int keepCount)
	{
		long lost = 0;
		int kept = 0;
		for (final PricedItem item : itemsByValueDesc)
		{
			for (int i = 0; i < item.quantity; i++)
			{
				if (kept < keepCount)
				{
					kept++;
				}
				else
				{
					lost += item.unitPrice;
				}
			}
		}
		return lost;
	}

	private int keepCount()
	{
		final Player local = client.getLocalPlayer();
		if (local == null)
		{
			return 3;
		}
		if (isUltimateIronman())
		{
			return 0;
		}
		int keep = local.getSkullIcon() == SkullIcon.NONE ? 3 : 0;
		if (client.isPrayerActive(Prayer.PROTECT_ITEM))
		{
			keep++;
		}
		return keep;
	}

	private boolean isUltimateIronman()
	{
		// VarbitID.IRONMAN: 2 = Ultimate Ironman.
		return client.getVarbitValue(VarbitID.IRONMAN) == 2;
	}

	@Nullable
	private Actor identifyKiller()
	{
		final Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}

		final Actor last = lastTarget.get();
		if (last != null && !last.isDead() && last.getInteracting() == local)
		{
			return last;
		}

		return client.getTopLevelWorldView().npcs().stream()
			.filter(npc -> npc != null && npc.getInteracting() == local && !npc.isDead())
			.filter(npc -> npc.getCombatLevel() > 0)
			.max(Comparator.comparingInt(NPC::getCombatLevel))
			.orElse(null);
	}

	private void post(final WebhookPayload payload)
	{
		final String webhook = config.combatWebhook();
		if (config.combatScreenshot())
		{
			final Consumer<Screenshot> consumer = shot ->
			{
				payload.image(shot.getFilename());
				webhookClient.send(webhook, payload, shot);
			};
			screenshotUtil.capture(consumer);
		}
		else
		{
			webhookClient.send(webhook, payload);
		}
	}

	private String localRsn()
	{
		final Player local = client.getLocalPlayer();
		return local != null ? local.getName() : null;
	}

	static final class PricedItem
	{
		final int id;
		final int quantity;
		final long unitPrice;

		PricedItem(final int id, final int quantity, final long unitPrice)
		{
			this.id = id;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
		}
	}
}
