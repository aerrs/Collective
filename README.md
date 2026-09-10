# Collective

Clan tools and Discord integration for OSRS clans. Shows your clan in an in-client side panel and posts clan events to your Discord through webhooks. Every feature is opt-in and default-off — nothing leaves your client until you enable a feature and configure it.

## What it does

- **Clan panel** — an in-client side panel showing your clan name, your rank, online/total member counts, and a scrollable roster of every member with their in-game rank icons. Built from the game's own clan data; no network.
- **Clan stats** — your clan's EHP, EHB, total XP and member count, shown in the panel and read from [Clan Collective](https://clancollective.cc) (which sources them from Wise Old Man / TempleOSRS). Opt-in; sends only your clan name or slug, reads back aggregate figures.
- **Discover clans** — browse and search the [Clan Collective](https://clancollective.cc) clan directory from the panel. Each result opens the clan's web profile or copies its clan-chat name. Read-only: it fetches the public list of approved clans and sends no personal data.
- **Clan chat relay** — clan channel messages (with sender name and rank) and clan broadcasts, posted to a Discord webhook.
- **Clan administration** — applications, invites, joins, leaves, and rank changes, posted to a Discord webhook.
- **Event attendance** — a side-panel tool that records which clan members appear near you during an event, copyable to your clipboard.
- **Webhook delivery** — asynchronous OkHttp delivery to one or more Discord webhook URLs, with bounded retries, exponential backoff, and Discord rate-limit (`Retry-After`) handling.

## Configure

Get your webhook URL(s) from your clan's Discord (Server Settings → Integrations → Webhooks) and paste them into the plugin's config. Each feed has its own webhook field, so you can route different event types to different channels. A feed stays off until you give it a URL and enable its toggle.

## Privacy and data flow

When you enable a feed and add a webhook, the data below is sent directly to the Discord webhook URL you configured. As with any web request, that also discloses your IP address to Discord — a third party not controlled or verified by RuneLite. The **Clan stats** and **Discover clans** features talk to Clan Collective (`clancollective.cc`) instead of Discord.

**Webhook URLs are credentials.** Anyone who has one can post with it. Collective never logs webhook URLs in full, never puts them in error messages, and never exports them.

What each feature transmits when enabled:

| Feature | Data sent | Destination | Other players' names? |
| --- | --- | --- | --- |
| Clan chat | Clan name, sender name, sender rank, message text | Discord webhook | Yes |
| Clan admin | Clan name, member name, event (join/leave/invite/application) | Discord webhook | Yes |
| Rank changes | Clan name, member name, old and new rank | Discord webhook | Yes |
| Event attendance | Observed clan member names, event duration | Clipboard only | Yes |
| Clan stats panel | Your clan name or slug (read-only lookup) | Clan Collective | No |
| Discover clans | Your search text and IP address (read-only lookup of approved clans) | Clan Collective | No |

The clan panel and roster read only the game's own clan data and make no network requests. Rank-change tracking stores a local snapshot of member ranks while that feature is enabled, purely to detect changes on your own client.

## Development

Requires JDK 11. Run the tests and a development client from the plugin root:

```
./gradlew test
./gradlew run
```

Collective is licensed under BSD-2-Clause (see `LICENSE`).
