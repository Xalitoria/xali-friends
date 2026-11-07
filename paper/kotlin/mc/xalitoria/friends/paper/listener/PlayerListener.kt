package mc.xalitoria.friends.paper.listener

import com.xalitoria.util.message.fail
import com.xalitoria.util.message.send
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.XaliFriends.Companion.debug
import mc.xalitoria.friends.paper.data.db.repository.PlayerRepository
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Makes a player's row on their first login, keeps their name and last seen current, and tells their friends when they
 * come and go: on this server alone, or, with the proxy plugin, on the whole network (see [mc.xalitoria.friends.paper.proxy.ProxyLink]).
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
        // With the proxy plugin the proxy announces arrivals once for the whole network; alone, this server does.
        if (plugin.link == null) plugin.service.announce(player.uniqueId, player.name, joined = true)
        // The proxy tells this server where everyone is a moment after the player arrives, and the summary needs it.
        if (plugin.link == null) summary(player)
        else player.scheduler.runDelayed(plugin, {
            // Only for coming onto the network, not for each server a player passes through.
            if (plugin.link?.consumeArrival(player.uniqueId) == true) summary(player)
        }, null, SUMMARY_DELAY_TICKS)
    }

    private fun summary(player: Player) {
        val snapshot = plugin.cache.get(player.uniqueId) ?: return
        val counts = plugin.config.data.notifications
        val onlineFriends = snapshot.friends.count { plugin.presentation.isOnline(player, it.profile) }
        if (onlineFriends > 0 && onlineFriends >= counts.onlineFriendsMinimum) player send notify.onlineFriends(onlineFriends)
        if (snapshot.requests.isNotEmpty() && snapshot.requests.size >= counts.requestsMinimum) player send notify.waitingRequests(snapshot.requests.size)
        debug("{0} joined with {1} friends and {2} requests", player.name, snapshot.friends.size, snapshot.requests.size)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        if (plugin.link == null) plugin.service.announce(player.uniqueId, player.name, joined = false)
        plugin.cache.drop(player.uniqueId)
        seen(player)
    }

    companion object {
        private const val SUMMARY_DELAY_TICKS = 40L
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
