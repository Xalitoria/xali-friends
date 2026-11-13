import mc.xalitoria.friends.paper.data.model.FriendEntry
import mc.xalitoria.friends.paper.data.model.Profile
import mc.xalitoria.friends.paper.data.model.SettingState
import mc.xalitoria.friends.paper.data.model.Settings
import mc.xalitoria.friends.paper.data.model.Snapshot
import mc.xalitoria.friends.paper.service.MessageRule
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageRule {
    private val receiver = UUID.randomUUID()
    private val friend = UUID.randomUUID()
    private val stranger = UUID.randomUUID()

    private fun profile(uuid: UUID, msgs: SettingState = SettingState.FRIENDS_ONLY) =
        Profile(uuid, "p", Settings(msgs = msgs), null, null)

    private fun snapshot(msgs: SettingState) =
        Snapshot(profile(receiver, msgs), listOf(FriendEntry(profile(friend), favorite = false, since = java.time.Instant.EPOCH)), emptyList())

    @Test
    fun `friends only lets friends through and keeps strangers out`() {
        val data = snapshot(SettingState.FRIENDS_ONLY)
        assertTrue(MessageRule.allows(data, friend))
        assertFalse(MessageRule.allows(data, stranger))
    }

    @Test
    fun `everyone lets everyone through`() {
        assertTrue(MessageRule.allows(snapshot(SettingState.ENABLED), stranger))
    }

    @Test
    fun `nobody, from an old install, keeps everyone out`() {
        val data = snapshot(SettingState.DISABLED)
        assertFalse(MessageRule.allows(data, friend))
        assertFalse(MessageRule.allows(data, stranger))
    }

    @Test
    fun `the console and a player whose data didn't load are not stopped`() {
        assertTrue(MessageRule.allows(snapshot(SettingState.FRIENDS_ONLY), null))
        assertTrue(MessageRule.allows(null, stranger))
    }

    @Test
    fun `clicking the messages setting swaps friends only and everyone`() {
        assertTrue(SettingState.FRIENDS_ONLY.toggled() == SettingState.ENABLED)
        assertTrue(SettingState.ENABLED.toggled() == SettingState.FRIENDS_ONLY)
        assertTrue(SettingState.DISABLED.toggled() == SettingState.FRIENDS_ONLY)
    }
}
