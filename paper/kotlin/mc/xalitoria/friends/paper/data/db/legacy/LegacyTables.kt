package mc.xalitoria.friends.paper.data.db.legacy

import org.jetbrains.exposed.v1.core.Table

/**
 * The original FriendSystem's tables, column for column, as they are to be read, and dropped once copied. They are classes, not
 * objects, because where they are (the prefix) is the user's to say.
 */
class LegacyTables(prefix: String) {
    val player = LegacyPlayer(prefix)
    val friend = LegacyFriend(prefix)
    val request = LegacyRequest(prefix)
}

class LegacyPlayer(prefix: String) : Table(prefix + "player") {
    val uuid = varchar("UUID", 40)
    val name = varchar("name", 24).nullable()
    val invites = bool("invites").nullable()
    val notifies = bool("notifies").nullable()
    val msgs = setting("msgs")
    val jump = bool("jump").nullable()
    val showLastSeen = bool("showLastSeen").nullable()
    val showServer = bool("showServer").nullable()
    val partyInvites = setting("partyInvites")
    val lastSeen = dateTime("lastSeen").nullable()
    val status = varchar("status", 64).nullable()

    override val primaryKey = PrimaryKey(uuid)
}

/** `owner` has `friend` as a friend; a friendship is two of these. */
class LegacyFriend(prefix: String) : Table(prefix + "friend") {
    val owner = varchar("UUID", 40)
    val friend = varchar("friendUUID", 40)
    // The original plugin's own column name, which has to be read as it is.
    val favorite = bool("isFavourite").nullable()
    val since = dateTime("friendshipStart").nullable()

    override val primaryKey = PrimaryKey(owner, friend)
}

/** `requester` (`requestUUID`) asked `target` (`UUID`). */
class LegacyRequest(prefix: String) : Table(prefix + "request") {
    val target = varchar("UUID", 40)
    val requester = varchar("requestUUID", 40)
    val date = dateTime("requestDate").nullable()

    override val primaryKey = PrimaryKey(target, requester)
}
