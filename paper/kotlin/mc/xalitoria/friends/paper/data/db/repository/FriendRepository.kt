package mc.xalitoria.friends.paper.data.db.repository

import mc.xalitoria.friends.paper.data.db.DatabaseManager.dbTransaction
import mc.xalitoria.friends.paper.data.db.repository.PlayerRepository.toProfile
import mc.xalitoria.friends.paper.data.db.tables.FriendTable
import mc.xalitoria.friends.paper.data.db.tables.PlayerTable
import mc.xalitoria.friends.paper.data.db.tables.RequestTable
import mc.xalitoria.friends.paper.data.model.FriendEntry
import mc.xalitoria.friends.paper.data.model.RequestEntry
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import kotlin.uuid.toKotlinUuid

/**
 * Friendships and requests. A friendship is one row, read from either side (see [FriendTable]); a request is a row held
 * by the player it was sent to. The checks (who may ask whom) are the service's; this only stores.
 */
object FriendRepository {
    suspend fun friends(owner: UUID): List<FriendEntry> =
        dbTransaction {
            val id = owner.toKotlinUuid()
            // The owner is the low side: the friend is the high one, and the favorite the low side's.
            val asLow = FriendTable.join(PlayerTable, JoinType.INNER, FriendTable.high, PlayerTable.uuid)
                .selectAll()
                .where { FriendTable.low eq id }
                .map { FriendEntry(it.toProfile(), it[FriendTable.favoriteLow], Instant.ofEpochMilli(it[FriendTable.since])) }
            val asHigh = FriendTable.join(PlayerTable, JoinType.INNER, FriendTable.low, PlayerTable.uuid)
                .selectAll()
                .where { FriendTable.high eq id }
                .map { FriendEntry(it.toProfile(), it[FriendTable.favoriteHigh], Instant.ofEpochMilli(it[FriendTable.since])) }
            asLow + asHigh
        }

    /** The requests [target] holds. Those sent before [expiredBefore] (milliseconds) are taken away first. */
    suspend fun requests(target: UUID, expiredBefore: Long?): List<RequestEntry> =
        dbTransaction {
            val id = target.toKotlinUuid()
            if (expiredBefore != null) {
                RequestTable.deleteWhere { (RequestTable.target eq id) and (RequestTable.sentAt less expiredBefore) }
            }
            RequestTable.join(PlayerTable, JoinType.INNER, RequestTable.requester, PlayerTable.uuid)
                .selectAll()
                .where { RequestTable.target eq id }
                .map { RequestEntry(it.toProfile(), Instant.ofEpochMilli(it[RequestTable.sentAt])) }
                .sortedBy { it.date }
        }

    suspend fun addRequest(requester: UUID, target: UUID) {
        dbTransaction {
            RequestTable.insertIgnore {
                it[RequestTable.target] = target.toKotlinUuid()
                it[RequestTable.requester] = requester.toKotlinUuid()
                it[sentAt] = System.currentTimeMillis()
            }
        }
    }

    /** Whether [target] held a request from [requester] to answer, and makes them friends if so. */
    suspend fun accept(target: UUID, requester: UUID): Boolean =
        dbTransaction {
            if (deleteRequest(target, requester) == 0) return@dbTransaction false
            FriendTable.insertIgnore {
                it[low] = Pairing.low(target, requester).toKotlinUuid()
                it[high] = Pairing.high(target, requester).toKotlinUuid()
                it[since] = System.currentTimeMillis()
            }
            true
        }

    /** Whether there was a request to turn down. */
    suspend fun deny(target: UUID, requester: UUID): Boolean =
        dbTransaction { deleteRequest(target, requester) > 0 }

    /** Whether they were friends. */
    suspend fun remove(a: UUID, b: UUID): Boolean =
        dbTransaction {
            FriendTable.deleteWhere {
                (FriendTable.low eq Pairing.low(a, b).toKotlinUuid()) and (FriendTable.high eq Pairing.high(a, b).toKotlinUuid())
            } > 0
        }

    /** Flips [friend] as one of [owner]'s favorites and says what it is now; null if they aren't friends. */
    suspend fun toggleFavorite(owner: UUID, friend: UUID): Boolean? =
        dbTransaction {
            val low = Pairing.low(owner, friend).toKotlinUuid()
            val high = Pairing.high(owner, friend).toKotlinUuid()
            val row = FriendTable.selectAll().where { (FriendTable.low eq low) and (FriendTable.high eq high) }.singleOrNull()
                ?: return@dbTransaction null
            val ownerIsLow = Pairing.isLow(owner, friend)
            val now = !(if (ownerIsLow) row[FriendTable.favoriteLow] else row[FriendTable.favoriteHigh])
            FriendTable.update({ (FriendTable.low eq low) and (FriendTable.high eq high) }) {
                if (ownerIsLow) it[favoriteLow] = now else it[favoriteHigh] = now
            }
            now
        }

    private fun deleteRequest(target: UUID, requester: UUID): Int =
        RequestTable.deleteWhere {
            (RequestTable.target eq target.toKotlinUuid()) and (RequestTable.requester eq requester.toKotlinUuid())
        }
}
