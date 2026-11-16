package mc.xalitoria.friends.paper.service

import com.xalitoria.util.message.fail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.XaliFriends.Companion.debug
import mc.xalitoria.friends.paper.data.db.repository.FriendRepository
import mc.xalitoria.friends.paper.data.db.repository.PlayerRepository
import mc.xalitoria.friends.paper.data.model.Profile
import mc.xalitoria.friends.paper.data.model.Snapshot
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
 * (they can't wait for a query) and so are the menus' placeholders, which also ask about players who are not online (see [peek]). It is loaded before a player joins, redone after
 * every change made here, and on a timer, which is how a change made on another server arrives.
 */
class FriendCache(private val plugin: XaliFriends) {
    private val snapshots = ConcurrentHashMap<UUID, Snapshot>()
    private val states = ConcurrentHashMap<UUID, UiState>()

    /** What the database holds of players who are not online here, as it was when last read in the background; for the placeholders. */
    private val offline = OfflineCache<Snapshot>(plugin.scope, { OFFLINE_MAX_AGE_MILLIS }, { uuid, e -> plugin.logger.warning("Could not read the friends of $uuid: ${e.message}") }) {
        read(it)
    }

    fun get(uuid: UUID): Snapshot? = snapshots[uuid]

    /**
     * [get] for a placeholder: the snapshot of a player who is online, and for anyone else what was last read of them in the
     * background. Null for a player with no row, and until that first read is done.
     */
    fun peek(uuid: UUID): Snapshot? = snapshots[uuid] ?: offline.get(uuid)?.value

    fun state(uuid: UUID): UiState = states.computeIfAbsent(uuid) { UiState() }

    /** Reads [uuid]'s data again and keeps it; null if they have no row. */
    suspend fun load(uuid: UUID): Snapshot? {
        offline.invalidate(uuid)
        return read(uuid)?.also { snapshots[uuid] = it }
    }

    private suspend fun read(uuid: UUID): Snapshot? {
        val profile = PlayerRepository.get(uuid) ?: return null
        val friends = FriendRepository.friends(uuid)
        val requests = FriendRepository.requests(uuid, plugin.requestCutoff())
        return Snapshot(profile, friends, requests)
    }

    /** Keeps [uuid]'s data, as [PlayerRepository.join] made it, with the friends and requests read for it. */
    suspend fun put(uuid: UUID, profile: Profile): Snapshot {
        offline.invalidate(uuid)
        return Snapshot(profile, FriendRepository.friends(uuid), FriendRepository.requests(uuid, plugin.requestCutoff()))
            .also { snapshots[uuid] = it }
    }

    fun drop(uuid: UUID) {
        snapshots.remove(uuid)
        states.remove(uuid)
        // What was read of them while they were away is older than what they had.
        offline.invalidate(uuid)
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
                offline.sweep()
                debug("Refreshed the friends of {0} players", snapshots.size)
            }
        }
    }

    private companion object {
        const val OFFLINE_MAX_AGE_MILLIS = 30_000L
    }
}
