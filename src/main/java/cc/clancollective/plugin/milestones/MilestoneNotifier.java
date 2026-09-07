package cc.clancollective.plugin.milestones;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.domain.CombatTier;
import cc.clancollective.plugin.domain.LevelNotifyMode;
import cc.clancollective.plugin.net.EmbedStyle;
import cc.clancollective.plugin.net.WebhookClient;
import cc.clancollective.plugin.net.WebhookPayload;
import cc.clancollective.plugin.util.Screenshot;
import cc.clancollective.plugin.util.ScreenshotUtil;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class MilestoneNotifier
{
	private static final String KIND_CA = "ca";
	private static final String KIND_PB = "pb";
	private static final String KIND_PET = "pet";
	private static final String KIND_LEVEL = "levelup";
	private static final String KIND_QUEST = "quest";
	private static final String KIND_CLUE = "clue";

	private static final long XP_MILLION = 1_000_000L;
	private static final int MAX_REAL_LEVEL = 99;
	private static final int PB_DEDUP_TICKS = 3;

	private static final Pattern CA_PATTERN = Pattern.compile(
		"Congratulations, you've completed an? (?<tier>\\w+) combat task: (?<task>.+?)\\.?$");
	private static final Pattern CA_POINTS_SUFFIX = Pattern.compile("\\s*\\(\\d+ points?\\)$");
	private static final Pattern NEW_PB_PATTERN = Pattern.compile(
		"(?<time>[0-9]+:[0-9]{2}(?:\\.[0-9]+)?)\\.?\\s*\\(new personal best\\)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PB_BROADCAST_PATTERN = Pattern.compile(
		"(?<names>.+?) achieved a new (?<activity>.+?) personal best: (?<time>[0-9:]+(?:\\.[0-9]+)?)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern CLUE_PATTERN = Pattern.compile(
		"You have completed (?<count>\\d+) (?<tier>\\w+) Treasure Trails?\\.");

	private static final String[] PET_MESSAGES = {
		"You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack",
		"You have a funny feeling like you would have been followed",
	};

	private final Client client;
	private final CollectiveConfig config;
	private final ItemManager itemManager;
	private final WebhookClient webhookClient;
	private final ScreenshotUtil screenshotUtil;

	private final Map<Skill, Integer> lastLevel = new EnumMap<>(Skill.class);
	private final Map<Skill, Long> lastXpMilestone = new EnumMap<>(Skill.class);
	private final Set<String> recentPbTimes = new HashSet<>();
	private int pbDedupTicks;

	private String pendingClueTier = "";
	private int pendingClueCount = -1;
	private int clueBadTicks = 0;

	@Inject
	public MilestoneNotifier(final Client client, final CollectiveConfig config,
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
		lastLevel.clear();
		lastXpMilestone.clear();
		recentPbTimes.clear();
		pbDedupTicks = 0;
		clearPendingClue();
	}

	private boolean enabled()
	{
		return !config.milestonesWebhook().trim().isEmpty();
	}

	private boolean pbEnabled()
	{
		return !pbWebhook().trim().isEmpty();
	}

	private String pbWebhook()
	{
		final String pb = config.pbWebhook().trim();
		return pb.isEmpty() ? config.milestonesWebhook() : config.pbWebhook();
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

	public void onGameMessage(final String message)
	{
		if (config.notifyPersonalBests() && pbEnabled() && handlePersonalBest(message))
		{
			return;
		}

		if (!enabled())
		{
			return;
		}

		if (config.notifyCombatTasks() && handleCombatTask(message))
		{
			return;
		}
		if (config.notifyPets() && handlePet(message))
		{
			return;
		}
		if (config.notifyClues())
		{
			parseClue(message);
		}
	}

	public void onClanBroadcast(final String rawMessage)
	{
		if (!config.notifyPersonalBests() || !pbEnabled() || !clanFilterMatches())
		{
			return;
		}

		final String message = Text.removeTags(rawMessage);
		final PbBroadcast pb = parsePbBroadcast(message);
		if (pb == null)
		{
			return;
		}

		final String rsn = localRsn();
		final boolean localNamed = rsn != null && containsIgnoreCase(pb.names, rsn);

		if (localNamed)
		{
			if (recentPbTimes.contains(normalizeTime(pb.time)))
			{
				return;
			}
			rememberPbTime(pb.time);
			postPersonalBest(rsn, rsn, pb.time, pb.activity, true);
		}
		else
		{
			final String author = pb.names.isEmpty() ? "A clan member" : String.join(", ", pb.names);
			postPersonalBest(null, author, pb.time, pb.activity, false);
		}
	}

	private boolean handleCombatTask(final String message)
	{
		final Matcher matcher = CA_PATTERN.matcher(message);
		if (!matcher.find())
		{
			return false;
		}

		final CombatTier tier = CombatTier.parse(matcher.group("tier"));
		if (tier == null || tier.ordinal() < config.combatTaskMinTier().ordinal())
		{
			return true;
		}

		final String task = CA_POINTS_SUFFIX.matcher(matcher.group("task")).replaceFirst("");
		final String rsn = localRsn();
		final String author = rsn != null ? rsn : "A clan member";

		final WebhookPayload payload = WebhookPayload.of(KIND_CA, rsn)
			.author(author, EmbedStyle.combatTaskIcon())
			.description(WebhookPayload.bold(author) + " completed a " + tier + " combat task:")
			.color(EmbedStyle.CA)
			.field("Task", task, false)
			.field("Tier", tier.toString(), true);

		post(payload);
		return true;
	}

	private boolean handlePersonalBest(final String message)
	{
		final String time = matchNewPbTime(message);
		if (time == null)
		{
			return false;
		}

		final String rsn = localRsn();
		rememberPbTime(time);
		postPersonalBest(rsn, rsn != null ? rsn : "A clan member", time, null, true);
		return true;
	}

	static String matchNewPbTime(final String rawMessage)
	{
		final Matcher matcher = NEW_PB_PATTERN.matcher(Text.removeTags(rawMessage));
		return matcher.find() ? matcher.group("time") : null;
	}

	public static boolean isPersonalBestBroadcast(final String message)
	{
		return PB_BROADCAST_PATTERN.matcher(message).find();
	}

	@Nullable
	static PbBroadcast parsePbBroadcast(final String message)
	{
		final Matcher matcher = PB_BROADCAST_PATTERN.matcher(message);
		if (!matcher.find())
		{
			return null;
		}

		final List<String> names = new ArrayList<>();
		for (final String name : matcher.group("names").split(",\\s*|\\s+and\\s+"))
		{
			final String trimmed = name.trim();
			if (!trimmed.isEmpty())
			{
				names.add(trimmed);
			}
		}
		return new PbBroadcast(names, matcher.group("activity").trim(), matcher.group("time").trim());
	}

	private void postPersonalBest(@Nullable final String rsn, final String author, final String time,
		@Nullable final String activity, final boolean allowScreenshot)
	{
		final WebhookPayload payload = WebhookPayload.of(KIND_PB, rsn)
			.author(author, EmbedStyle.pbIcon())
			.description(WebhookPayload.bold(author) + " achieved a new personal best!")
			.color(EmbedStyle.PB)
			.field("Time", time, true);

		if (activity != null && !activity.isEmpty())
		{
			payload.field("Activity", activity, true);
		}

		final String webhook = pbWebhook();
		if (allowScreenshot && config.pbScreenshot())
		{
			screenshotUtil.capture(shot ->
			{
				payload.image(shot.getFilename());
				webhookClient.send(webhook, payload, shot);
			});
		}
		else
		{
			webhookClient.send(webhook, payload);
		}
	}

	private void rememberPbTime(final String time)
	{
		recentPbTimes.add(normalizeTime(time));
		pbDedupTicks = PB_DEDUP_TICKS;
	}

	private static String normalizeTime(final String time)
	{
		final int dot = time.indexOf('.');
		return dot >= 0 ? time.substring(0, dot) : time;
	}

	private static boolean containsIgnoreCase(final List<String> names, final String target)
	{
		for (final String name : names)
		{
			if (name.equalsIgnoreCase(target))
			{
				return true;
			}
		}
		return false;
	}

	private boolean handlePet(final String message)
	{
		for (final String prefix : PET_MESSAGES)
		{
			if (message.contains(prefix))
			{
				final String rsn = localRsn();
				final String author = rsn != null ? rsn : "A clan member";

				final WebhookPayload payload = WebhookPayload.of(KIND_PET, rsn)
					.author(author, EmbedStyle.petIcon())
					.description(WebhookPayload.bold(author) + " has a funny feeling they're being followed.")
					.color(EmbedStyle.PET);

				post(payload);
				return true;
			}
		}
		return false;
	}

	private void parseClue(final String message)
	{
		final Matcher matcher = CLUE_PATTERN.matcher(message);
		if (!matcher.find())
		{
			return;
		}

		final CombatTier tier = CombatTier.parse(matcher.group("tier"));
		if (tier == null || tier.ordinal() < config.clueMinTier().ordinal())
		{
			return;
		}

		pendingClueTier = matcher.group("tier");
		pendingClueCount = Integer.parseInt(matcher.group("count"));
		clueBadTicks = 0;
	}

	public void onWidgetLoaded(final WidgetLoaded event)
	{
		if (!enabled())
		{
			return;
		}

		if (event.getGroupId() == InterfaceID.QUESTSCROLL && config.notifyQuests())
		{
			handleQuest();
		}
		else if (event.getGroupId() == InterfaceID.TRAIL_REWARDSCREEN && config.notifyClues())
		{
			handleClue();
		}
	}

	private void handleQuest()
	{
		final Widget title = client.getWidget(InterfaceID.Questscroll.QUEST_TITLE);
		if (title == null)
		{
			return;
		}

		final String questName = parseQuestTitle(title.getText());
		if (questName == null)
		{
			return;
		}

		final String rsn = localRsn();
		final String author = rsn != null ? rsn : "A clan member";

		final WebhookPayload payload = WebhookPayload.of(KIND_QUEST, rsn)
			.author(author, EmbedStyle.questIcon())
			.description(WebhookPayload.bold(author) + " completed a quest:")
			.color(EmbedStyle.QUEST)
			.field("Quest", questName, false);

		post(payload);
	}

	private void handleClue()
	{
		if (pendingClueTier.isEmpty())
		{
			return;
		}

		final Widget container = client.getWidget(InterfaceID.TrailRewardscreen.ITEMS);
		if (container == null)
		{
			clearPendingClue();
			return;
		}

		final Widget[] children = container.getChildren();
		if (children == null)
		{
			clearPendingClue();
			return;
		}

		final Map<Integer, Integer> items = new HashMap<>();
		for (final Widget child : children)
		{
			if (child == null)
			{
				continue;
			}
			final int itemId = child.getItemId();
			final int quantity = child.getItemQuantity();
			if (itemId > -1 && quantity > 0)
			{
				items.merge(itemId, quantity, Integer::sum);
			}
		}

		long total = 0;
		final List<String> lines = new ArrayList<>();
		int topId = -1;
		long topValue = -1;
		for (final Map.Entry<Integer, Integer> entry : items.entrySet())
		{
			final int itemId = entry.getKey();
			final int quantity = entry.getValue();
			final long value = (long) itemManager.getItemPrice(itemId) * quantity;
			total += value;
			if (value > topValue)
			{
				topValue = value;
				topId = itemId;
			}
			final StringBuilder sb = new StringBuilder();
			if (quantity > 1)
			{
				sb.append(QuantityFormatter.formatNumber(quantity)).append(" x ");
			}
			sb.append(itemName(itemId));
			if (value > 0)
			{
				sb.append(" (").append(QuantityFormatter.quantityToStackSize(value)).append(" gp)");
			}
			lines.add(sb.toString());
		}

		if (total < config.clueMinValue())
		{
			clearPendingClue();
			return;
		}

		final String tierName = pendingClueTier.substring(0, 1).toUpperCase()
			+ pendingClueTier.substring(1).toLowerCase();
		final String rsn = localRsn();
		final String author = rsn != null ? rsn : "A clan member";

		final WebhookPayload payload = WebhookPayload.of(KIND_CLUE, rsn)
			.author(author, EmbedStyle.clueIcon())
			.description(WebhookPayload.bold(author) + " completed a " + tierName + " clue scroll ("
				+ pendingClueCount + "):\n" + WebhookPayload.codeBlock(String.join("\n", lines)))
			.color(EmbedStyle.CLUE)
			.field("Value", QuantityFormatter.quantityToStackSize(total) + " gp", true);

		if (topId >= 0)
		{
			payload.thumbnail(EmbedStyle.itemIcon(topId));
		}

		post(payload);
		clearPendingClue();
	}

	public void onStatChanged(final StatChanged event)
	{
		if (!enabled() || !config.notifyLevels())
		{
			return;
		}

		final Skill skill = event.getSkill();
		if (skill == null)
		{
			return;
		}

		final int level = event.getLevel();
		final long xp = event.getXp();

		final Integer previous = lastLevel.get(skill);
		lastLevel.put(skill, level);

		if (previous == null)
		{
			lastXpMilestone.put(skill, xp / XP_MILLION);
			return;
		}

		if (level > previous)
		{
			maybeNotifyLevel(skill, level);
		}

		maybeNotifyXpMilestone(skill, xp);
	}

	private void maybeNotifyLevel(final Skill skill, final int level)
	{
		if (!shouldNotifyLevel(config.levelMode(), config.levelInterval(), level))
		{
			return;
		}

		final String rsn = localRsn();
		final String author = rsn != null ? rsn : "A clan member";
		final String skillName = displaySkill(skill);

		final WebhookPayload payload = WebhookPayload.of(KIND_LEVEL, rsn)
			.author(author, EmbedStyle.skillIcon(skill.getName()))
			.description(WebhookPayload.bold(author) + " levelled up " + skillName + "!")
			.color(EmbedStyle.LEVEL)
			.field("Skill", skillName, true)
			.field("Level", String.valueOf(level), true);

		post(payload);
	}

	private void maybeNotifyXpMilestone(final Skill skill, final long xp)
	{
		final int interval = config.xpMilestoneInterval();
		if (interval <= 0)
		{
			return;
		}

		final long milestone = xp / (interval * XP_MILLION);
		final Long previous = lastXpMilestone.get(skill);
		lastXpMilestone.put(skill, milestone);

		if (previous == null || milestone <= previous || milestone <= 0)
		{
			return;
		}

		final long millions = milestone * interval;
		final String rsn = localRsn();
		final String author = rsn != null ? rsn : "A clan member";
		final String skillName = displaySkill(skill);

		final WebhookPayload payload = WebhookPayload.of(KIND_LEVEL, rsn)
			.author(author, EmbedStyle.skillIcon(skill.getName()))
			.description(WebhookPayload.bold(author) + " reached " + millions + "M "
				+ skillName + " XP!")
			.color(EmbedStyle.LEVEL)
			.field("Skill", skillName, true)
			.field("XP", millions + "M", true);

		post(payload);
	}

	public void onGameTick()
	{
		if (pbDedupTicks > 0 && --pbDedupTicks == 0)
		{
			recentPbTimes.clear();
		}

		if (pendingClueTier.isEmpty())
		{
			return;
		}
		clueBadTicks++;
		if (clueBadTicks > 1)
		{
			clearPendingClue();
		}
	}

	private void post(final WebhookPayload payload)
	{
		final String webhook = config.milestonesWebhook();
		if (config.milestonesScreenshot())
		{
			captureAndSend(webhook, payload);
		}
		else
		{
			webhookClient.send(webhook, payload);
		}
	}

	private void captureAndSend(final String webhook, final WebhookPayload payload)
	{
		final Consumer<Screenshot> consumer = shot ->
		{
			payload.image(shot.getFilename());
			webhookClient.send(webhook, payload, shot);
		};
		screenshotUtil.capture(consumer);
	}

	private void clearPendingClue()
	{
		pendingClueTier = "";
		pendingClueCount = -1;
		clueBadTicks = 0;
	}

	private String itemName(final int itemId)
	{
		final var comp = itemManager.getItemComposition(itemId);
		return comp != null ? comp.getName() : "Item " + itemId;
	}

	private String localRsn()
	{
		final Player local = client.getLocalPlayer();
		return local != null ? local.getName() : null;
	}

	static boolean shouldNotifyLevel(final LevelNotifyMode mode, final int interval, final int level)
	{
		switch (mode)
		{
			case EVERY_LEVEL:
				return true;
			case NINETY_NINE_ONLY:
				return level == MAX_REAL_LEVEL;
			case INTERVAL:
				return level == MAX_REAL_LEVEL || (interval > 0 && level % interval == 0);
			default:
				return false;
		}
	}

	@Nullable
	static String parseQuestTitle(@Nullable final String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String text = raw.replaceAll("<[^>]*>", "").trim();
		final String[] prefixes = {"You have completed", "Congratulations! You have completed", "Congratulations!"};
		for (final String prefix : prefixes)
		{
			if (text.startsWith(prefix))
			{
				text = text.substring(prefix.length()).trim();
			}
		}
		if (text.endsWith("!") || text.endsWith("."))
		{
			text = text.substring(0, text.length() - 1).trim();
		}
		return text.isEmpty() ? null : text;
	}

	private static String displaySkill(final Skill skill)
	{
		final String name = skill.getName();
		return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
	}

	static final class PbBroadcast
	{
		final List<String> names;
		final String activity;
		final String time;

		PbBroadcast(final List<String> names, final String activity, final String time)
		{
			this.names = names;
			this.activity = activity;
			this.time = time;
		}
	}
}
