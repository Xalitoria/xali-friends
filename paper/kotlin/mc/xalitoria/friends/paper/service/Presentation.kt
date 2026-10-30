package mc.xalitoria.friends.paper.service

import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.data.model.Profile
import mc.xalitoria.friends.paper.data.model.SettingState
import org.bukkit.entity.Player
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How the data reads to a player: dates, who is online, and the words for a setting. Shared by the commands and the menus. */
class Presentation(private val plugin: XaliFriends) {
    private var pattern = ""
    private var formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private val words get() = plugin.messageConfig.data.words

    fun date(date: Instant?): String {
        if (date == null) return words.never
        val wanted = plugin.config.data.dateFormat
        if (wanted != pattern) {
            formatter = runCatching { DateTimeFormatter.ofPattern(wanted) }.getOrDefault(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            pattern = wanted
        }
        return formatter.format(date.atZone(ZoneId.systemDefault()))
    }

    /** Whether [friend] is online on this server or, with the proxy plugin or XaliChat, anywhere behind the proxy. */
    fun isOnline(viewer: Player?, friend: Profile): Boolean =
        plugin.server.getPlayer(friend.uuid) != null || plugin.link?.isOnline(friend.uuid) == true ||
            plugin.chat?.isOnline(friend.name, viewer) == true

    /** The server [friend] is on, as players read it; empty when it isn't known or they keep it to themselves. */
    fun server(friend: Profile): String {
        if (!friend.settings.showServer) return ""
        val link = plugin.link ?: return ""
        return link.serverOf(friend.uuid)?.let(link::displayName).orEmpty()
    }

    /** "Online", and where when that is known. */
    fun online(friend: Profile): String {
        val server = server(friend)
        return if (server.isEmpty()) words.online else "${words.online} ${words.onServer(server)}"
    }

    fun onlineWord(online: Boolean): String = if (online) words.online else words.offline

    /** "last seen <date>", or that it's hidden when the friend chose to hide it. */
    fun lastSeen(friend: Profile): String =
        if (friend.settings.showLastSeen) words.lastSeen(date(friend.lastSeen)) else words.lastSeenHidden

    fun status(profile: Profile): String = profile.status ?: words.noStatus

    fun toggle(on: Boolean): String = if (on) words.on else words.off

    fun messages(state: SettingState): String = when (state) {
        SettingState.ENABLED -> words.everyone
        SettingState.FRIENDS_ONLY -> words.friendsOnly
        SettingState.DISABLED -> words.nobody
    }
}
