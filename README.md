# Collective

Multi-clan Discord integration for OSRS clans. Posts gameplay events to your clan's Discord through webhooks. Every feature is opt-in and default-off — nothing leaves your client until you enable a feature and add a webhook URL.

## What it does

- **Webhook delivery** — async, multiple URLs, retries on transient failures, Discord rate-limit aware.
- **Screenshots** — optional, with chat privacy (default: hide all chat) so private messages don't leak into a post.
- **Drops** — posts item drops from NPC kills and PKs above a value threshold you set.
- **Clan chat relay** — posts your clan channel's messages, broadcasts, and member joins/leaves.

## Configure

Get your webhook URL(s) from your clan's Discord and paste them into the plugin's config. Each feature has its own webhook field, so you can route different event types to different channels. A feature stays off until you give it a URL.

Webhook URLs are effectively credentials — anyone with the URL can post to that channel. When a feature is active it sends data to Discord, which (as with any web request) submits your IP to Discord, a third-party server not controlled by RuneLite. Nothing is sent to any Collective server.
