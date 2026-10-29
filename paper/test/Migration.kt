import mc.xalitoria.friends.paper.data.db.legacy.LegacyMigration
import mc.xalitoria.friends.paper.data.db.legacy.LegacyTables
import mc.xalitoria.friends.paper.data.db.repository.Pairing
import mc.xalitoria.friends.paper.data.db.tables.FriendTable
import mc.xalitoria.friends.paper.data.db.tables.MetaTable
import mc.xalitoria.friends.paper.data.db.tables.PlayerTable
import mc.xalitoria.friends.paper.data.db.tables.RequestTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/** The move of the original FriendSystem's data into this plugin's tables, on SQLite databases made for each test. */
class Migration {
    private val old = UUID.fromString(TestDb.OLD)
    private val friend = UUID.fromString(TestDb.FRIEND)
    private val asker = UUID.fromString(TestDb.ASKER)

    /** The original's tables in one database and this plugin's in another, as with `migration.legacy-sqlite-file`. */
    private class Setup(zone: ZoneId = ZoneOffset.UTC, batch: Int = 2) {
        val legacy = TestDb.sqlite().also { TestDb.fillLegacy(it.file) }
        val target = TestDb.sqlite().also { TestDb.createSchema(it.db) }
        val migration = LegacyMigration(legacy.db, target.db, LegacyTables(""), zone, batch)
    }

    private fun players(db: Database) = transaction(db) { PlayerTable.selectAll().associateBy { it[PlayerTable.uuid].toJavaUuid() } }

    /** The friendships as (low, high, favouriteLow, favouriteHigh, since). */
    private fun friendships(db: Database) = transaction(db) {
        FriendTable.selectAll().map {
            listOf(it[FriendTable.low].toJavaUuid(), it[FriendTable.high].toJavaUuid(), it[FriendTable.favouriteLow], it[FriendTable.favouriteHigh], it[FriendTable.since])
        }
    }

    @Test
    fun `the players come across with their settings, times and status`() {
        val setup = Setup()
        setup.migration.run()
        val players = players(setup.target.db)
        assertEquals(setOf(old, friend, asker), players.keys, "the row with a broken UUID is left")

        val oldTimer = players.getValue(old)
        assertEquals("OldTimer", oldTimer[PlayerTable.name])
        assertEquals("oldtimer", oldTimer[PlayerTable.nameLower])
        assertEquals(false, oldTimer[PlayerTable.invites], "stored as 0")
        assertEquals(true, oldTimer[PlayerTable.notifies], "stored as 2, which the original reads as true")
        assertEquals(false, oldTimer[PlayerTable.showLastSeen])
        assertEquals(2, oldTimer[PlayerTable.msgs].toInt())
        assertEquals("away", oldTimer[PlayerTable.status])
        assertEquals(Instant.parse("2024-09-01T12:30:15Z").toEpochMilli(), oldTimer[PlayerTable.lastSeen])

        assertNull(players.getValue(friend)[PlayerTable.status])
        assertNull(players.getValue(asker)[PlayerTable.status], "an empty status is no status")
        assertEquals(0, players.getValue(asker)[PlayerTable.msgs].toInt(), "nobody stays nobody")
    }

    @Test
    fun `two rows become one friendship and each side keeps their own favourite`() {
        val setup = Setup()
        val report = setup.migration.run()!!
        val rows = friendships(setup.target.db).associateBy { it[0] to it[1] }
        assertEquals(3, rows.size)

        // OLD (low) favours FRIEND; FRIEND doesn't favour OLD.
        val first = rows.getValue(old to friend)
        assertEquals(listOf(true, false), first.subList(2, 4))
        assertEquals(Instant.parse("2024-09-02T08:00:00Z").toEpochMilli(), first[4])
        // ASKER (the high side, whose row came second) favours FRIEND.
        assertEquals(listOf(false, true), rows.getValue(friend to asker).subList(2, 4))
        // Only ASKER's row existed: OLD doesn't favour them, ASKER favours OLD, and the time is theirs.
        val third = rows.getValue(old to asker)
        assertEquals(listOf(false, true), third.subList(2, 4))
        assertEquals(Instant.parse("2024-09-04T12:00:00Z").toEpochMilli(), third[4])

        assertEquals("3 players, 3 friendships, 1 requests; 5 rows skipped", report.toString())
    }

