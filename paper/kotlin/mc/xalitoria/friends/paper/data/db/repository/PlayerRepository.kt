package mc.xalitoria.friends.paper.data.db.repository

import mc.xalitoria.friends.paper.data.db.DatabaseManager.dbTransaction
import mc.xalitoria.friends.paper.data.db.tables.PlayerTable
import mc.xalitoria.friends.paper.data.model.Profile
import mc.xalitoria.friends.paper.data.model.SettingState
import mc.xalitoria.friends.paper.data.model.Settings
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/** The settings a player can change; the key is how `/friend toggle` and the menus name them. */
enum class SettingKey(val key: String) {
    INVITES("invites"),
    NOTIFIES("notifies"),
    MSGS("msgs"),
    LAST_SEEN("lastseen"),
    JUMP("jump"),
    SERVER("server");

    companion object {
        fun of(key: String): SettingKey? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

object PlayerRepository {
    internal fun ResultRow.toProfile(): Profile = Profile(
        uuid = this[PlayerTable.uuid].toJavaUuid(),
        name = this[PlayerTable.name],
        settings = Settings(
            invites = this[PlayerTable.invites],
            notifies = this[PlayerTable.notifies],
            msgs = SettingState.of(this[PlayerTable.msgs].toInt()) ?: SettingState.FRIENDS_ONLY,
            showLastSeen = this[PlayerTable.showLastSeen],
            jump = this[PlayerTable.jump],
            showServer = this[PlayerTable.showServer],
        ),
        lastSeen = this[PlayerTable.lastSeen]?.let(Instant::ofEpochMilli),
        status = this[PlayerTable.status],
    )

    suspend fun get(uuid: UUID): Profile? =
        dbTransaction {
            PlayerTable.selectAll().where { PlayerTable.uuid eq uuid.toKotlinUuid() }.singleOrNull()?.toProfile()
        }

    /** A player by UUID or by name in any case. */
    suspend fun find(uuidOrName: String): Profile? {
        val uuid = runCatching { UUID.fromString(uuidOrName) }.getOrNull()
        return if (uuid != null) get(uuid) else dbTransaction {
            PlayerTable.selectAll().where { PlayerTable.nameLower eq uuidOrName.lowercase() }.firstOrNull()?.toProfile()
        }
    }

    suspend fun getAll(uuids: Collection<UUID>): Map<UUID, Profile> {
        if (uuids.isEmpty()) return emptyMap()
        return dbTransaction {
            PlayerTable.selectAll()
                .where { PlayerTable.uuid inList uuids.map { it.toKotlinUuid() } }
                .map { it.toProfile() }
                .associateBy { it.uuid }
        }
    }

    /** A player arrives: their row is made with [defaults] the first time, and otherwise kept up to date with their name and the time. */
    suspend fun join(uuid: UUID, name: String, defaults: Settings): Profile =
        dbTransaction {
            val id = uuid.toKotlinUuid()
            val now = System.currentTimeMillis()
            val existing = PlayerTable.selectAll().where { PlayerTable.uuid eq id }.singleOrNull()
            if (existing == null) {
                PlayerTable.insert {
                    it[PlayerTable.uuid] = id
                    it[PlayerTable.name] = name
                    it[nameLower] = name.lowercase()
                    it[invites] = defaults.invites
                    it[notifies] = defaults.notifies
                    it[msgs] = defaults.msgs.id.toByte()
                    it[showLastSeen] = defaults.showLastSeen
                    it[jump] = defaults.jump
                    it[showServer] = defaults.showServer
                    it[lastSeen] = now
                }
            } else {
                PlayerTable.update({ PlayerTable.uuid eq id }) {
                    it[PlayerTable.name] = name
                    it[nameLower] = name.lowercase()
                    it[lastSeen] = now
                }
            }
            PlayerTable.selectAll().where { PlayerTable.uuid eq id }.single().toProfile()
        }

    suspend fun seen(uuid: UUID) {
        dbTransaction {
            PlayerTable.update({ PlayerTable.uuid eq uuid.toKotlinUuid() }) { it[lastSeen] = System.currentTimeMillis() }
        }
    }

    /** An empty [status] clears it. */
    suspend fun setStatus(uuid: UUID, status: String) {
        dbTransaction {
            PlayerTable.update({ PlayerTable.uuid eq uuid.toKotlinUuid() }) { it[PlayerTable.status] = status.ifEmpty { null } }
        }
    }

    /** Stores [key]'s new value: a state for the messages, on or off for the others. */
    suspend fun set(uuid: UUID, key: SettingKey, on: Boolean = false, state: SettingState = SettingState.FRIENDS_ONLY) {
        dbTransaction {
            PlayerTable.update({ PlayerTable.uuid eq uuid.toKotlinUuid() }) {
                when (key) {
                    SettingKey.INVITES -> it[invites] = on
                    SettingKey.NOTIFIES -> it[notifies] = on
                    SettingKey.MSGS -> it[msgs] = state.id.toByte()
                    SettingKey.LAST_SEEN -> it[showLastSeen] = on
                    SettingKey.JUMP -> it[jump] = on
                    SettingKey.SERVER -> it[showServer] = on
                }
            }
        }
    }
}
