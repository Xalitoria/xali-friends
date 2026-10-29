package mc.xalitoria.friends.paper.data.db.tables

import mc.xalitoria.friends.paper.data.db.DatabaseManager
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/** [requester] asked [target] to be friends, at [sentAt] (milliseconds since the epoch). */
object RequestTable : Table(DatabaseManager.tableName("request")) {
    val target = reference("target", PlayerTable.uuid, onDelete = ReferenceOption.CASCADE)
    val requester = reference("requester", PlayerTable.uuid, onDelete = ReferenceOption.CASCADE)
    val sentAt = long("sent_at")

    override val primaryKey = PrimaryKey(target, requester)

    init {
        index(false, requester)
    }
}
