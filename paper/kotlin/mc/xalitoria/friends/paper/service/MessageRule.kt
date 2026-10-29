package mc.xalitoria.friends.paper.service

import mc.xalitoria.friends.paper.data.model.SettingState
import mc.xalitoria.friends.paper.data.model.Snapshot
import java.util.UUID

/** Who a player takes private messages from. */
object MessageRule {
    /**
     * Whether [sender] (null for the console) may message the player whose data is [receiver]. Nothing is known about a
     * player whose data didn't load, which lets the message through rather than silencing everyone while the database is down.
     */
    fun allows(receiver: Snapshot?, sender: UUID?): Boolean {
        if (sender == null || receiver == null) return true
        return when (receiver.settings.msgs) {
            SettingState.ENABLED -> true
            SettingState.FRIENDS_ONLY -> sender in receiver.friendIds
            SettingState.DISABLED -> false
        }
    }
}
