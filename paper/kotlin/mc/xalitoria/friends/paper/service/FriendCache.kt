package mc.xalitoria.friends.paper.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.XaliFriends.Companion.debug
import mc.xalitoria.friends.paper.data.db.repository.FriendRepository
import mc.xalitoria.friends.paper.data.db.repository.PlayerRepository
import mc.xalitoria.friends.paper.data.model.Profile
import mc.xalitoria.friends.paper.data.model.Snapshot
import mc.xalitoria.util.message.fail
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/** Which page of the friends and requests a player's menus show, and the friend their player menu is about. */
class UiState {
    @Volatile var friendsPage = 0
    @Volatile var requestsPage = 0
    @Volatile var selected: UUID? = null
}

/**
 * What the database holds about the players who are online, kept in memory. Private messages are checked against it
 * (they can't wait for a query) and so are the menus' placeholders. It is loaded before a player joins, redone after
 * every change made here, and on a timer, which is how a change made on another server arrives.
 */
class FriendCache(private val plugin: XaliFriends) {
    private val snapshots = ConcurrentHashMap<UUID, Snapshot>()
    private val states = ConcurrentHashMap<UUID, UiState>()

    fun get(uuid: UUID): Snapshot? = snapshots[uuid]

    fun state(uuid: UUID): UiState = states.computeIfAbsent(uuid) { UiState() }

    /** Reads [uuid]'s data again and keeps it; null if they have no row. */
    suspend fun load(uuid: UUID): Snapshot? {
        val profile = PlayerRepository.get(uuid) ?: return null
        val friends = FriendRepository.friends(uuid)
        val requests = FriendRepository.requests(uuid, plugin.requestCutoff())
        return Snapshot(profile, friends, requests).also { snapshots[uuid] = it }
    }

    /** Keeps [uuid]'s data, as [PlayerRepository.join] made it, with the friends and requests read for it. */
    suspend fun put(uuid: UUID, profile: Profile): Snapshot =
        Snapshot(profile, FriendRepository.friends(uuid), FriendRepository.requests(uuid, plugin.requestCutoff()))
            .also { snapshots[uuid] = it }

    fun drop(uuid: UUID) {
        snapshots.remove(uuid)
        states.remove(uuid)
    }

    /** Reloads everyone online every `cache.refresh-seconds`, and forgets those who left without us noticing. */
    fun startRefreshing() {
        plugin.scope.launch {
            while (true) {
                delay(plugin.config.data.cache.refreshSeconds.coerceAtLeast(5).seconds)
                val now = System.currentTimeMillis()
                for (uuid in snapshots.keys.toList()) {
                    // A player still logging in isn't online yet; give them a minute before they count as gone.
                    if (plugin.server.getPlayer(uuid) == null) {
                        if (now - (snapshots[uuid]?.loadedAt ?: now) > 60_000) drop(uuid)
                        continue
                    }
                    try {
                        load(uuid)
                    } catch (e: Exception) {
                        plugin.logger.fail("Failed to refresh the friends of $uuid", e)
                    }
                }
                debug("Refreshed the friends of {0} players", snapshots.size)
            }
        }
    }
}
