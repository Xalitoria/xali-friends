import mc.xalitoria.friends.paper.data.db.tables.FriendTable
import mc.xalitoria.friends.paper.data.db.tables.MetaTable
import mc.xalitoria.friends.paper.data.db.tables.PlayerTable
import mc.xalitoria.friends.paper.data.db.tables.RequestTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

/** A database made by an earlier version of this plugin gains the columns it lacks, and keeps its rows. */
class Upgrade {
    @Test
    fun `a player table from before jump and show-server gains them, switched on`() {
        val db = TestDb.sqlite()
        DriverManager.getConnection("jdbc:sqlite:${db.file}").use { connection ->
            connection.createStatement().use { stmt ->
                stmt.executeUpdate(
                    "CREATE TABLE xalifriends_player(uuid BLOB NOT NULL PRIMARY KEY, name VARCHAR(24) NOT NULL, name_lower VARCHAR(24) NOT NULL, " +
                        "invites BOOLEAN DEFAULT 1 NOT NULL, notifies BOOLEAN DEFAULT 1 NOT NULL, show_last_seen BOOLEAN DEFAULT 1 NOT NULL, " +
                        "msgs TINYINT DEFAULT 1 NOT NULL, last_seen BIGINT NULL, status VARCHAR(64) NULL)",
                )
                stmt.executeUpdate("INSERT INTO xalifriends_player(uuid, name, name_lower, invites) VALUES (x'11111111111111111111111111111111', 'Old', 'old', 0)")
            }
        }
        transaction(db.db) {
            MigrationUtils.statementsRequiredForDatabaseMigration(PlayerTable, FriendTable, RequestTable, MetaTable, withLogs = false).forEach { exec(it) }
        }
        val row = transaction(db.db) { PlayerTable.selectAll().single() }
        assertEquals("Old", row[PlayerTable.name])
        assertEquals(false, row[PlayerTable.invites], "what was there is kept")
        assertEquals(true, row[PlayerTable.jump])
        assertEquals(true, row[PlayerTable.showServer])
    }
}
