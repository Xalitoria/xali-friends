package mc.xalitoria.friends.paper.data.db.tables

import mc.xalitoria.friends.paper.data.db.DatabaseManager
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * One row per friendship, between the players [low] and [high] (the lower of the two UUIDs first, see
 * [mc.xalitoria.friends.paper.data.db.repository.Pairing]), so it can't be half made or half removed. Each side keeps their
 * own favorite. A player's friends are the rows where they are either [low] or [high], each side with an index of its own.
 */
object FriendTable : Table(DatabaseManager.tableName("friend")) {
    val low = reference("low", PlayerTable.uuid, onDelete = ReferenceOption.CASCADE)
    val high = reference("high", PlayerTable.uuid, onDelete = ReferenceOption.CASCADE)
    val favoriteLow = bool("favorite_low").default(false)
    val favoriteHigh = bool("favorite_high").default(false)
    val since = long("since")

    override val primaryKey = PrimaryKey(low, high)

    init {
        index(false, high)
    }
}
