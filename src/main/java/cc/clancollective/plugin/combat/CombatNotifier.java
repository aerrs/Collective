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
import java.util.regex.Matcher;
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
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

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

	private static final int DEATH_MERGE_TICKS = 2;
	private static final int DEATH_MERGE_MAX_TICKS = 6;

	private final Client client;
	private final CollectiveConfig config;
	private final ItemManager itemManager;
	private final WebhookClient webhookClient;
	private final ScreenshotUtil screenshotUtil;

	private WeakReference<Actor> lastTarget = new WeakReference<>(null);
	private PendingDeath pendingDeath;
	private String recentPvpKiller;
	private int recentPvpKillerTicks;

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
		pendingDeath = null;
		recentPvpKiller = null;
		recentPvpKillerTicks = 0;
	}

	public void onGameTick()
	{
		if (recentPvpKillerTicks > 0 && --recentPvpKillerTicks == 0)
		{
			recentPvpKiller = null;
		}

		final PendingDeath pd = pendingDeath;
		if (pd == null)
		{
			return;
		}

		pd.ticks++;
		final boolean ready = !pd.wantScreenshot || pd.shotReady;
		if ((ready && pd.ticks >= DEATH_MERGE_TICKS) || pd.ticks >= DEATH_MERGE_MAX_TICKS)
		{
			flushDeath();
		}
	}

	private boolean enabled()
	{
		return !config.combatWebhook().trim().isEmpty();
	}

	private boolean clanFilterMatches()
	{
		final String filter = config.clanFilter().trim();
		if (filter.isEmpty())
		{
			return true;
		}
		final ClanChannel channel = client.getClanChannel();
		return channel != null && filter.equalsIgnoreCase(channel.getName());
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
			beginDeath();
		}
		lastTarget = new WeakReference<>(null);
	}

	private void beginDeath()
	{
		final List<PricedItem> items = collectItems();
		final long lost = valueLost(items, keepCount());

		if (lost < config.deathMinValue())
		{
			return;
		}

		if (pendingDeath != null)
		{
			flushDeath();
		}

		final String rsn = localRsn();
		final String author = rsn != null ? rsn : "A clan member";

		final WebhookPayload payload = WebhookPayload.of(KIND_DEATH, rsn)
			.author(author, EmbedStyle.deathIcon())
			.description(WebhookPayload.bold(author) + " has died.")
			.color(EmbedStyle.DEATH)
			.field("Estimated value lost", QuantityFormatter.quantityToStackSize(lost) + " gp", true);

		final PendingDeath pd = new PendingDeath(payload, config.combatScreenshot());

		if (recentPvpKiller != null)
		{
			pd.killer = recentPvpKiller;
			pd.killerConfirmed = true;
			recentPvpKiller = null;
			recentPvpKillerTicks = 0;
		}
		else
		{
			final Actor confirmed = confirmedKiller();
			if (confirmed != null && confirmed.getName() != null)
			{
				pd.killer = confirmed.getName();
				pd.killerConfirmed = true;
			}
			else
			{
				final Actor probable = probableKiller();
				pd.killer = probable != null ? probable.getName() : null;
				pd.killerConfirmed = false;
			}
		}

		pendingDeath = pd;

		if (pd.wantScreenshot)
		{
			screenshotUtil.capture(shot ->
			{
				if (pendingDeath == pd && !pd.sent)
				{
					pd.shot = shot;
					pd.shotReady = true;
				}
			});
		}
	}

	private void flushDeath()
	{
		final PendingDeath pd = pendingDeath;
		if (pd == null || pd.sent)
		{
			return;
		}
		pd.sent = true;
		pendingDeath = null;

		if (pd.killer != null)
		{
			pd.payload.field(pd.killerConfirmed ? "Killed by" : "Likely killer", pd.killer, true);
		}

		final String webhook = config.combatWebhook();
		if (pd.wantScreenshot && pd.shot != null)
		{
			pd.payload.image(pd.shot.getFilename());
			webhookClient.send(webhook, pd.payload, pd.shot);
		}
		else
		{
			webhookClient.send(webhook, pd.payload);
		}
	}

	private void applyPvpKiller(@Nullable final String killer)
	{
		if (killer == null)
		{
			return;
		}
		if (pendingDeath != null && !pendingDeath.sent)
		{
			pendingDeath.killer = killer;
			pendingDeath.killerConfirmed = true;
		}
		else
		{
			recentPvpKiller = killer;
			recentPvpKillerTicks = DEATH_MERGE_TICKS;
		}
	}

	public void onClanBroadcast(final String rawMessage)
	{
		if (!enabled() || !config.notifyPvp() || !clanFilterMatches())
		{
			return;
		}

		final String message = Text.removeTags(rawMessage);
		final String rsn = localRsn();

		final Matcher death = PK_DEATH.matcher(message);
		if (death.find())
		{
			if (config.notifyDeaths() && isSubject(message, death.start(), rsn))
			{
				applyPvpKiller(killerAfter(message, death.end()));
				return;
			}
			relay(KIND_PKDEATH, message, EmbedStyle.DEATH, EmbedStyle.pkIcon(),
				isSubject(message, death.start(), rsn));
			return;
		}

		final Matcher kill = PK_KILL.matcher(message);
		if (kill.find())
		{
			relay(KIND_PK, message, EmbedStyle.PK, EmbedStyle.pkIcon(),
				isSubject(message, kill.start(), rsn));
			return;
		}

		final Matcher lootKey = LOOT_KEY.matcher(message);
		if (lootKey.find())
		{
			relay(KIND_LOOTKEY, message, EmbedStyle.LOOTKEY, EmbedStyle.lootKeyIcon(),
				isSubject(message, lootKey.start(), rsn));
		}
	}

	@Nullable
	static String killerAfter(final String message, final int from)
	{
		if (from < 0 || from > message.length())
		{
			return null;
		}
		String tail = message.substring(from).trim();
		final int and = tail.indexOf(" and ");
		if (and > 0)
		{
			tail = tail.substring(0, and);
		}
		tail = tail.replaceAll("[.!]+$", "").trim();
		return tail.isEmpty() ? null : tail;
	}

	static boolean subjectMatches(final String message, @Nullable final String rsn)
	{
		return isSubject(message, subjectEnd(message), rsn);
	}

	private static boolean isSubject(final String message, final int verbStart, @Nullable final String rsn)
	{
		if (rsn == null || verbStart <= 0)
		{
			return false;
		}
		return message.substring(0, verbStart).trim().equalsIgnoreCase(rsn);
	}

	private static int subjectEnd(final String message)
	{
		final Matcher death = PK_DEATH.matcher(message);
		if (death.find())
		{
			return death.start();
		}
		final Matcher kill = PK_KILL.matcher(message);
		if (kill.find())
		{
			return kill.start();
		}
		final Matcher lootKey = LOOT_KEY.matcher(message);
		if (lootKey.find())
		{
			return lootKey.start();
		}
		return -1;
	}

	public static boolean isPvpBroadcast(final String message)
	{
		return PK_DEATH.matcher(message).find()
			|| PK_KILL.matcher(message).find()
			|| LOOT_KEY.matcher(message).find();
	}

	private void relay(final String kind, final String message, final Color color,
		final String icon, final boolean allowScreenshot)
	{
		final String rsn = localRsn();
		final WebhookPayload payload = WebhookPayload.of(kind, rsn)
			.author(rsn != null ? rsn : "Clan", icon)
			.description(WebhookPayload.quote(WebhookPayload.bold(message)))
			.color(color);

		post(payload, allowScreenshot);
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
		return client.getVarbitValue(VarbitID.IRONMAN) == 2;
	}

	@Nullable
	private Actor confirmedKiller()
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
		return null;
	}

	@Nullable
	private Actor probableKiller()
	{
		final Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}

		return client.getTopLevelWorldView().npcs().stream()
			.filter(npc -> npc != null && npc.getInteracting() == local && !npc.isDead())
			.filter(npc -> npc.getCombatLevel() > 0)
			.max(Comparator.comparingInt(NPC::getCombatLevel))
			.orElse(null);
	}

	private void post(final WebhookPayload payload, final boolean allowScreenshot)
	{
		final String webhook = config.combatWebhook();
		if (allowScreenshot && config.combatScreenshot())
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

	private static final class PendingDeath
	{
		private final WebhookPayload payload;
		private final boolean wantScreenshot;
		private String killer;
		private boolean killerConfirmed;
		private Screenshot shot;
		private boolean shotReady;
		private int ticks;
		private boolean sent;

		private PendingDeath(final WebhookPayload payload, final boolean wantScreenshot)
		{
			this.payload = payload;
			this.wantScreenshot = wantScreenshot;
		}
	}
}
