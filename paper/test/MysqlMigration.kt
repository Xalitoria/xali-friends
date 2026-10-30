import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import mc.xalitoria.friends.paper.data.config.MainConfig
import mc.xalitoria.friends.paper.data.db.DatabaseManager
import mc.xalitoria.friends.paper.data.db.legacy.LegacyMigration
import mc.xalitoria.friends.paper.data.db.legacy.LegacyTables
import mc.xalitoria.friends.paper.data.db.repository.FriendRepository
import mc.xalitoria.friends.paper.data.db.repository.PlayerRepository
import mc.xalitoria.friends.paper.data.model.SettingState
import java.sql.DriverManager
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The migration against a real MariaDB, which SQLite can't stand in for (the original's MySQL tables, the column types
 * this plugin's tables get, the foreign keys). It runs only when `XALI_TEST_MYSQL` holds a JDBC URL of a server to use
 * (`jdbc:mariadb://127.0.0.1:3306/?user=root&password=...`); it makes and drops the database `friends_migration` there.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MysqlMigration {
    private val old = UUID.fromString(TestDb.OLD)
    private val friend = UUID.fromString(TestDb.FRIEND)
    private val asker = UUID.fromString(TestDb.ASKER)

    @Test
    @Order(1)
    fun `the original's MySQL data is moved and its tables are dropped`() = runBlocking<Unit> {
        if (BASE == null) return@runBlocking
        assertEquals(
            "0",
            query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'friends_migration' AND table_name IN ('player', 'friend', 'request')"),
        )
        assertEquals("3 players, 3 friendships, 1 requests; 3 rows skipped", REPORT.toString())

        val oldTimer = PlayerRepository.get(old)!!
        assertEquals("OldTimer", oldTimer.name)
        assertEquals(SettingState.ENABLED, oldTimer.settings.msgs)
        assertEquals(false, oldTimer.settings.invites)
        assertEquals(true, oldTimer.settings.notifies)
        assertEquals("away", oldTimer.status)
        assertEquals(true, oldTimer.settings.jump)
        assertEquals(false, PlayerRepository.get(friend)!!.settings.jump)
        assertEquals(false, PlayerRepository.get(asker)!!.settings.showServer)
        // The original kept DATETIMEs in the server's zone; they are UTC now.
        assertEquals(
            LocalDateTime.of(2024, 9, 1, 12, 30, 15).atZone(ZoneId.systemDefault()).toInstant(),
            oldTimer.lastSeen,
        )
        assertEquals(true, FriendRepository.friends(old).single { it.profile.uuid == friend }.favourite)
        assertEquals(false, FriendRepository.friends(friend).single { it.profile.uuid == old }.favourite)
        assertEquals(true, FriendRepository.friends(asker).single { it.profile.uuid == friend }.favourite)
        assertEquals(listOf(asker), FriendRepository.requests(old, null).map { it.profile.uuid })
    }

    @Test
    @Order(2)
    fun `the tables are made compact`() {
        if (BASE == null) return
        fun type(table: String, column: String) = query("SELECT column_type FROM information_schema.columns WHERE table_schema = 'friends_migration' AND table_name = '$table' AND column_name = '$column'")
        assertEquals("binary(16)", type("xalifriends_player", "uuid"))
        assertEquals("binary(16)", type("xalifriends_friend", "low"))
        assertEquals("binary(16)", type("xalifriends_friend", "high"))
        assertEquals("bigint(20)", type("xalifriends_friend", "since"))
        assertEquals("tinyint(4)", type("xalifriends_player", "msgs"))
        assertEquals(
            "2",
            query("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'friends_migration' AND table_name = 'xalifriends_friend' AND column_name IN ('low', 'high') AND seq_in_index = 1"),
        )
    }

    // Last: it deletes a player the others read.
    @Test
    @Order(3)
    fun `removing a player takes their friendships and requests with them`() = runBlocking<Unit> {
        if (BASE == null) return@runBlocking
        assertNotNull(PlayerRepository.get(asker))
        DriverManager.getConnection(BASE.replace("/?", "/friends_migration?")).use { connection ->
            connection.createStatement().use { it.executeUpdate("DELETE FROM xalifriends_player WHERE name = 'OldAsker'") }
        }
        assertNull(PlayerRepository.get(asker))
        assertEquals(listOf(friend), FriendRepository.friends(old).map { it.profile.uuid })
        assertEquals(emptyList(), FriendRepository.requests(old, null))
    }

    companion object {
        val BASE: String? = System.getenv("XALI_TEST_MYSQL")
        var REPORT: LegacyMigration.Report? = null

        private fun url(database: String) = BASE!!.replace("/?", "/$database?")

        private fun query(sql: String): String = DriverManager.getConnection(url("friends_migration")).use { connection ->
            connection.createStatement().use { stmt -> stmt.executeQuery(sql).use { rs -> rs.next(); rs.getString(1) } }
        }

        init {
            if (BASE != null) {
                DriverManager.getConnection(BASE).use { connection ->
                    connection.createStatement().use { stmt ->
                        stmt.executeUpdate("DROP DATABASE IF EXISTS friends_migration")
                        stmt.executeUpdate("CREATE DATABASE friends_migration")
                        stmt.executeUpdate("USE friends_migration")
                        // The statements of FriendSystem's MySQL.java, then rows as its addPlayer, addFriend and addRequest write them.
                        // (Its foreign keys stop a friend or a requester with no player row, so those oddities aren't here.)
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player(UUID VARCHAR(40), name VARCHAR(24), invites BOOLEAN, notifies BOOLEAN, msgs BOOLEAN, jump BOOLEAN, showLastSeen BOOLEAN, showServer BOOLEAN, partyInvites BOOLEAN, lastSeen DATETIME, status VARCHAR(64), PRIMARY KEY(UUID));")
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS friend(UUID VARCHAR(40), friendUUID VARCHAR(40), isFavourite BOOLEAN DEFAULT 0, friendshipStart DATETIME, FOREIGN KEY (friendUUID) REFERENCES friends_migration.player(UUID),PRIMARY KEY (UUID, friendUUID));")
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS request(UUID VARCHAR(40), requestUUID VARCHAR(40), requestDate DATETIME, FOREIGN KEY (requestUUID) REFERENCES player(UUID), PRIMARY KEY (UUID, requestUUID));")
                        TestDb.LEGACY_SQLITE
                            .filter { it.startsWith("INSERT") && !it.contains(TestDb.STRANGER) }
                            .forEach { stmt.executeUpdate(it.replace("INSERT INTO player VALUES", "INSERT INTO player(UUID, name, invites, notifies, msgs, jump, showLastSeen, showServer, partyInvites, lastSeen, status) VALUES")) }
                    }
                }
                val pool = HikariDataSource(HikariConfig().apply {
                    jdbcUrl = url("friends_migration")
                    driverClassName = "org.mariadb.jdbc.Driver"
                    maximumPoolSize = 2
                })
                // The plugin's own tables, made the way the plugin makes them, then the migration.
                DatabaseManager.attach(pool, MainConfig.Database())
                REPORT = LegacyMigration(DatabaseManager.database, DatabaseManager.database, LegacyTables(""), ZoneId.systemDefault()).run()
            }
        }
    }
}
