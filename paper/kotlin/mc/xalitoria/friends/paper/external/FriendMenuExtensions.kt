package mc.xalitoria.friends.paper.external

import com.xalitoria.util.message.PLAIN
import com.xalitoria.util.message.toComponent
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.data.db.repository.SettingKey
import mc.xalitoria.friends.paper.data.model.SettingState
import net.xalitoria.menu.Catalog
import net.xalitoria.menu.CatalogType
import net.xalitoria.menu.Condition
import net.xalitoria.menu.ConditionType
import net.xalitoria.menu.MenuContext
import net.xalitoria.menu.XaliMenuApi

/** Validated settings and names of the XaliMenu catalogs and conditions [FriendMenuExtensions] adds. */
object FriendMenuKinds {
    const val FRIENDS_CATALOG = "xalifriends:friends"
    const val REQUESTS_CATALOG = "xalifriends:requests"
    const val SETTING_CONDITION = "xalifriends:setting"
    const val HAS_REQUESTS_CONDITION = "xalifriends:has-requests"
    const val SELECTED_CONDITION = "xalifriends:selected"

    val FRIEND_FILTERS = setOf("all", "online", "offline", "favorites")
    val SELECTED_CHECKS = setOf("present", "favorite", "jumpable", "online")

    fun filter(text: String): String = text.trim().lowercase().also {
        require(it in FRIEND_FILTERS) { "'filter' is '$text'; it is all, online, offline or favorites" }
    }

    fun repeat(count: Int): Int = count.also {
        require(it in 1..9) { "'repeat' is $count; it is from 1 to 9" }
    }

    fun settingValue(key: SettingKey, text: String): String {
        val value = text.trim().lowercase()
        val allowed = if (key == SettingKey.MSGS) setOf("everyone", "friends", "nobody") else setOf("on", "off")
        require(value in allowed) { "'value' for '${key.key}' is '$text'; it is ${allowed.joinToString(" or ")}" }
        return value
    }

    fun selectedCheck(text: String): String = text.trim().lowercase().also {
        require(it in SELECTED_CHECKS) { "'check' is '$text'; it is ${SELECTED_CHECKS.joinToString()}" }
    }

    /** Strips formatting codes so catalog values, which XaliMenu shows verbatim, contain plain text only. */
    fun plain(text: String): String = PLAIN.serialize(text.toComponent())
}

/**
 * XaliMenu catalogs and conditions for friend menus.
 *
 * Catalogs:
 * - `xalifriends:friends` (optional `filter`: `all`, `online`, `offline`, `favorites`)
 * - `xalifriends:requests` (optional `repeat`: 1..9, repeating each request so a `pages.matrix` row can hold head + buttons)
 *
 * Conditions:
 * - `xalifriends:setting` (`setting`, `value`)
 * - `xalifriends:has-requests`
 * - `xalifriends:selected` (`check`: `present`, `favorite`, `jumpable`, `online`)
 */
class FriendMenuExtensions(private val plugin: XaliFriends, private val api: XaliMenuApi) {
    fun register() {
        api.registerCatalog(plugin, FriendMenuKinds.FRIENDS_CATALOG, FRIENDS_VALUES, mapOf("filter" to "all"), CatalogType { settings ->
            FriendsCatalog(plugin, FriendMenuKinds.filter(settings.string("filter", "all")))
        })
        api.registerCatalog(plugin, FriendMenuKinds.REQUESTS_CATALOG, REQUESTS_VALUES, mapOf("repeat" to "1"), CatalogType { settings ->
            RequestsCatalog(plugin, FriendMenuKinds.repeat(settings.int("repeat", 1)))
        })
        api.registerCondition(plugin, FriendMenuKinds.SETTING_CONDITION, ConditionType { settings ->
            val key = FriendActionKinds.setting(settings.requireString("setting"))
            val default = if (key == SettingKey.MSGS) "friends" else "on"
            val expected = FriendMenuKinds.settingValue(key, settings.string("value", default))
            SettingCondition(plugin, key, expected)
        })
        api.registerCondition(plugin, FriendMenuKinds.HAS_REQUESTS_CONDITION, ConditionType {
            object : Condition {
                override fun test(ctx: MenuContext): Boolean = plugin.cache.get(ctx.player.uniqueId)?.requests?.isNotEmpty() == true
            }
        })
        api.registerCondition(plugin, FriendMenuKinds.SELECTED_CONDITION, ConditionType { settings ->
            SelectedCondition(plugin, FriendMenuKinds.selectedCheck(settings.requireString("check")))
        })
    }

