package mc.xalitoria.friends.paper.data.db.tables

import mc.xalitoria.friends.paper.data.db.DatabaseManager
import org.jetbrains.exposed.v1.core.Table

/**
 * Every player seen on a server. The UUID is 16 bytes rather than its 36-character text, and [nameLower] is indexed, so
 * a lookup by name is not a scan of the table. Times are milliseconds since the epoch, in UTC.
 */
object PlayerTable : Table(DatabaseManager.tableName("player")) {
    val uuid = uuid("uuid")
    val name = varchar("name", 24)
    val nameLower = varchar("name_lower", 24).index()
    val invites = bool("invites").default(true)
    val notifies = bool("notifies").default(true)
    val showLastSeen = bool("show_last_seen").default(true)
    val jump = bool("jump").default(true)
    val showServer = bool("show_server").default(true)

    /** 0 nobody, 1 friends only, 2 everyone. */
    val msgs = byte("msgs").default(1)
    val lastSeen = long("last_seen").nullable()
    val status = varchar("status", 64).nullable()

    override val primaryKey = PrimaryKey(uuid)
}
