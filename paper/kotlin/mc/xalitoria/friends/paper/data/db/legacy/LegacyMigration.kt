package mc.xalitoria.friends.paper.data.db.legacy

import mc.xalitoria.friends.paper.data.db.repository.Pairing
import mc.xalitoria.friends.paper.data.db.tables.FriendTable
import mc.xalitoria.friends.paper.data.db.tables.MetaTable
import mc.xalitoria.friends.paper.data.db.tables.PlayerTable
import mc.xalitoria.friends.paper.data.db.tables.RequestTable
import mc.xalitoria.friends.paper.data.model.SettingState
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.uuid.toKotlinUuid

/**
 * Moves the original FriendSystem's data into this plugin's tables, and then **drops the original's tables**: the new
 * ones replace the old storage. There is no way back from it, so a backup before the first start is the server owner's
 * to make. The tables are dropped only after everything was copied; if the drop fails the copy is simply done again on
 * the next start, since every row is inserted only if it isn't there.
 *
 * What changes on the way: UUIDs go from text to 16 bytes; the times, which the original kept without a zone (UTC in
 * SQLite, the database server's own zone in MySQL), become UTC milliseconds; a friendship's two rows become one, each
 * side keeping their own favourite; the party setting (`partyInvites`), which nothing here uses, is left behind.
 * Rows that can't be carried (a UUID that isn't one, someone who is their own friend, a friend with no `player` row) are
 * counted and skipped.
 *
 * Several servers may share the database and start together: the first to insert the lock row does the work and the
 * others wait for it. A lock that is older than [STALE] (a server that died halfway) is taken over.
 */