    private class FriendsCatalog(private val plugin: XaliFriends, private val filter: String) : Catalog {
        override fun elements(ctx: MenuContext): List<Map<String, String>> {
            val viewer = ctx.player
            val snapshot = plugin.cache.get(viewer.uniqueId) ?: return []
            val view = plugin.presentation
            return plugin.listings.friends(viewer, snapshot)
                .filter { entry ->
                    when (filter) {
                        "online" -> view.isOnline(viewer, entry.profile)
                        "offline" -> !view.isOnline(viewer, entry.profile)
                        "favorites" -> entry.favorite
                        else -> true
                    }
                }
                .map { entry ->
                    val friend = entry.profile
                    val online = view.isOnline(viewer, friend)
                    val seen = if (online) view.online(friend) else view.lastSeen(friend)
                    mapOf(
                        "name" to friend.name,
                        "uuid" to friend.uuid.toString(),
                        "status" to FriendMenuKinds.plain(view.status(friend)),
                        "online" to online.toString(),
                        "state" to FriendMenuKinds.plain(view.onlineWord(online)),
                        "seen" to FriendMenuKinds.plain(seen),
                        "server" to view.server(friend),
                        "jumpable" to plugin.service.canJump(viewer, friend).toString(),
                        "since" to view.date(entry.since),
                        "favorite" to entry.favorite.toString(),
                        "star" to if (entry.favorite) "★ " else "",
                    )
                }
        }
    }

    private class RequestsCatalog(private val plugin: XaliFriends, private val repeat: Int) : Catalog {
        override fun elements(ctx: MenuContext): List<Map<String, String>> {
            val snapshot = plugin.cache.get(ctx.player.uniqueId) ?: return []
            val view = plugin.presentation
            val requests = plugin.listings.requests(snapshot)
            if (requests.isEmpty()) return []
            return buildList(requests.size * repeat) {
                for (entry in requests) {
                    val element = mapOf(
                        "name" to entry.profile.name,
                        "uuid" to entry.profile.uuid.toString(),
                        "date" to view.date(entry.date),
                    )
                    repeat(repeat) { add(element) }
                }
            }
        }
    }

    private class SettingCondition(private val plugin: XaliFriends, private val key: SettingKey, private val expected: String) : Condition {
        override fun test(ctx: MenuContext): Boolean {
            val settings = plugin.cache.get(ctx.player.uniqueId)?.settings ?: return false
            val current = when (key) {
                SettingKey.INVITES -> if (settings.invites) "on" else "off"
                SettingKey.NOTIFIES -> if (settings.notifies) "on" else "off"
                SettingKey.MSGS -> when (settings.msgs) {
                    SettingState.ENABLED -> "everyone"
                    SettingState.FRIENDS_ONLY -> "friends"
                    SettingState.DISABLED -> "nobody"
                }
                SettingKey.LAST_SEEN -> if (settings.showLastSeen) "on" else "off"
                SettingKey.JUMP -> if (settings.jump) "on" else "off"
                SettingKey.SERVER -> if (settings.showServer) "on" else "off"
            }
            return current == expected
        }
    }

    private class SelectedCondition(private val plugin: XaliFriends, private val check: String) : Condition {
        override fun test(ctx: MenuContext): Boolean {
            val viewer = ctx.player
            val entry = plugin.listings.selected(viewer) ?: return false
            return when (check) {
                "present" -> true
                "favorite" -> entry.favorite
                "jumpable" -> plugin.service.canJump(viewer, entry.profile)
                "online" -> plugin.presentation.isOnline(viewer, entry.profile)
                else -> false
            }
        }
    }

    private companion object {
        val FRIENDS_VALUES = mapOf(
            "name" to "Notch",
            "uuid" to "069a79f4-44e9-4726-a5be-fca90e38aaf5",
            "status" to "Mining",
            "online" to "true",
            "state" to "Online",
            "seen" to "Online on Survival",
            "server" to "Survival",
            "jumpable" to "true",
            "since" to "2026-11-01 12:00:00",
            "favorite" to "true",
            "star" to "★ ",
        )
        val REQUESTS_VALUES = mapOf(
            "name" to "Alex",
            "uuid" to "ec561538-f3fd-461d-aff5-086b22154bce",
            "date" to "2026-11-20 18:30:00",
        )
    }
}
