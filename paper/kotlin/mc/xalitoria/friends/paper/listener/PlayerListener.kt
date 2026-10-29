package mc.xalitoria.friends.paper.listener

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.XaliFriends.Companion.debug
import mc.xalitoria.friends.paper.data.db.repository.PlayerRepository
import mc.xalitoria.util.message.fail
import mc.xalitoria.util.message.send
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Makes a player's row on their first login, keeps their name and last seen current, and tells their friends on this
 * server when they come and go. (Friends on other servers aren't told: only this server's players are known to it.)
 */
class PlayerListener(private val plugin: XaliFriends) : Listener {
    private val notify get() = plugin.messageConfig.data.notify

    /** Reads the player's data before they are in, so it is there for the first message sent to them. This thread may block. */
    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return
        try {
            runBlocking {
                val profile = PlayerRepository.join(event.uniqueId, event.name, plugin.config.data.defaults.settings())
                plugin.cache.put(event.uniqueId, profile)
            }
        } catch (e: Exception) {
            // They may still play; with no data their messages aren't filtered until the next refresh.
            plugin.logger.fail("Failed to load the friends of ${event.name}", e)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val snapshot = plugin.cache.get(player.uniqueId) ?: return
        for (friend in snapshot.friends) {
            val online = plugin.server.getPlayer(friend.profile.uuid) ?: continue
            if (plugin.cache.get(online.uniqueId)?.settings?.notifies != false) online send notify.join(player.name)
        }
        val counts = plugin.config.data.notifications
        val onlineFriends = snapshot.friends.count { plugin.presentation.isOnline(player, it.profile) }
        if (onlineFriends > 0 && onlineFriends >= counts.onlineFriendsMinimum) player send notify.onlineFriends(onlineFriends)
        if (snapshot.requests.isNotEmpty() && snapshot.requests.size >= counts.requestsMinimum) player send notify.waitingRequests(snapshot.requests.size)
        debug("{0} joined with {1} friends and {2} requests", player.name, snapshot.friends.size, snapshot.requests.size)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        plugin.cache.get(player.uniqueId)?.friends?.forEach { friend ->
            val online = plugin.server.getPlayer(friend.profile.uuid) ?: return@forEach
            if (plugin.cache.get(online.uniqueId)?.settings?.notifies != false) online send notify.leave(player.name)
        }
        plugin.cache.drop(player.uniqueId)
        seen(player)
    }

    private fun seen(player: Player) {
        val uuid = player.uniqueId
        plugin.scope.launch {
            try {
                PlayerRepository.seen(uuid)
            } catch (e: Exception) {
                plugin.logger.fail("Failed to record when ${player.name} left", e)
            }
        }
    }
}
