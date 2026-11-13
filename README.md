# Collective

Clan bingo and clan listings for [Clan Collective](https://clancollective.cc). Everything is off by default; nothing leaves your client until you turn it on.

## Features

- **Auto-ticking tiles**: if your clan runs a bingo on Clan Collective and you are on a team, drops, collection log slots, pets, XP, levels, kill counts, kill times and clues that match an open tile tick it automatically. A game message confirms each one.
- **Team board**: the side panel shows your team's board, its place among the other teams, points, tiles and lines, and the latest tiles your team unlocked and who got them. In hidden-tile bingos, locked tiles show a padlock and the panel shows your team's picks.
- **Clan listings**: the side panel shows your clan's Clan Collective listing. When you are not in a clan it lists clans to browse, filtered by type.
- **Screenshots**: optionally attach a screenshot when something ticks a tile, so captains can check it. Kill counts and XP only send one on the kill or gain that finishes the tile.

## Data sent

| Setting | Data sent | When |
| --- | --- | --- |
| Clan bingo | Clan name, RSN, a one-way hash of your account | Normally every 15 minutes, or when you open the panel, to load your team's board (the hash keeps your team's unlocked tiles private in hidden-tile bingos) |
| Clan bingo | Clan name, RSN, a one-way hash of your account, matching names and amounts | Only when something matches an open tile (XP is batched every 5 minutes unless it finishes a tile) |
| Clan listings | Your clan name | When your clan loads after you log in, when you join a clan, or when you turn the setting on while in one (the same view is not fetched again for 5 minutes), and when you retry after an error |
| Clan listings | Nothing about you (the chosen clan type) | When you are not in a clan or logged out, including at startup, when you pick a type, and when you retry after an error |
| Bingo screenshots | A screenshot of your game client | Only when something matches an open tile (kill counts and XP only when they finish it), and only if turned on |

Everything goes to `clancollective.cc` over HTTPS, which also discloses your IP address. Tile icons and clan logos are loaded from the same site. No data about other players is collected. Bingo screenshots hide the chatbox and private messages; anything else on your screen at that moment is included.

Drops are read from RuneLite's Loot Tracker, so keep that plugin on. Collection log slots need the in-game collection log chat notification turned on.

## Development

Requires JDK 11.

```
./gradlew test
./gradlew run
```

Licensed under BSD-2-Clause (see `LICENSE`).
