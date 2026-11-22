package mc.xalitoria.friends.paper.service

import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.data.model.FriendEntry
import mc.xalitoria.friends.paper.data.model.RequestEntry
import mc.xalitoria.friends.paper.data.model.Snapshot
import org.bukkit.entity.Player
import java.util.UUID

/** The friends and requests in the order the menus and `/friend list` show them, and the page a player is on. */
class Listings(private val plugin: XaliFriends) {
    /** Favorites first, then those online, then by name. */
    fun friends(viewer: Player?, snapshot: Snapshot): List<FriendEntry> =
        snapshot.friends.sortedWith(
            compareByDescending<FriendEntry> { it.favorite }
                .thenByDescending { plugin.presentation.isOnline(viewer, it.profile) }
                .thenBy { it.profile.name.lowercase() }
        )

    fun requests(snapshot: Snapshot): List<RequestEntry> = snapshot.requests

    fun pages(size: Int, perPage: Int): Int = ((size + perPage - 1) / perPage).coerceAtLeast(1)

    /** [page] (counted from 0) kept inside the pages there are. */
    fun clamp(page: Int, size: Int, perPage: Int): Int = page.coerceIn(0, pages(size, perPage) - 1)

    /** Turns [uuid]'s friends or requests page: [move] is `next`, `previous` or anything else for the first page. */
    fun turnPage(uuid: UUID, friends: Boolean, move: String) {
        val snapshot = plugin.cache.get(uuid) ?: return
        val state = plugin.cache.state(uuid)
        val size = if (friends) snapshot.friends.size else snapshot.requests.size
        val perPage = if (friends) friendsPerPage() else requestsPerPage()
        val current = clamp(if (friends) state.friendsPage else state.requestsPage, size, perPage)
        val next = clamp(
            when (move) {
                "next" -> current + 1
                "previous" -> current - 1
                else -> 0
            },
            size, perPage,
        )
        if (friends) state.friendsPage = next else state.requestsPage = next
    }

    fun friendsPerPage(): Int = plugin.config.data.friendsPerPage().coerceAtLeast(1)

    fun requestsPerPage(): Int = plugin.config.data.requestsPerPage().coerceAtLeast(1)

    /** The friend in place [index] (from 1) of the page [viewer]'s menu is on. */
    fun friendAt(viewer: Player, index: Int): FriendEntry? {
        val snapshot = plugin.cache.get(viewer.uniqueId) ?: return null
        val all = friends(viewer, snapshot)
        val page = clamp(plugin.cache.state(viewer.uniqueId).friendsPage, all.size, friendsPerPage())
        return all.getOrNull(page * friendsPerPage() + index - 1)
    }

    fun requestAt(viewer: Player, index: Int): RequestEntry? {
        val snapshot = plugin.cache.get(viewer.uniqueId) ?: return null
        val all = requests(snapshot)
        val page = clamp(plugin.cache.state(viewer.uniqueId).requestsPage, all.size, requestsPerPage())
        return all.getOrNull(page * requestsPerPage() + index - 1)
    }

    /** The friend [viewer] selected in the menu, if they still are friends. */
    fun selected(viewer: Player): FriendEntry? {
        val uuid: UUID = plugin.cache.state(viewer.uniqueId).selected ?: return null
        return plugin.cache.get(viewer.uniqueId)?.friends?.firstOrNull { it.profile.uuid == uuid }
    }
}
