package mc.xalitoria.friends.paper.data.db.tables

import mc.xalitoria.friends.paper.data.db.DatabaseManager
import org.jetbrains.exposed.v1.core.Table

/** What the plugin knows about its own database: which schema it is, and whether the original's data was moved in. */
object MetaTable : Table(DatabaseManager.tableName("meta")) {
    val key = varchar("key", 32)
    val value = varchar("value", 255)

    override val primaryKey = PrimaryKey(key)
}
