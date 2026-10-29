package mc.xalitoria.friends.paper.data.model

import java.time.Instant
import java.util.UUID

/** Who may send a player private messages; stored as 0, 1 and 2, as the original plugin did. */
enum class SettingState(val id: Int) {
    DISABLED(0),
    FRIENDS_ONLY(1),
    ENABLED(2);

    /** What clicking the setting does: friends only and everyone swap, and a state of "nobody" from an old install goes to friends only. */
    fun toggled(): SettingState = if (this == ENABLED) FRIENDS_ONLY else if (this == FRIENDS_ONLY) ENABLED else FRIENDS_ONLY

    companion object {
        fun of(id: Int?): SettingState? = entries.firstOrNull { it.id == id }
    }
}

/** What a player chose. */
data class Settings(
    val invites: Boolean = true,
    val notifies: Boolean = true,
    val msgs: SettingState = SettingState.FRIENDS_ONLY,
    val showLastSeen: Boolean = true,
)

/** A row of the player table. */
data class Profile(
    val uuid: UUID,
    val name: String,
    val settings: Settings,
    val lastSeen: Instant?,
    val status: String?,
)

data class FriendEntry(val profile: Profile, val favourite: Boolean, val since: Instant)

data class RequestEntry(val profile: Profile, val date: Instant)

/** One player's side of the system: their row, friends and requests, as read from the database. */
class Snapshot(
    val profile: Profile,
    val friends: List<FriendEntry>,
    val requests: List<RequestEntry>,
    val loadedAt: Long = System.currentTimeMillis(),
) {
    val settings: Settings get() = profile.settings
    val friendIds: Set<UUID> = friends.mapTo(HashSet()) { it.profile.uuid }
}
