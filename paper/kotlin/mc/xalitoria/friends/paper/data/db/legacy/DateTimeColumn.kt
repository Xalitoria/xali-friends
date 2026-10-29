package mc.xalitoria.friends.paper.data.db.legacy

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * A `DATETIME` the way the original plugin kept it: whole seconds, `yyyy-MM-dd HH:mm:ss`, with no zone. Only for
 * reading the original's tables, to migrate them (the zone they were in is the migration's to know).
 */
class PlainDateTimeColumnType : ColumnType<LocalDateTime>() {
    override fun sqlType(): String = "DATETIME"

    override fun valueFromDB(value: Any): LocalDateTime = when (value) {
        is LocalDateTime -> value
        is java.sql.Timestamp -> value.toLocalDateTime()
        is String -> LocalDateTime.parse(value.trim().replace('T', ' '), READ)
        // SQLite hands back the number when something stored one.
        is Number -> LocalDateTime.ofInstant(Instant.ofEpochMilli(value.toLong()), ZoneOffset.UTC)
        else -> error("Unexpected value of type ${value::class.qualifiedName} for a date and time")
    }

    override fun notNullValueToDB(value: LocalDateTime): Any = WRITE.format(value)

    override fun nonNullValueToString(value: LocalDateTime): String = "'${WRITE.format(value)}'"

    private companion object {
        val WRITE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val READ: DateTimeFormatter = DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .toFormatter()
    }
}

fun Table.dateTime(name: String): Column<LocalDateTime> = registerColumn(name, PlainDateTimeColumnType())
