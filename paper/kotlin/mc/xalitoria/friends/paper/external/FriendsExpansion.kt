package mc.xalitoria.friends.paper.external

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.data.model.FriendEntry
import mc.xalitoria.friends.paper.data.model.Profile
import mc.xalitoria.friends.paper.data.model.RequestEntry
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

/**
 * `%xalifriends_...%`, which is what the friend menus are drawn from.
 *
 * - `friends`, `online`, `requests`: counts. `status`: the player's own status.
 * - `page_friends`, `pages_friends`, `page_requests`, `pages_requests`: the page the player's menu is on, and how many there are.
 * - `has_next_friends`, `has_previous_friends`, `has_next_requests`, `has_previous_requests`: true or false.
 * - `setting_invites`, `setting_notifies`, `setting_msgs`, `setting_lastseen`, `setting_jump`, `setting_server`: a setting as a word.
 * - `friend_<n>_<field>`: place n (from 1) of the friends page the player's menu is on. Fields: `present` (true/false),
 *   `name`, `uuid`, `status`, `online` (true/false), `state` (the online/offline word), `seen` (online, and on which
 *   server when it is known and shown, or when they were last seen), `server` (empty when unknown), `jumpable`
 *   (true/false: the viewer may jump to them now), `since`, `favorite` (true/false), `marker`.
 * - `request_<n>_<field>`: the same for requests, with `present`, `name`, `uuid`, `date`.
 * - `selected_<field>`: the friend picked in the menu, with the fields of a friend.
 *
 * A place with nobody in it answers with nothing (`present` with false), so a menu can leave its slot empty.
 */
class FriendsExpansion(private val plugin: XaliFriends) : PlaceholderExpansion() {
    override fun getIdentifier() = "xalifriends"
    override fun getAuthor() = "audizian"
    override fun getVersion() = plugin.pluginMeta.version
    override fun persist() = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val viewer = player?.player ?: return ""
        val snapshot = plugin.cache.get(viewer.uniqueId) ?: return ""
        val listings = plugin.listings
        val view = plugin.presentation
        val state = plugin.cache.state(viewer.uniqueId)
        val key = params.lowercase()

        return when {
            key == "friends" -> snapshot.friends.size.toString()
            key == "online" -> snapshot.friends.count { view.isOnline(viewer, it.profile) }.toString()
            key == "requests" -> snapshot.requests.size.toString()
            key == "status" -> view.status(snapshot.profile)
            key == "page_friends" -> (listings.clamp(state.friendsPage, snapshot.friends.size, listings.friendsPerPage()) + 1).toString()
            key == "pages_friends" -> listings.pages(snapshot.friends.size, listings.friendsPerPage()).toString()
            key == "page_requests" -> (listings.clamp(state.requestsPage, snapshot.requests.size, listings.requestsPerPage()) + 1).toString()
            key == "pages_requests" -> listings.pages(snapshot.requests.size, listings.requestsPerPage()).toString()
            key.startsWith("has_") -> hasPage(key.removePrefix("has_"), viewer, snapshot.friends.size, snapshot.requests.size)
            key.startsWith("setting_") -> when (key.removePrefix("setting_")) {
                "invites" -> view.toggle(snapshot.settings.invites)
                "notifies" -> view.toggle(snapshot.settings.notifies)
                "msgs" -> view.messages(snapshot.settings.msgs)
                "lastseen" -> view.toggle(snapshot.settings.showLastSeen)
                "jump" -> view.toggle(snapshot.settings.jump)
                "server" -> view.toggle(snapshot.settings.showServer)
                else -> null
            }
            key.startsWith("friend_") -> place(key.removePrefix("friend_"))?.let {
                friendField(viewer, listings.friendAt(viewer, it.index), it.field)
            }
            key.startsWith("request_") -> place(key.removePrefix("request_"))?.let {
                requestField(listings.requestAt(viewer, it.index), it.field)
            }
            key.startsWith("selected_") -> friendField(viewer, listings.selected(viewer), key.removePrefix("selected_"))
            else -> null
        }
    }

    /** `next_friends` and the like: whether the viewer's menu has a page that way. */
    private fun hasPage(rest: String, viewer: Player, friends: Int, requests: Int): String? {
        val listings = plugin.listings
        val state = plugin.cache.state(viewer.uniqueId)
        val move = rest.substringBefore('_')
        val friendsList = when (rest.substringAfter('_', "")) {
            "friends" -> true
            "requests" -> false
            else -> return null
        }
        val size = if (friendsList) friends else requests
        val perPage = if (friendsList) listings.friendsPerPage() else listings.requestsPerPage()
        val current = if (friendsList) state.friendsPage else state.requestsPage
        val page = listings.clamp(current, size, perPage)
        return when (move) {
            "next" -> (page + 1 < listings.pages(size, perPage)).toString()
            "previous" -> (page > 0).toString()
            else -> null
        }
    }

    private class Place(val index: Int, val field: String)

    /** `3_name` as place 3 and the field `name`. */
    private fun place(rest: String): Place? {
        val index = rest.substringBefore('_').toIntOrNull() ?: return null
        return Place(index, rest.substringAfter('_', ""))
    }

    private fun friendField(viewer: Player, entry: FriendEntry?, field: String): String? {
        val view = plugin.presentation
        val words = plugin.messageConfig.data.words
        if (field == "present") return (entry != null).toString()
        if (entry == null) return ""
        val friend: Profile = entry.profile
        return when (field) {
            "name" -> friend.name
            "uuid" -> friend.uuid.toString()
            "status" -> view.status(friend)
            "online" -> view.isOnline(viewer, friend).toString()
            "state" -> view.onlineWord(view.isOnline(viewer, friend))
            "seen" -> if (view.isOnline(viewer, friend)) view.online(friend) else view.lastSeen(friend)
            "server" -> view.server(friend)
            "jumpable" -> plugin.service.canJump(viewer, friend).toString()
            "since" -> view.date(entry.since)
            "favorite" -> entry.favorite.toString()
            "marker" -> if (entry.favorite) words.favoriteMarker else words.plainMarker
            else -> null
        }
    }

    private fun requestField(entry: RequestEntry?, field: String): String? {
        if (field == "present") return (entry != null).toString()
        if (entry == null) return ""
        return when (field) {
            "name" -> entry.profile.name
            "uuid" -> entry.profile.uuid.toString()
            "date" -> plugin.presentation.date(entry.date)
            else -> null
        }
    }
}
