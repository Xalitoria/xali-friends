package mc.xalitoria.friends.paper.proxy

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.XaliFriends.Companion.debug
import mc.xalitoria.friends.protocol.Located
import mc.xalitoria.friends.protocol.Message
import mc.xalitoria.friends.protocol.Protocol
import mc.xalitoria.util.message.fail
import mc.xalitoria.util.message.info
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.readText

/**
 * This server's end of the proxy relay (the Velocity plugin): it learns where every player on the network is, from the
 * proxy, and asks the proxy to move a player to a friend or to tell a player on another server something.
 *
 * Messages are signed with Velocity's forwarding secret, which Paper keeps in `config/paper-global.yml` (`proxies.velocity`).
 * Without it there is no link, and the plugin works from what this server alone knows.
 */
class ProxyLink(private val plugin: XaliFriends, val secret: String) : PluginMessageListener {
    private val located = ConcurrentHashMap<UUID, Located>()

    /** Who asked to jump to whom, until the proxy answers: the answer only knows the player. */
    private val jumping = ConcurrentHashMap<UUID, String>()

    /** When each player was last announced as arriving on the network, so a server switch isn't taken for an arrival. */
    private val arrivals = ConcurrentHashMap<UUID, Long>()

    /** Whether [uuid] arrived on the network just now (once: it answers true for the first ask only). */
    fun consumeArrival(uuid: UUID): Boolean {
        val now = System.currentTimeMillis()
        arrivals.values.removeIf { now - it > ARRIVAL_MILLIS }
        return arrivals.remove(uuid) != null
    }

    fun start() {
        plugin.server.messenger.registerOutgoingPluginChannel(plugin, Protocol.CHANNEL)
        plugin.server.messenger.registerIncomingPluginChannel(plugin, Protocol.CHANNEL, this)
    }

    fun stop() {
        plugin.server.messenger.unregisterIncomingPluginChannel(plugin, Protocol.CHANNEL, this)
        plugin.server.messenger.unregisterOutgoingPluginChannel(plugin, Protocol.CHANNEL)
    }

    /** The server [uuid] is on, as the proxy names it; null when they are not on the network, or it hasn't said yet. */
    fun serverOf(uuid: UUID): String? = located[uuid]?.server

    fun isOnline(uuid: UUID): Boolean = located.containsKey(uuid)

    /** [server] as the players should read it: `proxy.server-names` may give it another name. */
    fun displayName(server: String): String = plugin.config.data.proxy.serverNames[server] ?: server

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != Protocol.CHANNEL) return
        // Anything a client can send to a plugin channel reaches here too, so only a signed message is acted on.
        val opened = runCatching { Protocol.open(secret, message) }.getOrNull()
        if (opened == null) {
            debug("Ignored a proxy message with a missing or wrong signature")
            return
        }
        try {
            handle(opened)
        } catch (e: Exception) {
            plugin.logger.fail("Failed to handle ${opened::class.simpleName} from the proxy", e)
        }
    }

    private fun handle(message: Message) {
        when (message) {
            is Message.Locations -> {
                if (message.replace) located.clear()
                message.players.forEach { located[it.uuid] = it }
                debug("Proxy locations: {0} players", located.size)
            }
            is Message.Joined -> {
                located[message.who.uuid] = message.who
                arrivals[message.who.uuid] = System.currentTimeMillis()
                plugin.service.announce(message.who.uuid, message.who.name, joined = true)
            }
            is Message.Moved -> located[message.who.uuid] = message.who
            is Message.Left -> {
                located.remove(message.uuid)
                plugin.service.announce(message.uuid, message.name, joined = false)
            }
            is Message.JumpResult -> jumped(message)
            is Message.Notify -> plugin.scope.launch { plugin.service.receive(message.kind, message.target, message.fromName) }
            is Message.Jump -> debug("Ignored a Jump: only the proxy receives those")
        }
    }

    private fun jumped(result: Message.JumpResult) {
        val player = plugin.server.getPlayer(result.player) ?: return
        val friend = jumping.remove(result.player) ?: return
        val msg = plugin.messageConfig.data.friend
        when (result.status) {
            Message.JumpResult.Status.SENT -> player info msg.jumping(friend, displayName(result.server))
            Message.JumpResult.Status.OFFLINE -> player fail msg.jumpOffline(friend)
            Message.JumpResult.Status.SAME_SERVER -> player info msg.jumpSame(friend)
            Message.JumpResult.Status.UNAVAILABLE -> player fail msg.jumpFailed(friend)
        }
    }

    /** Asks the proxy to send [player] to the server [friend] is on. */
    fun jump(player: Player, friend: UUID, friendName: String) {
        jumping[player.uniqueId] = friendName
        send(player, Message.Jump(player.uniqueId, friend))
    }

    /** Tells the proxy to pass on to the server [target] is on that [fromName] (who is on this one) did [kind] to them. */
    fun notify(kind: Message.Notify.Kind, target: UUID, from: UUID, fromName: String) {
        val carrier = plugin.server.getPlayer(from) ?: plugin.server.onlinePlayers.firstOrNull() ?: return
        send(carrier, Message.Notify(kind, target, from, fromName))
    }

    /** Signs [message] and sends it to the proxy by way of [carrier], who has to be on this server. */
    private fun send(carrier: Player, message: Message) {
        carrier.sendPluginMessage(plugin, Protocol.CHANNEL, Protocol.seal(secret, message))
    }

    companion object {
        /** How long an arrival is remembered for the join summary, which is drawn a couple of seconds after it. */
        private const val ARRIVAL_MILLIS = 30_000L

        /**
         * Velocity's forwarding secret from Paper's own `config/paper-global.yml`, the one it shares with the proxy;
         * empty unless Velocity forwarding is enabled. Paper only reads it at startup, so this does too.
         */
        fun readSecret(plugin: XaliFriends): String = try {
            Yaml(configuration = YamlConfiguration(strictMode = false))
                .decodeFromString(PaperGlobal.serializer(), plugin.server.pluginsFolder.toPath().resolveSibling("config/paper-global.yml").readText())
                .proxies.velocity.takeIf { it.enabled }?.secret.orEmpty()
        } catch (e: Exception) {
            debug("Could not read the Velocity secret from paper-global.yml: {0}", e)
            ""
        }

        @Serializable
        private class PaperGlobal(val proxies: Proxies = Proxies()) {
            @Serializable
            class Proxies(val velocity: Velocity = Velocity())

            @Serializable
            class Velocity(val enabled: Boolean = false, val secret: String = "")
        }
    }
}
