package mc.xalitoria.friends.paper.data.config

import com.charleskorn.kaml.YamlComment
import com.xalitoria.util.config.DatabaseConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mc.xalitoria.friends.paper.data.model.SettingState
import mc.xalitoria.friends.paper.data.model.Settings

@Serializable
data class MainConfig(
    val debug: Boolean = false,
    val language: String = "en-us",
    @YamlComment("How dates are shown, such as 'friends since' and 'last seen'.")
    @SerialName("date-format")
    val dateFormat: String = "yyyy-MM-dd HH:mm:ss",
    val friends: Friends = Friends(),
    @YamlComment("What a player starts with, the first time they join. They change it with /friend toggle or in the settings menu.")
    val defaults: Defaults = Defaults(),
    val notifications: Notifications = Notifications(),
    @YamlComment("The friend menus are XaliMenu menus; they need XaliMenu and PlaceholderAPI.")
    val gui: Gui = Gui(),
    @YamlComment("The glyphs and button icons of the friend menus, for the plugin that serves your resource pack. Nothing is downloaded unless a handler is set.")
    val packs: Packs = Packs(),
    @YamlComment(
        "Each player's friends and settings are kept in memory while they are online, which is what private messages are",
        "checked against. Changes made on another server show up after this many seconds.",
    )
    val cache: Cache = Cache(),
    val database: DatabaseConfig = DatabaseConfig(
        tablePrefix = "xalifriends_",
        sqlite = DatabaseConfig.Sqlite("xalifriends.db"),
        mysql = DatabaseConfig.Mysql(database = "friends"),
    ),
    @YamlComment(
        "The proxy plugin (XaliFriends on Velocity) tells the servers where players are. It needs Velocity's modern",
        "forwarding: the secret is read from Paper's config/paper-global.yml. Without it everything works for this server alone.",
    )
    val proxy: Proxy = Proxy(),
    @YamlComment(
        "Moving the original FriendSystem's data in. It happens once, on the first start that finds its tables, and the",
        "original's tables are DROPPED afterwards: back the database up before that start.",
    )
    val migration: Migration = Migration(),
) {
    /** How the written menus look: [Gui.style], with `auto` settled by the pack installer's handler. */
    fun menuStyle(): Gui.Style = when (gui.style) {
        Gui.Style.AUTO -> if (packs.handler == Packs.Handler.NEXO) Gui.Style.NEXO else Gui.Style.VANILLA
        else -> gui.style
    }

    /** Players per page of the friend list for legacy placeholder-based menus (catalog menus size themselves from their layout). */
    fun friendsPerPage(): Int = gui.friendsPerPage.takeIf { it > 0 } ?: 21

    /** Players per page of the request list for legacy placeholder-based menus (catalog menus size themselves from their layout). */
    fun requestsPerPage(): Int = gui.requestsPerPage.takeIf { it > 0 } ?: 21

    @Serializable
    data class Friends(
        @YamlComment(
            "A limit per group. A player gets the highest limit among the groups they have the permission",
            "xalifriends.group.<group> for, and always the default group's. false for no limit.",
        )
        val limit: Limit = Limit(),
        @YamlComment("Minutes a friend request stays open; 0 keeps it until it is answered.")
        @SerialName("request-expiry")
        val requestExpiry: Int = 0,
        @YamlComment("Lines per page of /friend list and /friend requests.")
        @SerialName("per-page")
        val perPage: Int = 5,
    ) {
        @Serializable
        data class Limit(
            val enabled: Boolean = false,
            @SerialName("default-group")
            val defaultGroup: String = "default",
            val groups: Map<String, Int> = mapOf("default" to 5, "premium" to 25, "premium_plus" to 50, "youtuber" to 100, "moderator" to 500),
        )
    }

    @Serializable
    data class Defaults(
        @SerialName("friend-requests")
        val friendRequests: Boolean = true,
        val notifications: Boolean = true,
        @YamlComment("ENABLED (everyone), FRIENDS_ONLY or DISABLED (nobody).")
        @SerialName("private-messages")
        val privateMessages: SettingState = SettingState.FRIENDS_ONLY,
        @SerialName("show-last-seen")
        val showLastSeen: Boolean = true,
        @YamlComment("Whether friends may jump to a player's server (with the proxy plugin).")
        val jump: Boolean = true,
        @SerialName("show-server")
        @YamlComment("Whether friends are told which server a player is on (with the proxy plugin).")
        val showServer: Boolean = true,
    ) {
        fun settings(): Settings = Settings(
            invites = friendRequests,
            notifies = notifications,
            msgs = privateMessages,
            showLastSeen = showLastSeen,
            jump = jump,
            showServer = showServer,
        )
    }

    @Serializable
    data class Notifications(
        @YamlComment("Tell a player who joins how many friends are online, once there are at least this many.")
        @SerialName("online-friends-minimum")
        val onlineFriendsMinimum: Int = 1,
        @YamlComment("The same for requests waiting for them.")
        @SerialName("requests-minimum")
        val requestsMinimum: Int = 1,
    )

    @Serializable
    data class Gui(
        val enabled: Boolean = true,
        @YamlComment("The name of the main menu in XaliMenu. The others are friends-requests, friends-player, friends-remove and friends-settings (and friends-settings2 in the nexo style).")
        val menu: String = "friends",
        @YamlComment(
            "How the written menus look: vanilla (plain items), nexo (the friend GUI pack's backgrounds and buttons, which needs the",
            "pack installed in Nexo) or auto, which is nexo when packs.handler is nexo. Only read when the menus are written.",
        )
        val style: Style = Style.AUTO,
        @YamlComment(
            "Writes the menus into XaliMenu's menus folder when they are missing, so they can be edited there.",
            "Run /xalifriends menus reset to overwrite existing files with the current style.",
        )
        @SerialName("write-menus")
        val writeMenus: Boolean = true,
        @YamlComment("Used only by legacy placeholder-based menus (%xalifriends_friend_<n>_...%); paged catalog menus follow their own layout.")
        @SerialName("friends-per-page")
        val friendsPerPage: Int = 21,
        @SerialName("requests-per-page")
        val requestsPerPage: Int = 21,
    ) {
        @Serializable
        enum class Style {
            @SerialName("auto") AUTO,
            @SerialName("vanilla") VANILLA,
            @SerialName("nexo") NEXO,
        }
    }

    @Serializable
    data class Packs(
        @YamlComment(
            "The plugin the packs are installed into: none, nexo or itemsadder. With none nothing is downloaded. Reload that plugin",
            "(/nexo reload, /iazip) after the first install and after each update.",
        )
        val handler: Handler = Handler.NONE,
        @YamlComment(
            "Where the zipped packs are downloaded from (https), for example the assets of a GitHub release. Each pack is fetched as",
            "<url>/<pack>-<handler>.zip and checked against <url>/<pack>-<handler>.zip.sha256, and only fetched again when that changes.",
        )
        val url: String = "https://github.com/Xalitoria/xali-friends-resourcepack/releases/latest/download",
        @YamlComment("The packs to install.")
        val install: List<String> = ["friends-gui"],
    ) {
        /** [folder] is the plugin folder under plugins/ that the pack is unpacked into. */
        @Serializable
        enum class Handler(val id: String, val folder: String?) {
            @SerialName("none") NONE("none", null),
            @SerialName("nexo") NEXO("nexo", "Nexo"),
            @SerialName("itemsadder") ITEMSADDER("itemsadder", "ItemsAdder"),
        }
    }

    @Serializable
    data class Proxy(
        val enabled: Boolean = true,
        @YamlComment("Whether /friend jump is on: a friend may be followed to their server.")
        val jump: Boolean = true,
        @YamlComment("How a server is called to players, by the name it has on the proxy: survival: \"Survival\". Others show as they are.")
        @SerialName("server-names")
        val serverNames: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class Cache(
        @SerialName("refresh-seconds")
        val refreshSeconds: Int = 30,
    )

    @Serializable
    data class Migration(
        val enabled: Boolean = true,
        @YamlComment(
            "The prefix of the original's tables (player, friend, request), in this database. It must differ from",
            "database.table-prefix. These tables are dropped once their data is copied.",
        )
        @SerialName("legacy-prefix")
        val legacyPrefix: String = "",
        @YamlComment(
            "The original's SQLite file, when it isn't this plugin's own database (a path from this plugin's folder, or an",
            "absolute one). Leave empty when the original's tables are in the database above.",
        )
        @SerialName("legacy-sqlite-file")
        val legacySqliteFile: String = "",
        @YamlComment(
            "The zone the original's MySQL DATETIMEs are in (it kept them without one), such as Europe/Berlin; empty for the",
            "server's. An SQLite file is always UTC.",
        )
        @SerialName("legacy-time-zone")
        val legacyTimeZone: String = "",
        @YamlComment("Rows written to the database at a time.")
        @SerialName("batch-size")
        val batchSize: Int = 1000,
    )
}
