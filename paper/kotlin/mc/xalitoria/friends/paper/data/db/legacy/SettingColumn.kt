package mc.xalitoria.friends.paper.data.db.legacy

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.api.RowApi

/**
 * A number in a column the original plugin declared `BOOLEAN`: it keeps 0, 1 or 2 there (off, friends only, on), which
 * a `bool` column would flatten to two states. MySQL and MariaDB treat BOOLEAN as TINYINT(1), which holds them. Only
 * for reading the original's tables, to migrate them.
 */
class SettingColumnType : ColumnType<Int>() {
    override fun sqlType(): String = "BOOLEAN"

    /** Asks for a number: MariaDB's driver otherwise answers a TINYINT(1) with true or false, and the 2 is lost. */
    override fun readObject(rs: RowApi, index: Int): Any? = rs.getObject(index, Int::class.javaObjectType)

    override fun valueFromDB(value: Any): Int = when (value) {
        is Number -> value.toInt()
        is Boolean -> if (value) 1 else 0
        is String -> value.toIntOrNull() ?: 0
        else -> error("Unexpected value of type ${value::class.qualifiedName} for a setting")
    }

    override fun nonNullValueToString(value: Int): String = value.toString()
}

fun Table.setting(name: String): Column<Int?> = registerColumn<Int>(name, SettingColumnType()).nullable()
