# Collective

Multi-clan Discord integration for OSRS clans. Posts gameplay events to your clan's Discord through webhooks. Every feature is opt-in and default-off — nothing leaves your client until you enable a feature and paste a webhook URL.

## What it does

- **Webhook delivery** — asynchronous OkHttp delivery to one or more Discord webhook URLs, with bounded retries, exponential backoff, and Discord rate-limit (`Retry-After`) handling.
- **Screenshots** — optional per feature, with a chat-privacy mode (default: hide all chat) so private messages are not captured.
- **Drops** — item drops from NPC kills and player kills above a value threshold you set.
- **Clan chat relay** — clan channel messages (with sender name and rank) and clan broadcasts.
- **Clan administration** — applications, invites, joins, leaves, and rank changes.
- **Milestones** — combat achievement tasks, personal bests, pets, level-ups, XP milestones, quests, and clues.
- **Personal bests** — a dedicated feed for your own PBs (with an optional completion screenshot) and clanmates' PB broadcasts.
- **Combat** — your deaths, with an estimated value of items lost and the killer when it can be determined.
- **PvP** — relays clan PvP broadcasts (kills, deaths, loot keys).
- **Event attendance** — a side-panel tool that records which clan members appear near you during an event, copyable to your clipboard.

## Configure

Get your webhook URL(s) from your clan's Discord (Server Settings → Integrations → Webhooks) and paste them into the plugin's config. Each feature has its own webhook field, so you can route different event types to different channels. A feature stays off until you give it a URL and enable its toggle.

## Privacy and data flow

Collective has **no server of its own**. When you enable a feature and add a webhook, the data below is sent directly to the Discord webhook URL you configured. As with any web request, that also discloses your IP address to Discord — a third party not controlled or verified by RuneLite.

**Webhook URLs are credentials.** Anyone who has one can post to that channel. Collective never logs webhook URLs in full, never puts them in error messages, and never exports them.

What each feature transmits when enabled:

| Feature | Data sent | Other players' names? |
| --- | --- | --- |
| Drops | Your name, items, quantities, values | No |
| Deaths | Your name, estimated value lost, killer (if known) | Sometimes (killer) |
| Level-ups / XP | Your name, skill, level/XP | No |
| Quests | Your name, quest name | No |
| Clues | Your name, tier, reward items and value | No |
| Pets | Your name | No |
| Combat achievements | Your name, task, tier | No |
| Personal bests | Your name (or clanmates' names from the broadcast), activity, time | Sometimes |
| Clan chat | Clan name, sender name, sender rank, message text | Yes |
| Clan admin | Clan name, member name, event (join/leave/invite/application) | Yes |
| Rank changes | Clan name, member name, old and new rank | Yes |
| PvP broadcasts | The player names in the clan broadcast | Yes |
| Event attendance | Observed clan member names, event duration | Yes |
| Screenshots | An image of your client, which may show other players and (unless hidden) chat | Potentially |

Screenshot chat privacy defaults to hiding all chat. Rank-change tracking only stores a local snapshot of member ranks while that feature is enabled, purely to detect changes on your own client.

## Development

Requires JDK 11. Run the tests and a development client from the plugin root:

```
./gradlew test
./gradlew run
```

Collective is licensed under BSD-2-Clause (see `LICENSE`).
