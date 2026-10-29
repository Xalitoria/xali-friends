package mc.xalitoria.friends.paper.service

import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.XaliFriends.Companion.debug
import mc.xalitoria.friends.paper.data.db.repository.FriendRepository
import mc.xalitoria.friends.paper.data.db.repository.PlayerRepository
import mc.xalitoria.friends.paper.data.db.repository.SettingKey
import mc.xalitoria.friends.paper.data.model.Profile
import mc.xalitoria.friends.paper.data.model.Snapshot
import mc.xalitoria.util.message.fail
import mc.xalitoria.util.message.pass
import org.bukkit.entity.Player
import java.util.UUID

/**
 * What the friend commands and the menus' actions do. Every method runs off the main thread, tells [actor] how it went,
 * and tells the other player too when they are on this server. A target is a name or a UUID, as the original plugin took.
 */
class FriendService(private val plugin: XaliFriends) {
    private val msg get() = plugin.messageConfig.data.friend
    private val cache get() = plugin.cache
    private val view get() = plugin.presentation

    suspend fun add(actor: Player, target: String) = withTarget(actor, target) { me, other ->
        val ownSide = cache.get(actor.uniqueId) ?: return@withTarget
        when {
            other.uuid in ownSide.friendIds -> actor.fail(msg.alreadyFriends(other.name))
            ownSide.requests.any { it.profile.uuid == other.uuid } -> actor.fail(msg.alreadyReceived(other.name))
            FriendRepository.requests(other.uuid, plugin.requestCutoff()).any { it.profile.uuid == actor.uniqueId } ->
                actor.fail(msg.alreadyRequested(other.name))
            atLimit(actor, ownSide) -> actor.fail(msg.friendLimit(limitOf(actor) ?: 0))
            !other.settings.invites -> actor.fail(msg.invitesOff(other.name))
            else -> {
                FriendRepository.addRequest(actor.uniqueId, other.uuid)
                reload(actor.uniqueId, other.uuid)
                actor.pass(msg.sent(other.name))
                plugin.server.getPlayer(other.uuid)?.pass(msg.received(me.name))
            }
        }
    }

    suspend fun remove(actor: Player, target: String) = withTarget(actor, target) { me, other ->
        if (FriendRepository.remove(actor.uniqueId, other.uuid)) {
            reload(actor.uniqueId, other.uuid)
            actor.pass(msg.removed(other.name))
            plugin.server.getPlayer(other.uuid)?.pass(msg.removedOther(me.name))
        } else actor.fail(msg.notFriends(other.name))
    }

    suspend fun accept(actor: Player, target: String) = withTarget(actor, target) { me, other ->
        val ownSide = cache.get(actor.uniqueId) ?: return@withTarget
        when {
            ownSide.requests.none { it.profile.uuid == other.uuid } -> actor.fail(msg.noRequest(other.name))
            other.uuid in ownSide.friendIds -> actor.fail(msg.alreadyFriends(other.name))
            atLimit(actor, ownSide) -> actor.fail(msg.friendLimit(limitOf(actor) ?: 0))
            FriendRepository.accept(actor.uniqueId, other.uuid) -> {
                reload(actor.uniqueId, other.uuid)
                actor.pass(msg.accepted(other.name))
                plugin.server.getPlayer(other.uuid)?.pass(msg.acceptedOther(me.name))
            }
            else -> actor.fail(msg.noRequest(other.name))
        }
    }

    suspend fun deny(actor: Player, target: String) = withTarget(actor, target) { me, other ->
        if (FriendRepository.deny(actor.uniqueId, other.uuid)) {
            reload(actor.uniqueId, other.uuid)
            actor.pass(msg.denied(other.name))
            plugin.server.getPlayer(other.uuid)?.fail(msg.deniedOther(me.name))
        } else actor.fail(msg.noRequest(other.name))
    }

