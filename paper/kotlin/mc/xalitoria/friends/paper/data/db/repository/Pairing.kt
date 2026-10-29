package mc.xalitoria.friends.paper.data.db.repository

import java.util.UUID

/** Which of two players is the `low` one of a friendship row: the one whose UUID comes first, compared as unsigned numbers. */
object Pairing {
    fun compare(a: UUID, b: UUID): Int {
        val high = java.lang.Long.compareUnsigned(a.mostSignificantBits, b.mostSignificantBits)
        return if (high != 0) high else java.lang.Long.compareUnsigned(a.leastSignificantBits, b.leastSignificantBits)
    }

    fun low(a: UUID, b: UUID): UUID = if (compare(a, b) <= 0) a else b

    fun high(a: UUID, b: UUID): UUID = if (compare(a, b) <= 0) b else a

    /** Whether [owner] is the low side of their friendship with [other]. */
    fun isLow(owner: UUID, other: UUID): Boolean = compare(owner, other) < 0
}
