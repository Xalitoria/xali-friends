package mc.xalitoria.friends.paper.data.db.legacy

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mc.xalitoria.friends.paper.data.config.MainConfig
import mc.xalitoria.friends.paper.data.db.DatabaseManager
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.io.File
import java.time.ZoneId
import java.time.ZoneOffset

/** Runs the [LegacyMigration] as `migration` in the config says, once the plugin's own database is up. */
object LegacyImport {
    /** The counts of what was moved, or null if nothing was (switched off, done before, or no original tables). */
    fun run(config: MainConfig, dataFolder: File, log: (String) -> Unit): LegacyMigration.Report? {
        val migration = config.migration
        if (!migration.enabled) return null
        require(Regex("[A-Za-z0-9_]*").matches(migration.legacyPrefix)) { "migration.legacy-prefix may only contain letters, digits and underscores" }

        val separate = migration.legacySqliteFile.takeIf { it.isNotBlank() }?.let { name ->
            val file = File(name).let { if (it.isAbsolute) it else dataFolder.resolve(name) }
            if (!file.isFile) {
                log("migration.legacy-sqlite-file '$file' doesn't exist; nothing to migrate")
                return null
            }
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:${file.absolutePath}"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
                poolName = "XaliFriends-Legacy"
            })
        }
        try {
            val source = separate?.let { Database.connect(it) } ?: DatabaseManager.database
            val zone = when {
                separate != null || config.database.type.equals("sqlite", ignoreCase = true) -> ZoneOffset.UTC
                migration.legacyTimeZone.isNotBlank() -> ZoneId.of(migration.legacyTimeZone)
                else -> ZoneId.systemDefault()
            }
            return try {
                LegacyMigration(
                    source, DatabaseManager.database, LegacyTables(migration.legacyPrefix), zone,
                    migration.batchSize.coerceAtLeast(1), log,
                ).run()
            } finally {
                if (separate != null) runCatching { TransactionManager.closeAndUnregister(source) }
            }
        } finally {
            separate?.close()
        }
    }
}
