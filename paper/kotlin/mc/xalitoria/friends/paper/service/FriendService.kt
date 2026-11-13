package mc.xalitoria.friends.paper.service

import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.XaliFriends.Companion.debug
import mc.xalitoria.friends.paper.data.db.repository.FriendRepository
import mc.xalitoria.friends.paper.data.db.repository.PlayerRepository
import mc.xalitoria.friends.paper.data.db.repository.SettingKey
import mc.xalitoria.friends.paper.data.model.Profile
import mc.xalitoria.friends.paper.data.model.Snapshot
import mc.xalitoria.friends.paper.data.constants.Permissions
import com.xalitoria.friends.protocol.Message.Notify
import com.xalitoria.util.message.fail
import com.xalitoria.util.message.info
import com.xalitoria.util.message.pass
import com.xalitoria.util.message.send
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
                tell(Notify.Kind.REQUESTED, other.uuid, me)
            }
        }
    }

    suspend fun remove(actor: Player, target: String) = withTarget(actor, target) { me, other ->
        if (FriendRepository.remove(actor.uniqueId, other.uuid)) {
            reload(actor.uniqueId, other.uuid)
            actor.pass(msg.removed(other.name))
            tell(Notify.Kind.REMOVED, other.uuid, me)
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
                tell(Notify.Kind.ACCEPTED, other.uuid, me)
            }
            else -> actor.fail(msg.noRequest(other.name))
        }
    }

    suspend fun deny(actor: Player, target: String) = withTarget(actor, target) { me, other ->
        if (FriendRepository.deny(actor.uniqueId, other.uuid)) {
            reload(actor.uniqueId, other.uuid)
            actor.pass(msg.denied(other.name))
            tell(Notify.Kind.DENIED, other.uuid, me)
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

    suspend fun favorite(actor: Player, target: String) = withTarget(actor, target) { _, other ->
        when (FriendRepository.toggleFavorite(actor.uniqueId, other.uuid)) {
            null -> actor.fail(msg.notFriends(other.name))
            true -> { reload(actor.uniqueId); actor.pass(msg.favoriteAdded(other.name)) }
            false -> { reload(actor.uniqueId); actor.pass(msg.favoriteRemoved(other.name)) }
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
            SettingKey.JUMP -> {
                PlayerRepository.set(actor.uniqueId, key, on = !own.jump)
                reload(actor.uniqueId)
                actor.pass(msg.toggleJump(view.toggle(!own.jump)))
            }
            SettingKey.SERVER -> {
                PlayerRepository.set(actor.uniqueId, key, on = !own.showServer)
                reload(actor.uniqueId)
                actor.pass(msg.toggleServer(view.toggle(!own.showServer)))
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

    /** Takes [actor] to the server their friend [target] is on, with the proxy plugin. */
    suspend fun jump(actor: Player, target: String) {
        val link = plugin.link
        if (link == null || !plugin.config.data.proxy.jump) return actor.fail(msg.jumpUnavailable)
        val own = cache.get(actor.uniqueId) ?: cache.load(actor.uniqueId) ?: return actor.fail(plugin.messageConfig.data.commands.unexpectedError)
        val friend = own.friends.firstOrNull { it.profile.uuid.toString() == target || it.profile.name.equals(target, ignoreCase = true) }
            ?: return actor.fail(msg.notFriends(target))
        val name = friend.profile.name
        val server = link.serverOf(friend.profile.uuid)
        when {
            server == null -> actor.fail(msg.jumpOffline(name))
            !friend.profile.settings.jump -> actor.fail(msg.jumpDenied(name))
            server == link.serverOf(actor.uniqueId) -> actor.info(msg.jumpSame(name))
            else -> link.jump(actor, friend.profile.uuid, name)
        }
    }

    /** Whether [actor] may jump to [friend] now: the proxy, the setting, the permission, and the friend being somewhere else. */
    fun canJump(actor: Player, friend: Profile): Boolean {
        val link = plugin.link ?: return false
        if (!plugin.config.data.proxy.jump || !friend.settings.jump || !actor.hasPermission(Permissions.JUMP)) return false
        val server = link.serverOf(friend.uuid) ?: return false
        return server != link.serverOf(actor.uniqueId)
    }

    /** Tells the players on this server who are friends with [uuid] that they [joined] the network (or the server, with no proxy plugin) or left it. */
    fun announce(uuid: UUID, name: String, joined: Boolean) {
        for (online in plugin.server.onlinePlayers) {
            if (online.uniqueId == uuid) continue
            val snapshot = cache.get(online.uniqueId) ?: continue
            if (uuid !in snapshot.friendIds || !snapshot.settings.notifies) continue
            online send (if (joined) plugin.messageConfig.data.notify.join(name) else plugin.messageConfig.data.notify.leave(name))
        }
    }

    /** What the other player is told, here when they are on this server and through the proxy when they are elsewhere. */
    private fun tell(kind: Notify.Kind, target: UUID, from: Profile) {
        val local = plugin.server.getPlayer(target)
        if (local != null) show(local, kind, from.name)
        else plugin.link?.takeIf { it.isOnline(target) }?.notify(kind, target, from.uuid, from.name)
    }

    /** A [Notify] from the proxy: [target], if they are here, has their friends read again and is told. */
    suspend fun receive(kind: Notify.Kind, target: UUID, fromName: String) {
        val player = plugin.server.getPlayer(target) ?: return
        cache.load(target)
        show(player, kind, fromName)
    }

    private fun show(player: Player, kind: Notify.Kind, name: String) = when (kind) {
        Notify.Kind.REQUESTED -> player.pass(msg.received(name))
        Notify.Kind.ACCEPTED -> player.pass(msg.acceptedOther(name))
        Notify.Kind.DENIED -> player.fail(msg.deniedOther(name))
        Notify.Kind.REMOVED -> player.pass(msg.removedOther(name))
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
