# XaliFriends

Friend system for Paper and Folia, laid out like [XaliChat](../chat/README.md). It replaces FriendSystem 4.9.8 by
sk8ingDuck, whose jars are kept in `ref` for reference (see below), and it moves that plugin's data into its own tables and drops the old ones.
Parties are not part of it.

What it does: friend requests, favourites, a status line, settings, a friend menu (with [XaliMenu](../menu/README.md)),
and private messages that can be limited to friends (with [XaliChat](../chat/README.md)). What it doesn't do yet: tell
friends on *other* servers that someone came or went, and jump to a friend's server (both need the proxy, and the Velocity
plugin only registers its channel so far).

## Modules

| Module | What it is |
|---|---|
| `friends/paper` | The Paper plugin, `xali-friends-<version>-paper.jar`: commands, storage (Exposed over SQLite or MariaDB/MySQL), the cache, the placeholders and the hooks into XaliMenu and XaliChat. |
| `friends/velocity` | The Velocity plugin, `xali-friends-<version>-velocity.jar`. Registers the `xalifriends:main` channel; it will tell friends when someone joins, leaves or switches server. |
| `friends/common` | What both share (the channel name). No platform API; targets Java 17. |

The descriptors come from the [Gradle plugin](../plugin/README.md); the `paper { }` and `velocity { }` blocks are in
`friends/build.gradle.kts`. `./gradlew :friends:paper:runServer` and `:friends:velocity:runProxy` start the shared dev
server and proxy with this plugin installed.

XaliMenu, XaliChat and PlaceholderAPI are all optional. Without them the friend commands work and `/friend` prints its help.

## Commands

| Command | Aliases | Description |
|---|---|---|
| `/xfriend` | `friend`, `f` | Open the friend menu (the help, when there is no menu). |
| `/xfriend add <player>` | | Ask a player to be friends. They need friend requests switched on. |
| `/xfriend accept <player\|all>`, `deny <player\|all>` | | Answer a request. |
| `/xfriend remove <player>`, `favourite <player>` | | End a friendship; flip a favourite (favourites are listed first). |
| `/xfriend list [page]`, `requests [page]` | | Your friends, and the requests waiting for you. |
| `/xfriend status <text>`, `removestatus` | | Say what you are up to (64 characters). |
| `/xfriend toggle <invites\|notifies\|msgs\|lastseen>` | | Change a setting. |
| `/xfriend menu`, `menu page <friends\|requests> <next\|previous\|first>`, `menu select <n>` | | What the menus run; they only set what the next draw shows. |
| `/xalifriends reload` | `friends` | Reload the config and messages. `xalifriends.reload`. |

Everyone may use the friend commands (`xalifriends.friend`, default on). A player is named by their name or UUID, and found
by the last name they were seen under, so a request can go to someone who is offline.

## The database

The plugin has its own compact tables (`xalifriends_player`, `_friend`, `_request`, `_meta`; `database.table-prefix` changes
the prefix), built for size and for lookups rather than to match the original FriendSystem's:

- UUIDs are 16 bytes, not 36 characters of text, in every table and index.
- A friendship is **one row** between the lower UUID and the higher, each side keeping its own favourite, instead of two
  rows that could be left half made or half removed. A player's friends are read from either side, each with an index.
- Times are milliseconds since the epoch, in UTC. (The original kept them without a zone: UTC in SQLite, the server's zone
  in MySQL.)
- Names have an indexed lowercase column, so finding a player by name isn't a scan.
- A foreign key to `player` with `ON DELETE CASCADE` on both friendship sides and on requests.
- Left out: the original's `jump`, `showServer` and `partyInvites`, which nothing here uses.

### Moving the original's data in

On the first start that finds the original's `player`, `friend` and `request` tables, the data is copied into the new
tables and the original's tables are then **dropped**: the new ones replace the old storage, and there is no way back.
**Back the database up before the first start** (for SQLite, copy the file); that is the server owner's to do. The tables
are dropped only once everything is copied, so a copy that fails leaves them where they were.

- Same database: the original's tables are looked for next to the new ones (`migration.legacy-prefix`, empty as the original
  had none; it must differ from `database.table-prefix`). For an original SQLite file of its own, set
  `migration.legacy-sqlite-file`. For MySQL times in a zone other than the server's, set `migration.legacy-time-zone`.
- Settings carry over as they were (a "nobody" for messages stays nobody). A friendship's two rows become one; if only one
  of its rows exists, it is carried as it is. Rows that can't be carried (a UUID that isn't one, a friend or requester with
  no `player` row, someone their own friend) are counted and skipped, and the log says how many.
- It is done once: with the original's tables gone, later starts find nothing to move. It is also recorded in
  `xalifriends_meta`. Several servers sharing a database may start together: the first takes a lock row and the others wait
  for it (a lock older than ten minutes is taken over). If it fails, the plugin disables itself with the error and the next
  start tries again (rows already copied are not copied twice).
