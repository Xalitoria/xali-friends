package com.xalitoria.friends.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/** A player on the proxy: who, and the name the proxy knows the server they are on by. */
class Located(val uuid: UUID, val name: String, val server: String)

/**
 * What the servers and the proxy tell each other. [encode] writes the subchannel as a `writeUTF` string followed by
 * the fields below; UUIDs are two longs, strings are `writeUTF`.
 */
sealed interface Message {
    fun encode(): ByteArray

    /**
     * `LOCATIONS`: where players are, from the proxy. [replace] starts a new picture, which the following ones
     * (only [replace] false) add to; a picture is cut into parts so that none outgrows a plugin message.
     */
    class Locations(val replace: Boolean, val players: List<Located>) : Message {
        override fun encode() = write("LOCATIONS") { writeBoolean(replace); writeInt(players.size); players.forEach { writeLocated(it) } }

        companion object {
            /** About how many players one part takes (`Located` is 16 + 18 + 18 bytes at most, usually less). */
            const val PART = 400
        }
    }

    /** `JOINED`: [who] came onto the proxy. */
    class Joined(val who: Located) : Message {
        override fun encode() = write("JOINED") { writeLocated(who) }
    }

    /** `MOVED`: [who] changed server; [who] holds the new one. */
    class Moved(val who: Located) : Message {
        override fun encode() = write("MOVED") { writeLocated(who) }
    }

    /** `LEFT`: the player [uuid], called [name], left the proxy. */
    class Left(val uuid: UUID, val name: String) : Message {
        override fun encode() = write("LEFT") { writeUuid(uuid); writeUTF(name) }
    }

    /** `JUMP`: the player [player] wants to go to the server [target] is on. The server has checked that they are friends. */
    class Jump(val player: UUID, val target: UUID) : Message {
        override fun encode() = write("JUMP") { writeUuid(player); writeUuid(target) }
    }

    /** `JUMP_RESULT`: what became of the [Jump] of [player]; [server] is where they are being sent, for [Status.SENT]. */
    class JumpResult(val player: UUID, val status: Status, val server: String) : Message {
        /** The wire carries the ordinal. */
        enum class Status { SENT, OFFLINE, SAME_SERVER, UNAVAILABLE }

        override fun encode() = write("JUMP_RESULT") { writeUuid(player); writeByte(status.ordinal); writeUTF(server) }
    }

    /**
     * `NOTIFY`: something happened between [from] ([fromName]) and [target], who is on another server: the proxy passes
     * it to the server [target] is on, which reloads their friends and tells them.
     */
    class Notify(val kind: Kind, val target: UUID, val from: UUID, val fromName: String) : Message {
        /** The wire carries the ordinal. */
        enum class Kind { REQUESTED, ACCEPTED, DENIED, REMOVED }

        override fun encode() = write("NOTIFY") { writeByte(kind.ordinal); writeUuid(target); writeUuid(from); writeUTF(fromName) }
    }

    companion object {
        /** Reads a message written by [encode]; null for a subchannel this version doesn't know. */
        fun decode(bytes: ByteArray): Message? = DataInputStream(ByteArrayInputStream(bytes)).run {
            when (readUTF()) {
                "LOCATIONS" -> Locations(readBoolean(), List(readInt().also { require(it in 0..10_000) }) { readLocated() })
                "JOINED" -> Joined(readLocated())
                "MOVED" -> Moved(readLocated())
                "LEFT" -> Left(readUuid(), readUTF())
                "JUMP" -> Jump(readUuid(), readUuid())
                "JUMP_RESULT" -> JumpResult(readUuid(), JumpResult.Status.entries[readByte().toInt()], readUTF())
                "NOTIFY" -> Notify(Notify.Kind.entries[readByte().toInt()], readUuid(), readUuid(), readUTF())
                else -> null
            }
        }

        private fun write(subchannel: String, body: DataOutputStream.() -> Unit): ByteArray =
            ByteArrayOutputStream().also { DataOutputStream(it).apply { writeUTF(subchannel); body() } }.toByteArray()

        private fun DataOutputStream.writeUuid(uuid: UUID) { writeLong(uuid.mostSignificantBits); writeLong(uuid.leastSignificantBits) }

        private fun DataOutputStream.writeLocated(who: Located) { writeUuid(who.uuid); writeUTF(who.name); writeUTF(who.server) }

        private fun DataInputStream.readUuid() = UUID(readLong(), readLong())

        private fun DataInputStream.readLocated() = Located(readUuid(), readUTF(), readUTF())
    }
}
