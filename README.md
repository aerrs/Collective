# Collective

Clan tools and Discord integration for OSRS clans. Shows your clan in an in-client side panel and posts clan events to your Discord through webhooks. Every feature is opt-in and default-off — nothing leaves your client until you enable a feature and configure it.

## What it does

- **Clan panel** — an in-client side panel showing your clan name, your rank, online/total member counts, and a scrollable roster of every member with their in-game rank icons. Built from the game's own clan data; no network.
- **Clan stats** — your clan's EHP, EHB, total XP and member count, shown in the panel and read from [Clan Collective](https://clancollective.cc) (which sources them from Wise Old Man / TempleOSRS). Opt-in; sends only your clan name or slug, reads back aggregate figures.
- **Playtime** — optionally tracks your logged-in time and shows your clan's playtime leaderboard (last 7 days) in the panel. Sends your name, your unique account identifier (account hash), clan, and elapsed time to Clan Collective using a token your clan generates. Off until you enable it and paste the token.
- **Clan chat relay** — clan channel messages (with sender name and rank) and clan broadcasts, posted to a Discord webhook.
- **Clan administration** — applications, invites, joins, leaves, and rank changes, posted to a Discord webhook.
- **Event attendance** — a side-panel tool that records which clan members appear near you during an event, copyable to your clipboard.
- **Webhook delivery** — asynchronous OkHttp delivery to one or more Discord webhook URLs, with bounded retries, exponential backoff, and Discord rate-limit (`Retry-After`) handling.

## Configure

Get your webhook URL(s) from your clan's Discord (Server Settings → Integrations → Webhooks) and paste them into the plugin's config. Each feed has its own webhook field, so you can route different event types to different channels. A feed stays off until you give it a URL and enable its toggle. For playtime, a clan admin generates a token on the clan's Clan Collective dashboard and members paste it into the plugin.

## Privacy and data flow

When you enable a feed and add a webhook, the data below is sent directly to the Discord webhook URL you configured. As with any web request, that also discloses your IP address to Discord — a third party not controlled or verified by RuneLite. The **Clan stats** and **Playtime** features talk to Clan Collective (`clancollective.cc`) instead of Discord.

**Webhook URLs and the playtime token are credentials.** Anyone who has one can post with it. Collective never logs webhook URLs in full, never puts them in error messages, and never exports them.

What each feature transmits when enabled:

| Feature | Data sent | Destination | Other players' names? |
| --- | --- | --- | --- |
| Clan chat | Clan name, sender name, sender rank, message text | Discord webhook | Yes |
| Clan admin | Clan name, member name, event (join/leave/invite/application) | Discord webhook | Yes |
| Rank changes | Clan name, member name, old and new rank | Discord webhook | Yes |
| Event attendance | Observed clan member names, event duration | Clipboard only | Yes |
| Clan stats panel | Your clan name or slug (read-only lookup) | Clan Collective | No |
| Playtime | Your name, unique account identifier (account hash), clan name/slug, elapsed logged-in time, token, IP address | Clan Collective | No |

The clan panel and roster read only the game's own clan data and make no network requests. Rank-change tracking stores a local snapshot of member ranks while that feature is enabled, purely to detect changes on your own client.

## Development

Requires JDK 11. Run the tests and a development client from the plugin root:

```
./gradlew test
./gradlew run
```

Collective is licensed under BSD-2-Clause (see `LICENSE`).
