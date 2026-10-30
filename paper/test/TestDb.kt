import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mc.xalitoria.friends.paper.data.db.tables.FriendTable
import mc.xalitoria.friends.paper.data.db.tables.MetaTable
import mc.xalitoria.friends.paper.data.db.tables.PlayerTable
import mc.xalitoria.friends.paper.data.db.tables.RequestTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

/** Throwaway SQLite databases, and the original FriendSystem's data to put in one. */
object TestDb {
    class Sqlite(val file: Path, val db: Database)

    fun sqlite(): Sqlite {
        val file = Files.createTempFile("xalifriends-test", ".db").also { it.toFile().deleteOnExit() }
        val source = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:$file"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 1
            connectionInitSql = "PRAGMA foreign_keys = ON"
        })
        return Sqlite(file, Database.connect(source))
    }

    /** This plugin's tables in [db]. */
    fun createSchema(db: Database) {
        transaction(db) {
            MigrationUtils.statementsRequiredForDatabaseMigration(PlayerTable, FriendTable, RequestTable, MetaTable, withLogs = false)
                .forEach { exec(it) }
        }
    }

    const val OLD = "11111111-1111-1111-1111-111111111111"
    const val FRIEND = "22222222-2222-2222-2222-222222222222"
    const val ASKER = "33333333-3333-3333-3333-333333333333"
    const val STRANGER = "44444444-4444-4444-4444-444444444444"

    /**
     * The statements FriendSystem's SQLite.java runs, then rows as its addPlayer, addFriend and addRequest write them,
     * and the oddities a real database has: a UUID that isn't one, a friend with no player row, someone their own friend,
     * a friendship with only one of its two rows.
     */
    val LEGACY_SQLITE = listOf(
        "CREATE TABLE IF NOT EXISTS player(UUID TEXT PRIMARY KEY, name TEXT, invites BOOLEAN, notifies BOOLEAN, msgs BOOLEAN, jump BOOLEAN, showLastSeen BOOLEAN, showServer BOOLEAN, partyInvites BOOLEAN, lastSeen DATETIME, status TEXT);",
        "CREATE TABLE IF NOT EXISTS friend(UUID TEXT, friendUUID TEXT, isFavourite BOOLEAN DEFAULT 0, friendshipStart DATETIME DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (UUID, friendUUID), FOREIGN KEY (friendUUID) REFERENCES player(UUID));",
        "CREATE TABLE IF NOT EXISTS request(UUID TEXT, requestUUID TEXT, requestDate DATETIME, PRIMARY KEY (UUID, requestUUID), FOREIGN KEY (requestUUID) REFERENCES player(UUID));",
        "INSERT INTO player VALUES ('$OLD', 'OldTimer', 0, 2, 2, 2, 0, 2, 1, '2024-09-01 12:30:15', 'away')",
        "INSERT INTO player VALUES ('$FRIEND', 'OldFriend', 2, 2, 1, 0, 2, 2, 1, '2024-09-05 00:00:00', NULL)",
        "INSERT INTO player VALUES ('$ASKER', 'OldAsker', 2, 0, 0, 2, 2, 0, 1, '2024-09-06 06:00:00', '')",
        "INSERT INTO player VALUES ('not-a-uuid', 'Broken', 2, 2, 1, 2, 2, 2, 1, '2024-09-01 00:00:00', NULL)",
        // OLD and FRIEND: both rows, only OLD's side a favourite.
        "INSERT INTO friend VALUES ('$OLD', '$FRIEND', 1, '2024-09-02 08:00:00')",
        "INSERT INTO friend VALUES ('$FRIEND', '$OLD', 0, '2024-09-02 08:00:00')",
        // FRIEND and ASKER: both rows, only the higher side (ASKER) a favourite.
        "INSERT INTO friend VALUES ('$FRIEND', '$ASKER', 0, '2024-09-03 10:00:00')",
        "INSERT INTO friend VALUES ('$ASKER', '$FRIEND', 1, '2024-09-03 10:00:00')",
        // ASKER and OLD: only the higher side's row exists.
        "INSERT INTO friend VALUES ('$ASKER', '$OLD', 1, '2024-09-04 12:00:00')",
        // Rows that can't be carried: a friend nobody has a row for, someone their own friend, a broken owner.
        "INSERT INTO friend VALUES ('$OLD', '$STRANGER', 0, '2024-09-04 12:00:00')",
        "INSERT INTO friend VALUES ('$OLD', '$OLD', 0, '2024-09-04 12:00:00')",
        "INSERT INTO friend VALUES ('not-a-uuid', '$OLD', 0, '2024-09-04 12:00:00')",
        "INSERT INTO request VALUES ('$OLD', '$ASKER', '2024-09-07 09:15:00')",
        "INSERT INTO request VALUES ('$FRIEND', '$STRANGER', '2024-09-07 09:15:00')",
    )

    /** Puts the original's tables and rows into the SQLite [file]. */
    fun fillLegacy(file: Path) {
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { stmt -> LEGACY_SQLITE.forEach { stmt.executeUpdate(it) } }
        }
    }
}
