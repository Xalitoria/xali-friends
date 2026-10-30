package mc.xalitoria.friends.velocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import com.velocitypowered.api.proxy.server.RegisteredServer
import mc.xalitoria.friends.protocol.Located
import mc.xalitoria.friends.protocol.Message
import mc.xalitoria.friends.protocol.Message.*
import mc.xalitoria.friends.protocol.Protocol
import org.slf4j.Logger
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

/**
 * The proxy end of XaliFriends. The servers hold the friendships; the proxy is what sees a player arrive on, leave and
 * move between servers, so it tells every server who is where, moves a player to a friend's server when a server asks,
 * and passes a request, an answer or a removal on to the server the other player is on.
 *
 * Messages are signed with the forwarding secret from velocity.toml and anything else is dropped. The proxy trusts
 * the servers to have checked that two players are friends, and checks only that the player a message is about is
 * on the server that sent it.
 */
class XaliFriends(
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val dataDirectory: Path,
    /** The loader Velocity knows as the plugin, which listeners are registered with. */
    private val plugin: Any,
) : Runnable, AutoCloseable {
    private val channel = MinecraftChannelIdentifier.from(Protocol.CHANNEL)

    /** Who has been announced as arrived, so that a connection that never got onto a server isn't announced as gone. */
    private val present = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * The forwarding secret, found the way Velocity finds it: `VELOCITY_FORWARDING_SECRET`, else the file named by
     * `forwarding-secret-file` in velocity.toml (`forwarding.secret` when unset). The API doesn't expose it.
     */
    private var secret: String? = null

    override fun run() {
        dataDirectory.createDirectories()
        val root = dataDirectory.toAbsolutePath().parent.parent // plugins/<id> sits under the folder holding velocity.toml
        secret = runCatching { System.getenv("VELOCITY_FORWARDING_SECRET") }.getOrNull()
        if (secret == null) secret = try {
            val file = secretFileRegex
                .find(root.resolve("velocity.toml").readText())
                ?.groupValues?.getOrNull(1)
                ?: "forwarding.secret"
            root.resolve(file).readText().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            logger.error("Could not read the forwarding secret", e)
            null
        }
        if (secret == null) logger.error("Could not read the forwarding secret, so no message will be relayed")

        proxy.channelRegistrar.register(channel)
        proxy.eventManager.register(plugin, this)
        logger.info("XaliFriends relay ready on {}", Protocol.CHANNEL)
    }

    override fun close() {
        proxy.channelRegistrar.unregister(channel)
    }

    @Subscribe
    fun onServerPostConnect(event: ServerPostConnectEvent) {
        val player = event.player
        val server = player.currentServer.map { it.server }.orElse(null) ?: return
        val who = locate(player) ?: return
        // The server that now has a player to carry it learns where everyone is; every server learns of the change.
        deliver(server, snapshot())
        val first = present.add(player.uniqueId)
        broadcast(if (first) Joined(who) else Moved(who))
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        val player = event.player
        if (!present.remove(player.uniqueId)) return
        broadcast(Left(player.uniqueId, player.username))
    }

    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent) {
        if (event.identifier != channel) return
        // A client can send on any channel, and what it sends here would be taken as the proxy's own by the server
        // behind it, so nothing a player sends here goes anywhere. The secret would stop a forgery anyway.
        event.result = PluginMessageEvent.ForwardResult.handled()
        val origin = (event.source as? ServerConnection)?.server ?: return

        when (val message = Protocol.open(secret, event.data)) {
            is Jump -> jump(origin, message)
            is Notify -> notify(origin, message)
            null -> logger.warn("Dropped a message from {} with a missing or wrong signature", origin.serverInfo.name)
            else -> logger.warn("Dropped a {} from {}: only the proxy sends those", message::class.simpleName, origin.serverInfo.name)
        }
    }

    /** Sends the player to the server their friend is on. */
    private fun jump(origin: RegisteredServer, message: Jump) {
        val player = proxy.getPlayer(message.player).orElse(null)
        if (player == null || !isOn(player, origin)) return
        val target = proxy.getPlayer(message.target).orElse(null)
        val server = target?.currentServer?.map { it.server }?.orElse(null)
        when {
            server == null -> deliver(origin, JumpResult(message.player, JumpResult.Status.OFFLINE, ""))
            server == origin -> deliver(origin, JumpResult(message.player, JumpResult.Status.SAME_SERVER, server.serverInfo.name))
            else -> {
                deliver(origin, JumpResult(message.player, JumpResult.Status.SENT, server.serverInfo.name))
                player.createConnectionRequest(server).connect().thenAccept { result ->
                    if (!result.isSuccessful) {
                        logger.info("{} could not be sent to {}: {}", player.username, server.serverInfo.name, result.status)
                        // They are still where they were, so that server can tell them.
                        if (isOn(player, origin)) deliver(origin, JumpResult(message.player, JumpResult.Status.UNAVAILABLE, server.serverInfo.name))
                    }
                }
            }
        }
    }

    /** Passes what happened between two players to the server the target is on. */
    private fun notify(origin: RegisteredServer, message: Notify) {
        val from = proxy.getPlayer(message.from).orElse(null)
        if (from == null || !isOn(from, origin)) return
        val server = proxy.getPlayer(message.target).orElse(null)?.currentServer?.map { it.server }?.orElse(null) ?: return
        if (server != origin) deliver(server, message)
    }

    private fun isOn(player: Player, server: RegisteredServer): Boolean =
        player.currentServer.map { it.server == server }.orElse(false)

    private fun locate(player: Player): Located? =
        player.currentServer.map { Located(player.uniqueId, player.username, it.serverInfo.name) }.orElse(null)

    /** Everyone who is on a server, as parts that each fit a plugin message: the first replaces what a server knew. */
    private fun snapshot(): List<Message> {
        val everyone = proxy.allPlayers.mapNotNull(::locate)
        val parts = everyone.chunked(Locations.PART).ifEmpty { listOf(emptyList()) }
        return parts.mapIndexed { index, players -> Locations(index == 0, players) }
    }

    /** Tells every server that has someone on it to carry [message] to its plugin. */
    private fun broadcast(message: Message) {
        proxy.allServers.filter { it.playersConnected.isNotEmpty() }.forEach { deliver(it, message) }
    }

    private fun deliver(target: RegisteredServer, messages: List<Message>) = messages.forEach { deliver(target, it) }

    /** Sends [message] to [target]; false when it can't be signed or nobody is on [target] to carry it. */
    private fun deliver(target: RegisteredServer, message: Message): Boolean {
        val secret = secret ?: return false
        return target.sendPluginMessage(channel, Protocol.seal(secret, message))
    }

    companion object {
        private val secretFileRegex = Regex("""(?m)^\s*forwarding-secret-file\s*=\s*"([^"]*)"""")
    }
}
