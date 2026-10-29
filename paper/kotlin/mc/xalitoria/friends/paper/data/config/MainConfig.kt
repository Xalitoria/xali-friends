package mc.xalitoria.friends.paper.data.config

import com.charleskorn.kaml.YamlComment
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
    @YamlComment(
        "Each player's friends and settings are kept in memory while they are online, which is what private messages are",
        "checked against. Changes made on another server show up after this many seconds.",
    )
    val cache: Cache = Cache(),
    val database: Database = Database(),
    @YamlComment(
        "Moving the original FriendSystem's data in. It happens once, on the first start that finds its tables, and the",
        "original's tables are DROPPED afterwards: back the database up before that start.",
    )
    val migration: Migration = Migration(),
) {
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
    ) {
        fun settings(): Settings = Settings(
            invites = friendRequests,
            notifies = notifications,
            msgs = privateMessages,
            showLastSeen = showLastSeen,
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
        @YamlComment("The name of the main menu in XaliMenu. The others are friends-requests, friends-player and friends-settings.")
        val menu: String = "friends",
        @YamlComment(
            "Writes the four menus into XaliMenu's menus folder when they are missing, so they can be edited there.",
            "Their friend and request slots hold this many players per page; if a menu is changed, change these to match.",
        )
        @SerialName("write-menus")
        val writeMenus: Boolean = true,
        @SerialName("friends-per-page")
        val friendsPerPage: Int = 21,
        @SerialName("requests-per-page")
        val requestsPerPage: Int = 21,
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

    @Serializable
    data class Database(
        @YamlComment("Database types: mariadb or mysql, sqlite")
        val type: String = "sqlite",
        @YamlComment("Prepended to every table name: letters, digits and underscores. Needs a restart; a new prefix means new, empty tables.")
        @SerialName("table-prefix")
        val tablePrefix: String = "xalifriends_",
        val sqlite: Sqlite = Sqlite(),
        @YamlComment("MySQL/MariaDB connection settings")
        val mysql: Mysql = Mysql(),
    ) {
        @Serializable
        data class Sqlite(
            val filename: String = "xalifriends.db",
        )

        @Serializable
        data class Mysql(
            val host: String = "localhost",
            val port: Int = 3306,
            val database: String = "friends",
            val username: String = "root",
            val password: String = "",
            @SerialName("use-ssl")
            val useSsl: Boolean = false,
            @SerialName("pool-size")
            val poolSize: Int = 10,
        )
    }
}
