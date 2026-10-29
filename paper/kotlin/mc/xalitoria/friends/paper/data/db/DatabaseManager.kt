package mc.xalitoria.friends.paper.data.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.XaliFriends.Companion.debug
import mc.xalitoria.friends.paper.data.config.MainConfig
import mc.xalitoria.friends.paper.data.db.tables.FriendTable
import mc.xalitoria.friends.paper.data.db.tables.MetaTable
import mc.xalitoria.friends.paper.data.db.tables.PlayerTable
import mc.xalitoria.friends.paper.data.db.tables.RequestTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

/** HikariCP pool and Exposed [Database] for the plugin's tables (see [mc.xalitoria.friends.paper.data.db.tables]). */
object DatabaseManager {
    lateinit var dataSource: HikariDataSource
        private set
    lateinit var database: Database
        private set

    var tablePrefix = "xalifriends_"
        private set

    private val TABLE_PREFIX = Regex("[A-Za-z0-9_]*")

    fun tableName(name: String): String = tablePrefix + name

    fun connect(plugin: XaliFriends, config: MainConfig.Database) {
        val hikariConfig = HikariConfig()
        val type = Type.valueOf(config.type.uppercase())
        when (type) {
            Type.MARIADB, Type.MYSQL -> {
                val mysql = config.mysql
                // MariaDB has its own driver: MySQL's Connector/J can't read MariaDB's metadata.
                if (type == Type.MARIADB) {
                    hikariConfig.jdbcUrl = "jdbc:mariadb://${mysql.host}:${mysql.port}/${mysql.database}?sslMode=${if (mysql.useSsl) "trust" else "disable"}"
                    hikariConfig.driverClassName = "org.mariadb.jdbc.Driver"
                } else {
                    hikariConfig.jdbcUrl = "jdbc:mysql://${mysql.host}:${mysql.port}/${mysql.database}?useSSL=${mysql.useSsl}"
                    hikariConfig.driverClassName = "com.mysql.cj.jdbc.Driver"
                }
                hikariConfig.username = mysql.username
                hikariConfig.password = mysql.password
                hikariConfig.maximumPoolSize = mysql.poolSize.coerceAtLeast(1)
            }
            Type.SQLITE -> {
                val file = plugin.dataFolder.resolve(config.sqlite.filename)
                file.parentFile.mkdirs()
                hikariConfig.jdbcUrl = "jdbc:sqlite:${file.absolutePath}"
                hikariConfig.driverClassName = "org.sqlite.JDBC"
                // Single-writer: SQLite does not support real concurrent writes.
                hikariConfig.maximumPoolSize = 1
                // SQLite leaves foreign keys (and their cascades) off unless asked, on every connection.
                hikariConfig.connectionInitSql = "PRAGMA foreign_keys = ON"
            }
        }
        hikariConfig.poolName = "XaliFriends-DB"
        attach(HikariDataSource(hikariConfig), config)
    }

    /** Sets up the schema on [source]. The prefix is set first, as table objects read it once. */
    internal fun attach(source: HikariDataSource, config: MainConfig.Database) {
        dataSource = source
        require(TABLE_PREFIX.matches(config.tablePrefix)) { "database.table-prefix may only contain letters, digits and underscores" }
        tablePrefix = config.tablePrefix
        database = Database.connect(source)
        debug("Attaching database: prefix='{0}'", tablePrefix)

        transaction(database) {
            MigrationUtils.statementsRequiredForDatabaseMigration(PlayerTable, FriendTable, RequestTable, MetaTable, withLogs = false)
                .onEach { debug("Schema statement: {0}", it) }
                .forEach { exec(it) }
        }
    }

    fun close() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    /** Runs [statement] in a suspending transaction on [Dispatchers.IO]; nested calls join the outer one. */
    suspend fun <T> dbTransaction(statement: suspend JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db = database, statement = statement) }

    enum class Type {
        SQLITE,
        MARIADB,
        MYSQL,
    }
}