class LegacyMigration(
    /** Where the original's tables are. */
    private val source: Database,
    /** Where this plugin's are. */
    private val target: Database,
    private val legacy: LegacyTables,
    /** The zone the original's times are in. */
    private val zone: ZoneId,
    private val batchSize: Int = 1000,
    private val log: (String) -> Unit = {},
) {
    class Report(val players: Int, val friends: Int, val requests: Int, val skipped: Int) {
        override fun toString() = "$players players, $friends friendships, $requests requests; $skipped rows skipped"
    }

    /** The counts of what was moved, or null when there was nothing to do (it was done before, or there are no original tables). */
    fun run(): Report? {
        require(legacy.player.tableName != PlayerTable.tableName) {
            "The original's tables and this plugin's would be the same ones: database.table-prefix must differ from migration.legacy-prefix"
        }
        if (!transaction(source) { legacy.player.exists() && legacy.friend.exists() && legacy.request.exists() }) return null
        if (!acquire()) return null
        return try {
            copy().also { report ->
                dropOriginal()
                transaction(target) {
                    MetaTable.update({ MetaTable.key eq KEY }) { it[value] = "done:${System.currentTimeMillis()}" }
                    MetaTable.insertIgnore { it[key] = "migration_report"; it[value] = report.toString() }
                }
                log("Migrated the original FriendSystem's data and dropped its tables: $report")
            }
        } catch (e: Throwable) {
            // So that the next start tries again, rather than waiting out a lock nobody holds.
            runCatching { transaction(target) { MetaTable.deleteWhere { (MetaTable.key eq KEY) and (MetaTable.value like "running:%") } } }
            throw e
        }
    }

    /** Takes the lock; false if the work is done or another server did it while this one waited. */
    private fun acquire(): Boolean {
        val deadline = System.currentTimeMillis() + STALE
        while (true) {
            val now = System.currentTimeMillis()
            val took = transaction(target) {
                MetaTable.insertIgnore { it[key] = KEY; it[value] = "running:$now" }.insertedCount > 0
            }
            if (took) return true
            val state = transaction(target) { MetaTable.selectAll().where { MetaTable.key eq KEY }.singleOrNull()?.get(MetaTable.value) }
                ?: continue
            if (state.startsWith("done")) return false
            val started = state.removePrefix("running:").toLongOrNull() ?: 0
            if (now - started > STALE) {
                log("Taking over a migration that stopped partway (started ${(now - started) / 1000}s ago)")
                transaction(target) { MetaTable.update({ MetaTable.key eq KEY }) { it[value] = "running:$now" } }
                return true
            }
            if (now > deadline) error("Another server has been migrating for over ${STALE / 60_000} minutes")
            log("Another server is migrating the original's data; waiting")
            Thread.sleep(2000)
        }
    }

    private fun copy(): Report {
        var skipped = 0
        val fallback = System.currentTimeMillis()

        // Players first: everything else points at them.
        val players = transaction(source) { legacy.player.selectAll().toList() }
        val known = HashSet<UUID>(players.size * 2)
        val rows = ArrayList<Player>(players.size)
        for (row in players) {
            val uuid = parse(row[legacy.player.uuid])
            if (uuid == null || !known.add(uuid)) { skipped++; continue }
            val name = (row[legacy.player.name] ?: uuid.toString().take(16)).take(24)
            rows += Player(
                uuid, name,
                row[legacy.player.invites] ?: true, row[legacy.player.notifies] ?: true,
                (SettingState.of(row[legacy.player.msgs]) ?: SettingState.FRIENDS_ONLY).id.toByte(),
                row[legacy.player.showLastSeen] ?: true,
                row[legacy.player.jump] ?: true,
                row[legacy.player.showServer] ?: true,
                row[legacy.player.lastSeen]?.let(::millis),
                row[legacy.player.status]?.takeIf { it.isNotBlank() }?.take(64),
            )
        }
        rows.chunked(batchSize).forEach { chunk ->
            transaction(target) {
                PlayerTable.batchInsert(chunk, ignore = true) { player ->
                    this[PlayerTable.uuid] = player.uuid.toKotlinUuid()
                    this[PlayerTable.name] = player.name
                    this[PlayerTable.nameLower] = player.name.lowercase()
                    this[PlayerTable.invites] = player.invites
                    this[PlayerTable.notifies] = player.notifies
                    this[PlayerTable.msgs] = player.msgs
                    this[PlayerTable.showLastSeen] = player.showLastSeen
                    this[PlayerTable.jump] = player.jump
                    this[PlayerTable.showServer] = player.showServer
                    this[PlayerTable.lastSeen] = player.lastSeen
                    this[PlayerTable.status] = player.status
                }
            }
        }
        log("Moved ${rows.size} players")

        // A friendship was two rows. The row of the low side makes it; the other one adds its own favourite.
        val legacyFriends = transaction(source) { legacy.friend.selectAll().toList() }
        val friends = ArrayList<Friend>(legacyFriends.size)
        for (row in legacyFriends) {
            val owner = parse(row[legacy.friend.owner])
            val friend = parse(row[legacy.friend.friend])
            if (owner == null || friend == null || owner == friend || owner !in known || friend !in known) { skipped++; continue }
            friends += Friend(owner, friend, row[legacy.friend.favourite] ?: false, row[legacy.friend.since]?.let(::millis) ?: fallback)
        }
        val sides = friends.partition { Pairing.isLow(it.owner, it.friend) }
        val lows = sides.first
        val highs = sides.second
        lows.chunked(batchSize).forEach { chunk ->
            transaction(target) {
                for (friend in chunk) FriendTable.insertIgnore {
                    it[low] = friend.owner.toKotlinUuid()
                    it[high] = friend.friend.toKotlinUuid()
                    it[favouriteLow] = friend.favourite
                    it[since] = friend.since
                }
            }
        }
        highs.chunked(batchSize).forEach { chunk ->
            transaction(target) {
                for (friend in chunk) {
                    val low = friend.friend.toKotlinUuid()
                    val high = friend.owner.toKotlinUuid()
                    val updated = FriendTable.update({ (FriendTable.low eq low) and (FriendTable.high eq high) }) { it[favouriteHigh] = friend.favourite }
                    // Only this side was in the original's table.
                    if (updated == 0) FriendTable.insertIgnore {
                        it[FriendTable.low] = low
                        it[FriendTable.high] = high
                        it[favouriteHigh] = friend.favourite
                        it[since] = friend.since
                    }
                }
            }
        }
        val friendships = transaction(target) { FriendTable.selectAll().count().toInt() }
        log("Moved ${friends.size} friend rows as $friendships friendships")

        val requests = transaction(source) { legacy.request.selectAll().toList() }
        var moved = 0
        val carried = ArrayList<Request>(requests.size)
        for (row in requests) {
            val target = parse(row[legacy.request.target])
            val requester = parse(row[legacy.request.requester])
            if (target == null || requester == null || target == requester || target !in known || requester !in known) { skipped++; continue }
            carried += Request(target, requester, row[legacy.request.date]?.let(::millis) ?: fallback)
        }
        carried.chunked(batchSize).forEach { chunk ->
            transaction(target) {
                for (request in chunk) {
                    RequestTable.insertIgnore {
                        it[RequestTable.target] = request.target.toKotlinUuid()
                        it[requester] = request.requester.toKotlinUuid()
                        it[sentAt] = request.sentAt
                    }
                    moved++
                }
            }
        }
        return Report(rows.size, friendships, moved, skipped)
    }

    /** Removes the original's tables, the ones that point at `player` first. */
    private fun dropOriginal() {
        // One at a time, because SchemaUtils.drop picks its own order and the original's foreign keys aren't declared on these classes.
        for (table in listOf(legacy.friend, legacy.request, legacy.player)) transaction(source) { SchemaUtils.drop(table) }
    }

    private fun millis(date: LocalDateTime): Long = date.atZone(zone).toInstant().toEpochMilli()

    private fun parse(text: String): UUID? = runCatching { UUID.fromString(text.trim()) }.getOrNull()

    private class Player(
        val uuid: UUID, val name: String, val invites: Boolean, val notifies: Boolean, val msgs: Byte,
        val showLastSeen: Boolean, val jump: Boolean, val showServer: Boolean, val lastSeen: Long?, val status: String?,
    )

    private class Friend(val owner: UUID, val friend: UUID, val favourite: Boolean, val since: Long)

    private class Request(val target: UUID, val requester: UUID, val sentAt: Long)

    companion object {
        const val KEY = "migration"

        /** How long a migration may run before another server takes it over. */
        const val STALE = 10 * 60_000L
    }
}
