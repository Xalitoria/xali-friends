import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import mc.xalitoria.friends.paper.data.config.MainConfig
import mc.xalitoria.friends.paper.data.db.DatabaseManager
import mc.xalitoria.friends.paper.data.db.repository.FriendRepository
import mc.xalitoria.friends.paper.data.db.repository.PlayerRepository
import mc.xalitoria.friends.paper.data.db.repository.SettingKey
import mc.xalitoria.friends.paper.data.model.SettingState
import mc.xalitoria.friends.paper.data.model.Settings
import mc.xalitoria.friends.paper.data.db.tables.FriendTable
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import kotlin.uuid.toKotlinUuid
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The friend rules against a real SQLite file. The tables read the prefix once, so one database serves every test. */
class FriendRepository {
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val carol = UUID.randomUUID()

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun players() {
        PlayerRepository.join(alice, "Alice${alice.toString().take(5)}", Settings())
        PlayerRepository.join(bob, "Bob${bob.toString().take(5)}", Settings())
        PlayerRepository.join(carol, "Carol${carol.toString().take(5)}", Settings())
    }

    @Test
    fun `a request is answered by accepting it`() = run {
        players()
        FriendRepository.addRequest(alice, bob)
        assertEquals(listOf(alice), FriendRepository.requests(bob, null).map { it.profile.uuid })
        assertTrue(FriendRepository.accept(bob, alice))
        assertEquals(listOf(bob), FriendRepository.friends(alice).map { it.profile.uuid })
        assertEquals(listOf(alice), FriendRepository.friends(bob).map { it.profile.uuid })
        assertTrue(FriendRepository.friends(alice).single().since.toEpochMilli() > 0)
        assertTrue(FriendRepository.requests(bob, null).isEmpty())
        assertFalse(FriendRepository.accept(bob, alice), "there is nothing left to accept")
    }

    @Test
    fun `a request can be turned down`() = run {
        players()
        FriendRepository.addRequest(alice, bob)
        assertTrue(FriendRepository.deny(bob, alice))
        assertFalse(FriendRepository.deny(bob, alice))
        assertTrue(FriendRepository.friends(alice).isEmpty())
    }

    @Test
    fun `a request older than the cutoff is gone`() = run {
        players()
        FriendRepository.addRequest(alice, bob)
        val future = System.currentTimeMillis() + 5 * 60_000
        assertTrue(FriendRepository.requests(bob, future).isEmpty())
        assertFalse(FriendRepository.accept(bob, alice), "it was removed when it expired")
    }

    @Test
    fun `asking twice keeps one request`() = run {
        players()
        FriendRepository.addRequest(alice, bob)
        FriendRepository.addRequest(alice, bob)
        assertEquals(1, FriendRepository.requests(bob, null).size)
    }

    @Test
    fun `removing a friendship ends it for both sides`() = run {
        players()
        FriendRepository.addRequest(alice, bob)
        FriendRepository.accept(bob, alice)
        assertTrue(FriendRepository.remove(bob, alice))
        assertTrue(FriendRepository.friends(alice).isEmpty())
        assertTrue(FriendRepository.friends(bob).isEmpty())
        assertFalse(FriendRepository.remove(bob, alice))
    }

    @Test
    fun `a favorite is one side's choice, whichever side that is`() = run {
        players()
        FriendRepository.addRequest(alice, bob)
        FriendRepository.accept(bob, alice)
        // Which of them is the low side of the row is by UUID, so both are tried.
        for (side in listOf(alice to bob, bob to alice)) {
            val owner = side.first
            val other = side.second
            assertEquals(true, FriendRepository.toggleFavorite(owner, other))
            assertTrue(FriendRepository.friends(owner).single().favorite)
            assertFalse(FriendRepository.friends(other).single().favorite)
            assertEquals(false, FriendRepository.toggleFavorite(owner, other))
            assertFalse(FriendRepository.friends(owner).single().favorite)
        }
        assertNull(FriendRepository.toggleFavorite(alice, carol), "they aren't friends")
    }

    @Test
    fun `a friendship is one row that either side can read`() = run {
        players()
        FriendRepository.addRequest(alice, bob)
        FriendRepository.accept(bob, alice)
        assertEquals(listOf(bob), FriendRepository.friends(alice).map { it.profile.uuid })
        assertEquals(listOf(alice), FriendRepository.friends(bob).map { it.profile.uuid })
        assertEquals(1, transaction(DatabaseManager.database) { FriendTable.selectAll().where { FriendTable.low inList listOf(alice, bob).map { it.toKotlinUuid() } }.count() }.toInt())
    }

    @Test
    fun `a player joining twice keeps their settings and takes the new name`() = run {
        val first = PlayerRepository.join(carol, "Carol", Settings(msgs = SettingState.ENABLED, invites = false))
        assertEquals(SettingState.ENABLED, first.settings.msgs)
        assertFalse(first.settings.invites)
        PlayerRepository.set(carol, SettingKey.MSGS, state = SettingState.DISABLED)
        PlayerRepository.set(carol, SettingKey.LAST_SEEN, on = false)
        PlayerRepository.setStatus(carol, "building")
        val again = PlayerRepository.join(carol, "CarolNew", Settings())
        assertEquals("CarolNew", again.name)
        assertEquals(SettingState.DISABLED, again.settings.msgs)
        assertFalse(again.settings.showLastSeen)
        assertEquals("building", again.status)
        assertFalse(again.settings.invites, "defaults are only for the first time")
    }

    @Test
    fun `the jump and server settings are stored, and start as the defaults say`() = run {
        val first = PlayerRepository.join(UUID.randomUUID(), "Jumper${alice.toString().take(4)}", Settings(jump = false))
        assertFalse(first.settings.jump)
        assertTrue(first.settings.showServer)
        PlayerRepository.set(first.uuid, SettingKey.JUMP, on = true)
        PlayerRepository.set(first.uuid, SettingKey.SERVER, on = false)
        val read = PlayerRepository.get(first.uuid)!!
        assertTrue(read.settings.jump)
        assertFalse(read.settings.showServer)
    }

    @Test
    fun `players are found by name in any case, or by uuid`() = run {
        PlayerRepository.join(bob, "BobTheBuilder", Settings())
        assertEquals(bob, PlayerRepository.find("bobthebuilder")?.uuid)
        assertEquals(bob, PlayerRepository.find(bob.toString())?.uuid)
        assertNull(PlayerRepository.find("nobody-by-this-name"))
    }

    companion object {
        init {
            val file = Files.createTempFile("xalifriends-test", ".db").also { it.toFile().deleteOnExit() }
            val config = HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:$file"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
            }
            DatabaseManager.attach(HikariDataSource(config), MainConfig().database)
        }
    }
}
