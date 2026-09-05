# Collective

Multi-clan Discord integration for OSRS clans running on Collective Bot.

Posts gameplay events to your clan's Discord through webhooks. Every feature is opt-in and default-off — nothing leaves your client until you enable a feature and provide a webhook URL.

## Status

This plugin is built in phases. What is implemented today:

- **Shared webhook infrastructure** — async delivery, multi-URL fan-out, retry with exponential backoff on transient failures, Discord 429/Retry-After handling, and per-webhook health tracking.
- **Screenshot capture** — with 3-way chat privacy (default: hide all chat) so private conversations don't leak into a post, and a PNG→JPEG size-ceiling fallback.
- **Drops feed** — posts item drops (NPC kills and PKs) to a Discord webhook above a configurable value threshold, with optional screenshot.
- **Clan chat relay** — posts your clan channel's messages, broadcasts (drops, level-ups, quests, combat achievements, collection log), and member joins/leaves to a Discord webhook.

Everything under **Planned** below is roadmap, not yet built.

## Install

Not yet on the Plugin Hub. This is an in-development build.

## Configure

Get your webhook URL(s) from your clan's Discord (via Collective Bot) and paste them into the plugin's config. Each feature has its own webhook field so you can route different event types to different channels.

Webhook URLs are effectively credentials — anyone with the URL can post to that channel. The plugin validates that each URL is a well-formed Discord webhook and ignores anything else.

- **Screenshots** — chat privacy mode (default: hide all chat), screenshot scale.
- **Network** — request timeout, max retries, base retry delay.
- **Drops** — webhook URL(s), minimum value (default 50,000 gp), include-screenshot toggle.
- **Clan chat** — webhook URL(s), and per-type toggles for messages, broadcasts, and member events.

## Privacy

Each feature is off until you provide its webhook URL; providing a URL is what turns the feature on. When a feature is active it sends data to the Discord webhook you configure. As with any web request, this submits your IP address to Discord, a third-party server not controlled or verified by RuneLite developers. Clan chat relay additionally sends the messages, broadcasts, and member events from your configured clan channel. Nothing is sent to any Collective server — the plugin talks only to Discord.

## Implemented components

- `net/WebhookClient` — the single networking chokepoint. Every feature posts through here; nothing talks to Discord directly. Async, multi-URL, retrying, 429-aware, health-tracked. Validates Discord webhook URLs (HTTPS, Discord host, `/api/webhooks/<id>/<token>` shape).
- `net/WebhookPayload` — embed builder with markdown helpers (quote, code block, bold) for styled Discord embeds.
- `net/EmbedStyle` — per-event colours and OSRS icon resolvers (item icons from RuneLite's cache, rank crests from the wiki).
- `net/WebhookHealth` — immutable per-feed status snapshot read by the panel.
- `util/ScreenshotUtil` — frame capture with chat-privacy hiding (on the client thread) and a PNG→JPEG size-ceiling fallback. Returns a `Screenshot` carrying bytes, MIME type, and filename together so the multipart upload is always consistent.
- `util/Screenshot` — the encoded-image value type.
- `domain/ChatPrivacyMode` — the chat privacy enum.
- `notifiers/DropNotifier` — the drops feed. Subscribes to core `NpcLootReceived` and `PlayerLootReceived`, so no other plugin is required.
- `ui/` — the sidebar panel: header, Feed Health section, Setup section, footer links.

## Planned

Not yet implemented — the intended full scope:

- Clan chat relay to Discord
- Level-ups, personal bests, combat achievements, quests, collection log, pets
- Deaths (with HCIM tag-loss mode)
- Bingo tile auto-submission
- Event attendance heartbeat
- Rank icons in the local chatbox
- Chat highlighting and @mention notifications
- Local chat log
- Pickpocket / chest / event drop sources for the drops feed

## Development

Requires a Jagex account session for in-client testing. See the RuneLite wiki "Using Jagex Accounts" guide: configure the launcher with `--insecure-write-credentials`, launch once via the Jagex launcher to write `.runelite/credentials.properties`, then run the dev client (`./gradlew run`). Do not share that credentials file.

Headless unit tests cover the webhook payload contract, Discord URL validation, screenshot MIME/fallback logic, and drop thresholds:

```
./gradlew test
```