    /** `accept all` and `deny all`: answers each request that is waiting. */
    suspend fun answerAll(actor: Player, accept: Boolean) {
        val requests = (cache.get(actor.uniqueId) ?: cache.load(actor.uniqueId))?.requests.orEmpty()
        if (requests.isEmpty()) return actor.fail(msg.noRequests)
        for (request in requests) {
            if (accept) accept(actor, request.profile.uuid.toString()) else deny(actor, request.profile.uuid.toString())
        }
    }

    suspend fun favourite(actor: Player, target: String) = withTarget(actor, target) { _, other ->
        when (FriendRepository.toggleFavourite(actor.uniqueId, other.uuid)) {
            null -> actor.fail(msg.notFriends(other.name))
            true -> { reload(actor.uniqueId); actor.pass(msg.favouriteAdded(other.name)) }
            false -> { reload(actor.uniqueId); actor.pass(msg.favouriteRemoved(other.name)) }
        }
    }

    suspend fun toggle(actor: Player, key: SettingKey) {
        val own = (cache.get(actor.uniqueId) ?: cache.load(actor.uniqueId))?.settings ?: return actor.fail(plugin.messageConfig.data.commands.unexpectedError)
        when (key) {
            SettingKey.INVITES -> {
                PlayerRepository.set(actor.uniqueId, key, on = !own.invites)
                reload(actor.uniqueId)
                actor.pass(msg.toggleInvites(view.toggle(!own.invites)))
            }
            SettingKey.NOTIFIES -> {
                PlayerRepository.set(actor.uniqueId, key, on = !own.notifies)
                reload(actor.uniqueId)
                actor.pass(msg.toggleNotifies(view.toggle(!own.notifies)))
            }
            SettingKey.LAST_SEEN -> {
                PlayerRepository.set(actor.uniqueId, key, on = !own.showLastSeen)
                reload(actor.uniqueId)
                actor.pass(msg.toggleLastSeen(view.toggle(!own.showLastSeen)))
            }
            SettingKey.MSGS -> {
                val next = own.msgs.toggled()
                PlayerRepository.set(actor.uniqueId, key, state = next)
                reload(actor.uniqueId)
                actor.pass(msg.toggleMsgs(view.messages(next)))
            }
        }
        debug("{0} toggled {1}", actor.name, key)
    }

    /** Sets what [actor] is up to; an empty [text] clears it. */
    suspend fun status(actor: Player, text: String) {
        val status = text.trim()
        if (status.length > MAX_STATUS) return actor.fail(msg.statusTooLong(MAX_STATUS))
        PlayerRepository.setStatus(actor.uniqueId, status)
        reload(actor.uniqueId)
        if (status.isEmpty()) actor.pass(msg.statusRemoved) else actor.pass(msg.statusUpdated(status))
    }

    /** The most friends [player] may have, or null for no limit. */
    fun limitOf(player: Player): Int? {
        val limit = plugin.config.data.friends.limit
        if (!limit.enabled) return null
        return limit.groups.filterKeys { it == limit.defaultGroup || player.hasPermission("xalifriends.group.$it") }.values.maxOrNull()
    }

    private fun atLimit(player: Player, own: Snapshot): Boolean = limitOf(player)?.let { own.friends.size >= it } == true

    /** Finds [target], answering the actor if there is none or it is themself, then runs [block] with their own profile and the target's. */
    private suspend fun withTarget(actor: Player, target: String, block: suspend (Profile, Profile) -> Unit) {
        val own = (cache.get(actor.uniqueId) ?: cache.load(actor.uniqueId))?.profile
            ?: return actor.fail(plugin.messageConfig.data.commands.unexpectedError)
        val other = PlayerRepository.find(target) ?: return actor.fail(msg.targetInvalid(target))
        if (other.uuid == own.uuid) return actor.fail(msg.self)
        block(own, other)
    }

    /** Reads the data of each of [players] again, those that are online on this server. */
    private suspend fun reload(vararg players: UUID) {
        for (uuid in players) if (cache.get(uuid) != null) cache.load(uuid)
    }

    companion object {
        const val MAX_STATUS = 64
    }
}
