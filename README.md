# XaliFriends

**XaliFriends** is a friend system for Paper and Folia with an optional Velocity plugin. It replaces FriendSystem 4.9.8
by sk8ingDuck, moves that plugin's data into its own tables and drops the old ones. Parties are not part of it.

It provides friend requests, favorites, a status line, per-player settings, a friend menu (with
[XaliMenu](../menu/README.md)) and private messages that can be limited to friends (with
[XaliChat](../chat/README.md)). Behind Velocity, with the proxy plugin, it also tells friends on other servers that
someone came or went, shows which server a friend is on, lets a player jump to a friend and passes requests and answers
on to players on other servers (see [Behind Velocity](#behind-velocity)).

## Contents

- [Installation](#installation): [Requirements](#requirements), [Modules](#modules),
  [Building and installing](#building-and-installing), [Removal](#removal)
- [Configuration](#configuration): [Files](#files), [config.yml](#configyml)
- [Usage](#usage): [Commands](#commands), [Friends-only messages](#friends-only-messages), [The menus](#the-menus),
  [The resource pack](#the-resource-pack)
- [Behind Velocity](#behind-velocity)
- [The database](#the-database)
- [Troubleshooting](#troubleshooting)
- [Development](#development)
- [See also](#see-also)

## Installation

### Requirements

| Plugin | Needed | Purpose |
|---|---|---|
| XaliMenu | No | The friend menus. Without it, `/friend` prints its help. |
| PlaceholderAPI | No | The `%xalifriends_...%` placeholders the menus are drawn from. |
| XaliChat | No | Friends-only private messages, see [Friends-only messages](#friends-only-messages). |
| Nexo, ItemsAdder | No | Receive the [resource pack](#the-resource-pack). Loaded before XaliFriends. |

Without any of these the friend commands work.

### Modules

| Module | Description |
|---|---|
| `friends/paper` | The Paper plugin, `xali-friends-<version>-paper.jar`: commands, storage (Exposed over SQLite or MariaDB/MySQL), the cache, the placeholders, the pack installer and the hooks into XaliMenu and XaliChat. |
| `friends/velocity` | The Velocity plugin, `xali-friends-<version>-velocity.jar`. Tells every server who is where, moves a player to a friend's server and passes a request, an answer or a removal on to the server the other player is on. |
| `friends/common` | What both share: the channel name, the message encodings and the signing. No platform API; targets Java 17. |
| [`friends/resourcepack`](resourcepack/README.md) | The GUI resource packs, built as zips. Not part of any plugin jar. |

### Building and installing

```console
user $ ./gradlew :friends:paper:shadowJar :friends:velocity:shadowJar
```

Copy `build/libs/friends/xali-friends-<version>-paper.jar` into each server's `plugins` folder, and the Velocity jar into
the proxy's. `./gradlew :friends:paper:runServer` and `:friends:velocity:runProxy` start the shared dev server and proxy
with this plugin installed (see the [root README](../README.md#dev-servers)).

The descriptors come from the [Gradle plugin](../xali-gradle/README.md); the `paper { }` and `velocity { }` blocks are in
`friends/build.gradle.kts`.

> [!IMPORTANT]
> On the first start that finds the original FriendSystem's tables, the data is moved and the old tables are dropped.
> Back up the database first, see [Moving the original's data in](#moving-the-originals-data-in).

### Removal

Delete the jars. `plugins/XaliFriends/`, the menu files `plugins/XaliMenu/menus/friends*.conf` and the tables
`<prefix>player`, `<prefix>friend`, `<prefix>request` and `<prefix>meta` stay. The menus name this plugin's actions, so
delete them too or XaliMenu reports them as broken. The original FriendSystem's tables are gone after the first start,
see [Moving the original's data in](#moving-the-originals-data-in).

## Configuration

### Files

| File | Holds |
|---|---|
| `plugins/XaliFriends/config.yml` | Settings, see [below](#configyml). |
| `plugins/XaliFriends/language/<language>.yml` | Every message, written with the defaults on the first start. |
| `plugins/XaliFriends/packs/` | Marker of the installed resource pack. |
| `plugins/XaliMenu/menus/friends*.conf` | The [menus](#the-menus), written on the first start. |

`/xalifriends reload` reads the configuration and the messages again.

### config.yml

| Key | Default | Meaning |
|---|---|---|
| `debug` | `false` | Verbose logging. |
| `language`, `date-format` | `en-us`, `yyyy-MM-dd HH:mm:ss` | Language file, and how dates such as "friends since" and "last seen" are shown. |
| `friends.limit` | off | A limit per group. A player gets the highest limit among the groups they hold the permission `xalifriends.group.<group>` for, and always the default group's. `enabled`, `default-group` and `groups` (group to limit) set it. |
| `friends.request-expiry` | `0` | Minutes a request stays open; `0` keeps it until it is answered. |
| `friends.per-page` | `5` | Lines per page of `/friend list` and `/friend requests`. |
| `defaults` | all on, messages from friends only | What a new player starts with: `friend-requests`, `notifications`, `private-messages` (`ENABLED`, `FRIENDS_ONLY` or `DISABLED`), `show-last-seen`, `jump`, `show-server`. |
| `notifications` | `1`, `1` | `online-friends-minimum` and `requests-minimum`: how many friends online, or requests waiting, a joining player needs before being told. |
| `gui` | on | `enabled`, `menu` (name of the main menu), `write-menus`, `friends-per-page` and `requests-per-page`, see [The menus](#the-menus). |
| `packs` | none | The [resource pack](#the-resource-pack) installer. |
| `cache.refresh-seconds` | `30` | How often each online player's friends are read again. |
| `database` | SQLite | `type` (`sqlite`, `mysql` or `mariadb`), `table-prefix` and the connection settings. `mariadb` uses MariaDB's driver and `mysql` MySQL's; the MySQL driver cannot read MariaDB 11's metadata. |
| `proxy` | on | `enabled`, `jump` and `server-names` (how a server is called to players), see [Behind Velocity](#behind-velocity). |
| `migration` | on | The one-time import, see [Moving the original's data in](#moving-the-originals-data-in). |

## Usage

### Commands

| Command | Aliases | Description |
|---|---|---|
| `/xfriend` | `friend`, `f` | Open the friend menu (the help, when there is no menu). |
| `/xfriend add <player>` | | Ask a player to be friends. They need friend requests switched on. |
| `/xfriend accept <player\|all>`, `deny <player\|all>` | | Answer a request. |
| `/xfriend remove <player>`, `favorite <player>` | | End a friendship; flip a favorite (favorites are listed first). |
| `/xfriend list [page]`, `requests [page]` | | The friends, and the requests waiting. |
| `/xfriend status <text>`, `removestatus` | | Say what the player is up to (64 characters). |
| `/xfriend jump <friend>` | | Go to the server a friend is on (behind Velocity). |
| `/xfriend toggle <invites\|notifies\|msgs\|lastseen\|jump\|server>` | | Change a setting. |
| `/xfriend menu`, `menu page <friends\|requests> <next\|previous\|first>`, `menu select <n>` | | What menus written by an earlier version run. They only set what the next draw shows. |
| `/xalifriends reload` | `friends` | Reload the config and messages. Permission `xalifriends.reload`. |
| `/xalifriends pack update` | | Install the menu resource pack into Nexo or ItemsAdder again, see [The resource pack](#the-resource-pack). Permission `xalifriends.pack`. |

Everyone may use the friend commands (`xalifriends.friend`, default on; `xalifriends.jump`, default on, for `jump`). A
player is named by name or UUID and found by the last name they were seen under, so a request can go to someone who is
offline.

### Friends-only messages

With XaliChat, `msgs` decides who may send a player private messages: **friends only** (the default) or **everyone**.
`/xfriend toggle msgs` or the settings menu swaps them. A database from the original may hold "nobody" (0), which is kept
until the player changes it. XaliChat asks this plugin through its `PrivateMessageGate`, for messages from the same
server and from other servers. The sender is told the player is not accepting messages from them. Staff with
`xalichat.bypass.message.ignore` are not stopped. See
[XaliChat](../chat/README.md#working-with-other-plugins).

Each online player's friends and settings are kept in memory (loaded before they join, redone after every change made on
the server and every `cache.refresh-seconds`), because a message cannot wait for a query. A change made on another
server, such as a friend removing the player, reaches the cache at the next refresh. If a player's data did not load,
messages to them are let through rather than blocked.

### The menus

With XaliMenu and PlaceholderAPI, five menus are written into `plugins/XaliMenu/menus` the first time: `friends`,
`friends-requests`, `friends-player`, `friends-settings` and `friends-remove` (confirms a removal). `/friend` opens the
first. They are ordinary XaliMenu files from then on: edit them there. A `material` can be anything XaliMenu knows, **a
Nexo item (`nexo:<id>`) included**, or ItemsAdder's. `gui.friends-per-page` and `gui.requests-per-page` (21 by default,
28 at most for the written files) say how many places the lists have; when the lists in a menu change, change these to
match.

The menus draw from placeholders, leave a place empty with XaliMenu's `text` conditions on them, and do things with this
plugin's own actions, registered with XaliMenu when it is installed. Each does what the `/xfriend` command of the same name
does, in the background, and then draws the menu again, so a click needs no command and no wait.

| Action | Keys |
|---|---|
| `xalifriends:accept`, `deny` | `player`: a UUID, a name or `all`. Placeholders are filled in. |
| `xalifriends:remove`, `favorite`, `jump` | `player` |
| `xalifriends:toggle` | `setting`: `invites`, `notifies`, `msgs`, `lastseen`, `jump` or `server`. |
| `xalifriends:clear-status` | |
| `xalifriends:select` | `place`: the friend at that place of the list becomes the one `friends-player` is about. |
| `xalifriends:page` | `list`: `friends` or `requests`; `move`: `next` (the default), `previous` or `first`. |

`after` says what happens once the work is done: `refresh` (the default; `close` for `jump`), `close`, `none` or
`open:<menu>`. `select` and `page` only set what the next draw shows, so they run at once. A wrong key makes XaliMenu report
the menu as broken. The action needs the viewer to have `xalifriends.friend`, like the commands. Menus written by an earlier
version, which run `/xfriend` through the `player` action, keep working; they are only written when missing, so delete a
file to get the new one.

```
click.left.actions = [{ type = "xalifriends:accept", player = "%xalifriends_request_1_uuid%" }]
click.any.actions = [{ type = "xalifriends:remove", player = "%xalifriends_selected_uuid%", after = "open:friends" }]
```

#### Placeholders

`%xalifriends_...%`, for the player the text is for. `friends`, `online`, `requests`, `status` and the `setting_` ones also work for a
player who is not online here (another player's profile, a player on another server): the first request reads them from the
database in the background and answers with nothing until that is done, and after that they come from memory, are read again
when 30 seconds old while they keep being asked for, and are dropped when nobody has asked for ten minutes. The ones about the
menu (`page_`, `pages_`, `has_`, `friend_`, `request_` and `selected_`) are empty for a player who has no menu open on this server.

| Placeholder | Meaning |
|---|---|
| `friends`, `online`, `requests` | Counts. `status` is the player's own status. |
| `page_friends`, `pages_friends`, `page_requests`, `pages_requests` | The page the menu is on, and how many there are. |
| `has_next_friends`, `has_previous_friends`, `has_next_requests`, `has_previous_requests` | `true` or `false`. |
| `setting_invites`, `setting_notifies`, `setting_msgs`, `setting_lastseen`, `setting_jump`, `setting_server` | A setting as a word (from `words` in the language file). |
| `friend_<n>_<field>` | Place *n* (from 1) of the friends on the current page. Fields: `present`, `name`, `uuid`, `status`, `online`, `state`, `seen` (online, and on which server when that is known and shown), `server` (empty when unknown or hidden), `jumpable` (`true` when the viewer may jump to them now), `since`, `favorite`, `marker`. |
| `request_<n>_<field>` | The same for requests: `present`, `name`, `uuid`, `date`. |
| `selected_<field>` | The friend picked in the menu, with a friend's fields. |

A place with nobody in it answers with nothing (`present` with `false`).

### The resource pack

The glyphs that draw the menu backgrounds and the button icons are the packs of [resourcepack](resourcepack/README.md),
built as zips and not bundled in the plugin. `packs` in `config.yml` has the plugin fetch them for the plugin that serves
the resource pack. Nothing is downloaded unless a handler is set.

**File** `config.yml`

```yaml
packs:
  handler: none       # none (the default), nexo or itemsadder
  url: https://github.com/Xalitoria/xali-friends-resourcepack/releases/latest/download
  install: [friends-gui]
```

On start (after Nexo or ItemsAdder has enabled) and on `/xalifriends pack update`, the plugin fetches
`<url>/friends-gui-nexo.zip.sha256` and, unless that is the checksum it installed last time,
`<url>/friends-gui-nexo.zip`. It verifies the zip and unpacks it into `plugins/Nexo` (`plugins/ItemsAdder` for
`itemsadder`), off the main thread. A name that would leave that folder, a zip that does not match its checksum, a `url`
that is not `https://` and a missing handler plugin are refused with a message. A failed download changes nothing.
Reload the handler afterwards (`/nexo reload`, `/iazip`).

> [!NOTE]
> The default `url` is the repository's release. The repository is private, so a server that is not logged in to it gets
> a 404. Point `url` at wherever the zips are published.

## Behind Velocity

Put `xali-friends-<version>-velocity.jar` on the proxy and the Paper plugin on each server. The two talk over the
`xalifriends:main` plugin channel, signed with Velocity's forwarding secret (the one XaliChat uses). The servers read it
from `config/paper-global.yml` (`proxies.velocity`), so modern forwarding has to be on. Without a secret, or with
`proxy.enabled: false`, each server works alone. The proxy plugin has no configuration of its own. It trusts the servers
to have checked that two players are friends.

- **Who is where.** The proxy tells a server where every player is when someone arrives on it, and tells all servers
  when a player comes onto the network, changes server or leaves. Friends are told once for the network ("Alice
  joined."), not for each server a player passes through, and the "n friends online" summary is for coming onto the
  network only.
- **Servers.** Lists and menus say which server an online friend is on (`Online on survival`), unless the friend turned
  `server` off. `proxy.server-names` renames servers for players.
- **Jump.** `/friend jump <friend>` asks the proxy to send the player to their friend's server. The server checks that
  they are friends, that the friend allows it (`jump` setting) and that `proxy.jump` is on. The proxy checks that the
  player is on the server that asked. The friends menu has a jump button for those who can.
- **Across servers.** A request, an acceptance, a refusal or a removal reaches a player on another server at once and
  reloads their friends there, instead of waiting for the cache refresh.
- **Vanish.** The proxy does not know of vanished players (PremiumVanish and the like), so a vanished friend is
  announced and shown as online. Friends-only private messages are not affected.

### Limits

- Without the proxy plugin, online status is from this server and, with XaliChat, from the proxy's player list. The
  friend's server is not known, and joining and leaving are announced to friends on the same server only.
- The menus do not use shift-click: in testing, a client's shift-clicks reached XaliMenu as a plain click, not as
  `shiftLeft`, so removing a friend asks for confirmation in a menu instead.

## The database

The plugin has its own compact tables (`xalifriends_player`, `_friend`, `_request` and `_meta`; `database.table-prefix`
changes the prefix), built for size and for lookups rather than to match the original FriendSystem's:

- UUIDs are 16 bytes, not 36 characters of text, in every table and index.
- A friendship is **one row** between the lower UUID and the higher, each side keeping its own favorite, instead of two
  rows that could be left half made or half removed. A player's friends are read from either side, each with an index.
- Times are milliseconds since the epoch, in UTC. The original kept them without a zone: UTC in SQLite, the server's zone
  in MySQL.
- Names have an indexed lowercase column, so finding a player by name is not a scan.
- A foreign key to `player` with `ON DELETE CASCADE` on both friendship sides and on requests.
- Left out: the original's `partyInvites`, since there are no parties. Its `jump` and `showServer` are kept (`jump`,
  `show_server`); a database made before they were added gains them, switched on.

### Moving the original's data in

On the first start that finds the original's `player`, `friend` and `request` tables, the data is copied into the new
tables and the original's tables are then **dropped**: the new ones replace the old storage, and there is no way back.

> [!WARNING]
> Back the database up before the first start (for SQLite, copy the file). That is the server owner's to do. The tables
> are dropped only once everything is copied, so a copy that fails leaves them where they were.

- Same database: the original's tables are looked for next to the new ones (`migration.legacy-prefix`, empty as the
  original had none; it must differ from `database.table-prefix`). For an original SQLite file of its own, set
  `migration.legacy-sqlite-file`. For MySQL times in a zone other than the server's, set `migration.legacy-time-zone`.
- Settings carry over as they were (a "nobody" for messages stays nobody). A friendship's two rows become one; if only
  one of its rows exists, it is carried as it is. Rows that cannot be carried (a UUID that is not one, a friend or
  requester with no `player` row, someone their own friend) are counted and skipped, and the log says how many.
- It is done once: with the original's tables gone, later starts find nothing to move. It is also recorded in
  `xalifriends_meta`. Several servers sharing a database may start together: the first takes a lock row and the others
  wait for it (a lock older than ten minutes is taken over). If it fails, the plugin disables itself with the error and
  the next start tries again (rows already copied are not copied twice).
- With `migration.legacy-sqlite-file`, the tables are dropped from that file, which stays behind empty.
- It runs while the plugin enables, so a very large database delays that start once. Set `migration.enabled: false` to
  skip it.

## Troubleshooting

### `/friend` prints only help

XaliMenu or PlaceholderAPI is missing, or the menu named by `gui.menu` has not been read yet. Install both, check that
`plugins/XaliMenu/menus/friends.conf` exists (`gui.write-menus`) and run `/xmenu reload`.

### Friends on other servers do not show up

The proxy plugin is missing or the forwarding secret could not be read. Check `proxies.velocity.enabled` and `secret` in
`config/paper-global.yml` and see [Behind Velocity](#behind-velocity). Without it, status comes from this server only.

### The plugin disables itself on start

The migration failed or the database cannot be reached. The log names the error. Nothing was dropped when a copy failed,
and the next start tries again.

### The pack download gives a 404

The default `packs.url` points at a private repository. Point it at where the zips are published.

## Development

Sources are under `friends/<submodule>/kotlin`, tests under `friends/<submodule>/test` (see the
[root README](../README.md#source-layout)).

### Source layout

| Where | Holds |
|---|---|
| `friends/common` (`com.xalitoria.friends.protocol`) | `Message.kt` and `Protocol.kt`: the channel name, the message encodings and the signing. No platform API; targets Java 17. |
| `friends/velocity` | `XaliFriends.kt`: tracks who is where and relays requests, answers and jumps. No configuration. |
| `friends/paper` `XaliFriends.kt` | The composition root: wires everything in `onEnable`. |
| `command`, `modal/command/XCommand.kt` | `FriendCommand` (`/xfriend`), `XaliFriendsCommand` (`/xalifriends`), and the base that registers aliases. |
| `service` | `FriendService` (what the commands and menu actions do, off the main thread), `FriendCache` (each online player's friends and settings, in memory), `Listings` (the order and pages), `Presentation` (dates, who is online, words for a setting) and `MessageRule` (who a player takes private messages from; the gate XaliChat asks). |
| `data/db` | The tables, the repositories (`Pairing.kt` orders the two UUIDs of a friendship) and `legacy/`, the one-time import of the original's tables. |
| `external` | Everything that names another plugin: `MenuHook` and `FriendActions` (XaliMenu's actions), `DefaultMenus` (the written menu files), `ChatHook` (XaliChat's gate) and `FriendsExpansion` (PlaceholderAPI). |
| `proxy/ProxyLink.kt` | The server end of the proxy channel. |
| `pack/PackInstaller.kt` | The resource pack installer for Nexo and ItemsAdder. |

### Testing

| Test | Covers |
|---|---|
| `friends/common` `Protocol` | The proxy messages and their signing. |
| `friends/paper` `FriendRepository`, `MessageRule`, `Upgrade` | Storage, the friends-only message rule and columns added to older databases. |
| `friends/paper` `OfflineReads` | What the placeholders keep of a player who is not online: empty until the first read, a player with no row, a read again when it is old or invalid, a read that fails, and a player who is forgotten. |
| `friends/paper` `MenuActions` | The menu actions' `after` forms, the keys checked when a menu is read, and the written menus clicking through registered actions. |
| `friends/paper` `Migration` | The import on SQLite: favorites on either side, one-sided friendships, rows that cannot be carried, time zones, doing it twice, a stopped lock, and the original's tables dropped only after a good copy. |
| `friends/paper` `MysqlMigration` | The same on MariaDB, plus the column types and the cascades. Runs only when `XALI_TEST_MYSQL` is set. |
| `friends/paper` `PackInstall` | The pack installer: checksum files, unpacking into the handler's folder, refused names and mismatches, and the downloader's redirects and size limit. |
| `friends/resourcepack` `Packs` | The packs, see the [pack README](resourcepack/README.md). |

```console
user $ docker run -d --rm --name xali-mariadb-test -p 3307:3306 -e MARIADB_ROOT_PASSWORD=pw mariadb:11
user $ XALI_TEST_MYSQL='jdbc:mariadb://127.0.0.1:3307/?user=root&password=pw' ./gradlew :friends:paper:test --rerun-tasks
```

The relay was tested with two Folia servers behind Velocity and mineflayer bots: arrivals, leaving, switching server,
jumping (allowed, refused, already there), and requests, acceptances, refusals and removals between servers.

**Adding a menu action**

1. Add the action to `external/FriendActions.kt` and register it in `MenuHook`. It does what the `/xfriend` command of the
   same name does, in the background, then draws again (`after`).
2. Check the viewer has `xalifriends.friend`, like the commands.
3. Use it in `DefaultMenus` if a written menu needs it, and add a case to `MenuActions`. Menus are written only when missing,
   so a changed default reaches existing servers only when the file is deleted.
4. Add a row to the table under [The menus](#the-menus).

**Adding a column or table**

1. Add it to the table object, and make an older database gain it: `Upgrade` tests a column added to an existing table.
2. Keep UUIDs as 16 bytes and times as epoch milliseconds in UTC, like the rest, see [The database](#the-database).

### The reference jars

`friends/ref` holds the original plugin's jars, their decompiled sources and its resource packs. The folder is ignored
by git, so a fresh clone does not have it. The sources are the Spigot and Velocity jars decompiled with Vineflower,
trimmed to the plugin's own packages (`me.sk8ingduck`) and its resources. To redo them:

```console
user $ vineflower friends/ref/FriendSystem-Spigot-4.9.8.jar   friends/ref/decompiled/paper
user $ vineflower friends/ref/FriendSystem-Velocity-4.9.8.jar friends/ref/decompiled/velocity
```

Where the original keeps things: friendships, requests and parties in `friendsystemvelocity/mysql`, the proxy-side
commands in `commands`, the GUIs (`gui`, `config/GuiConfig`) in the Spigot jar, and the public API and events in
`friendsystemvelocity/api`.

## See also

- [XaliChat](../chat/README.md) — the gate API that limits private messages to friends
- [XaliMenu](../menu/README.md) — the menus and their addon API
- [Friend GUI packs](resourcepack/README.md) — the resource packs
- [Gradle plugin](../xali-gradle/README.md) — descriptors and library loading
- [Root README](../README.md) — the workspace

## External resources

- [PlaceholderAPI](https://wiki.placeholderapi.com/) — the placeholders