    @Test
    fun `every friendship is stored with the lower UUID first`() {
        val setup = Setup()
        setup.migration.run()
        friendships(setup.target.db).forEach { assertTrue(Pairing.compare(it[0] as UUID, it[1] as UUID) < 0) }
    }

    @Test
    fun `requests keep who sent them and when`() {
        val setup = Setup()
        setup.migration.run()
        val rows = transaction(setup.target.db) { RequestTable.selectAll().toList() }
        val request = rows.single()
        assertEquals(old, request[RequestTable.target].toJavaUuid())
        assertEquals(asker, request[RequestTable.requester].toJavaUuid())
        assertEquals(Instant.parse("2024-09-07T09:15:00Z").toEpochMilli(), request[RequestTable.sentAt])
    }

    @Test
    fun `times are turned from the zone the original kept them in into UTC`() {
        val setup = Setup(zone = ZoneId.of("Asia/Tokyo"))
        setup.migration.run()
        // 12:30:15 in Tokyo (UTC+9) is 03:30:15 UTC.
        assertEquals(Instant.parse("2024-09-01T03:30:15Z").toEpochMilli(), players(setup.target.db).getValue(old)[PlayerTable.lastSeen])
    }

    private fun legacyTables(file: java.nio.file.Path): List<String> = java.sql.DriverManager.getConnection("jdbc:sqlite:$file").use { c ->
        c.metaData.getTables(null, null, "%", arrayOf("TABLE")).use { rs -> buildList { while (rs.next()) add(rs.getString("TABLE_NAME")) } }
    }

    @Test
    fun `the original's tables are dropped once the data is across`() {
        val setup = Setup()
        assertEquals(setOf("player", "friend", "request"), legacyTables(setup.legacy.file).toSet())
        setup.migration.run()
        assertEquals(emptyList(), legacyTables(setup.legacy.file))
    }

    @Test
    fun `it is done once, and a second start finds nothing to move`() {
        val setup = Setup()
        assertNotNull(setup.migration.run())
        transaction(setup.target.db) { FriendTable.deleteAll() }
        assertNull(setup.migration.run(), "the original's tables are gone")
        assertEquals(0, friendships(setup.target.db).size, "the removed friendships are not brought back")
        assertTrue(transaction(setup.target.db) { MetaTable.selectAll().any { it[MetaTable.key] == "migration_report" } })
    }

    @Test
    fun `a failed copy leaves the original's tables where they were`() {
        val setup = Setup()
        // The target has no tables, so the copy fails before anything is dropped.
        val bare = TestDb.sqlite()
        assertFailsWith<Throwable> { LegacyMigration(setup.legacy.db, bare.db, LegacyTables(""), ZoneOffset.UTC).run() }
        assertEquals(setOf("player", "friend", "request"), legacyTables(setup.legacy.file).toSet())
    }

    @Test
    fun `a database without the original's tables has nothing to move`() {
        val target = TestDb.sqlite().also { TestDb.createSchema(it.db) }
        val empty = TestDb.sqlite()
        assertNull(LegacyMigration(empty.db, target.db, LegacyTables(""), ZoneOffset.UTC).run())
        assertEquals(0, transaction(target.db) { MetaTable.selectAll().count() }.toInt(), "and nothing is recorded, so a later start can still do it")
    }

    @Test
    fun `a migration that stopped partway is taken over`() {
        val setup = Setup()
        val started = System.currentTimeMillis() - LegacyMigration.STALE - 1000
        transaction(setup.target.db) { MetaTable.insert { it[key] = LegacyMigration.KEY; it[value] = "running:$started" } }
        assertNotNull(setup.migration.run())
        assertEquals(3, players(setup.target.db).size)
    }

    @Test
    fun `the original's tables and this plugin's can't be the same`() {
        val target = TestDb.sqlite().also { TestDb.createSchema(it.db) }
        assertFailsWith<IllegalArgumentException> {
            LegacyMigration(target.db, target.db, LegacyTables("xalifriends_"), ZoneOffset.UTC).run()
        }
    }

    @Test
    fun `the original's tables can be in the same database as this plugin's`() {
        val one = TestDb.sqlite()
        TestDb.fillLegacy(one.file)
        TestDb.createSchema(one.db)
        val report = LegacyMigration(one.db, one.db, LegacyTables(""), ZoneOffset.UTC).run()
        assertNotNull(report)
        assertEquals(3, players(one.db).size)
    }
}