- With `migration.legacy-sqlite-file` the tables are dropped from that file, which stays behind empty.
- It runs while the plugin enables, so a very large database delays that start once. Set `migration.enabled: false` to skip it.

`Migration` in `friends/paper/test` checks it on SQLite databases (favourites on either side, one-sided friendships, rows
that can't be carried, time zones, doing it twice, a stopped lock, the original's tables dropped only after a good copy). `MysqlMigration` does
the same on a real MariaDB, plus the column types and the cascades, and runs only when
`XALI_TEST_MYSQL=jdbc:mariadb://host:port/?user=...&password=...` is set.

- `type: mariadb` uses MariaDB's driver and `mysql` MySQL's; the MySQL one can't read MariaDB's metadata.

## Friends-only messages

With XaliChat, `msgs` decides who may send a player private messages: **friends only** (the default) or **everyone**.
`/xfriend toggle msgs` or the settings menu swaps them. A database from the original may hold "nobody" (0), which is kept
until the player changes it. XaliChat asks this plugin through its `PrivateMessageGate`, for messages from the same server
and from other servers; the sender is told the player isn't accepting messages from them. Staff with
`xalichat.bypass.message.ignore` are not stopped. See [XaliChat](../chat/README.md#working-with-other-plugins).

Each online player's friends and settings are kept in memory (loaded before they join, redone after every change made on
the server, and every `cache.refresh-seconds`), because a message can't wait for a query. A change made on another server,
such as a friend removing them, reaches the cache at the next refresh. If a player's data didn't load, messages to them
are let through rather than blocked.

## The menus

With XaliMenu and PlaceholderAPI, four menus are written into `plugins/XaliMenu/menus` the first time (`friends`,
`friends-requests`, `friends-player`, `friends-settings`, and `friends-remove` to confirm a removal), and `/friend` opens
the first. They are ordinary XaliMenu files from then on: edit them there, and a `material` can be anything XaliMenu
knows, **a Nexo item (`nexo:<id>`) included**, or ItemsAdder's. `gui.friends-per-page` and `gui.requests-per-page` (21 by
default, 28 at most for the written files) say how many places the lists have; if you change the lists in a menu, change
these to match.

The menus use only what XaliMenu has: placeholders for what to show, `text` conditions on them to leave a place empty, and
its `player` action to run `/xfriend` commands. Nothing of this plugin is registered with XaliMenu, because a plugin that has
its own copy of the Kotlin runtime can't pass Kotlin-typed objects (an action's serializer) to another plugin.

### Placeholders

`%xalifriends_...%`, for the player the text is for:

| Placeholder | Meaning |
|---|---|
| `friends`, `online`, `requests` | Counts. `status` is the player's own status. |
| `page_friends`, `pages_friends`, `page_requests`, `pages_requests` | The page the menu is on, and how many there are. |
| `has_next_friends`, `has_previous_friends`, `has_next_requests`, `has_previous_requests` | `true` or `false`. |
| `setting_invites`, `setting_notifies`, `setting_msgs`, `setting_lastseen` | A setting as a word (from `words` in the language file). |
| `friend_<n>_<field>` | Place *n* (from 1) of the friends on the current page. Fields: `present`, `name`, `uuid`, `status`, `online`, `state`, `seen`, `since`, `favourite`, `marker`. |
| `request_<n>_<field>` | The same for requests: `present`, `name`, `uuid`, `date`. |
| `selected_<field>` | The friend picked in the menu, with a friend's fields. |

A place with nobody in it answers with nothing (`present` with `false`).

## Not covered

- Online status is from this server and, with XaliChat, from the proxy's player list (vanished players are not shown). A
  friend's *server* isn't known, so `showServer` and `jump` are stored but not used.
- Joining and leaving are announced to friends on the same server only.
- The menus don't use shift-click: in testing, a client's shift-clicks reached XaliMenu as a plain click, not as
  `shiftLeft`, so removing a friend asks for confirmation in a menu instead.

## The reference jars

`ref` holds the original plugin's jars and its resource packs. `ref` and
`ref` are the Spigot and Velocity jars decompiled with Vineflower, trimmed to the plugin's
own packages (`me.sk8ingduck`) and its resources. To redo it:

```sh
vineflower friends/old/FriendSystem-Spigot-4.9.8.jar   friends/old/decompiled/paper
vineflower friends/old/FriendSystem-Velocity-4.9.8.jar friends/old/decompiled/velocity
```

Where the original keeps things: friendships, requests and parties in `friendsystemvelocity/mysql`, the proxy-side
commands in `commands`, the GUIs (`gui`, `config/GuiConfig`) in the Spigot jar, and the public API and events in
`friendsystemvelocity/api`.
