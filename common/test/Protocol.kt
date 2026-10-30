import mc.xalitoria.friends.protocol.Located
import mc.xalitoria.friends.protocol.Message
import mc.xalitoria.friends.protocol.Protocol
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Protocol {
    private val secret = "s3cret\n"
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    private fun roundTrip(message: Message) = Protocol.open(secret, Protocol.seal(secret, message))

    @Test
    fun `locations survive the trip`() {
        val sent = Message.Locations(true, listOf(Located(a, "Alice", "lobby"), Located(b, "Bob", "survival")))
        val got = assertIs<Message.Locations>(roundTrip(sent))
        assertTrue(got.replace)
        assertEquals(listOf("Alice" to "lobby", "Bob" to "survival"), got.players.map { it.name to it.server })
        assertEquals(listOf(a, b), got.players.map { it.uuid })
    }

    @Test
    fun `presence events survive the trip`() {
        assertEquals("lobby", assertIs<Message.Joined>(roundTrip(Message.Joined(Located(a, "Alice", "lobby")))).who.server)
        assertEquals(a, assertIs<Message.Moved>(roundTrip(Message.Moved(Located(a, "Alice", "creative")))).who.uuid)
        val left = assertIs<Message.Left>(roundTrip(Message.Left(a, "Alice")))
        assertEquals(a to "Alice", left.uuid to left.name)
    }

    @Test
    fun `jumps and their results survive the trip`() {
        val jump = assertIs<Message.Jump>(roundTrip(Message.Jump(a, b)))
        assertEquals(a to b, jump.player to jump.target)
        for (status in Message.JumpResult.Status.entries) {
            val result = assertIs<Message.JumpResult>(roundTrip(Message.JumpResult(a, status, "survival")))
            assertEquals(status to "survival", result.status to result.server)
        }
    }

    @Test
    fun `notices survive the trip`() {
        for (kind in Message.Notify.Kind.entries) {
            val notify = assertIs<Message.Notify>(roundTrip(Message.Notify(kind, a, b, "Bob")))
            assertEquals(listOf<Any>(kind, a, b, "Bob"), listOf(notify.kind, notify.target, notify.from, notify.fromName))
        }
    }

    @Test
    fun `a message signed with another secret, or altered, or cut short, is not opened`() {
        val frame = Protocol.seal(secret, Message.Jump(a, b))
        assertNull(Protocol.open("other", frame))
        assertNull(Protocol.open(null, frame))
        assertNull(Protocol.open("", frame))
        assertNull(Protocol.open(secret, frame.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }))
        assertNull(Protocol.open(secret, frame.copyOf(10)))
        assertNull(Protocol.open(secret, ByteArray(0)))
    }

    /** [body] with a valid signature, as a peer of another version might send it. */
    private fun signed(body: ByteArray): ByteArray = javax.crypto.Mac.getInstance("HmacSHA256")
        .apply { init(javax.crypto.spec.SecretKeySpec(secret.trim().toByteArray(), "HmacSHA256")) }
        .doFinal(body) + body

    @Test
    fun `a signed message that is not one of ours is ignored`() {
        val unknown = java.io.ByteArrayOutputStream().also { java.io.DataOutputStream(it).writeUTF("SOMETHING") }.toByteArray()
        assertNull(Protocol.open(secret, signed(unknown)))
        assertNull(Protocol.open(secret, signed(Message.Jump(a, b).encode().copyOf(12))), "cut off")
    }

    @Test
    fun `a part of the locations fits a plugin message`() {
        val players = List(Message.Locations.PART) { Located(UUID.randomUUID(), "N".repeat(16), "server-".padEnd(16, 'x')) }
        assertTrue(Protocol.seal(secret, Message.Locations(true, players)).size < Protocol.MAX_BYTES)
    }
}
