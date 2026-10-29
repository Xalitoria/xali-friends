package mc.xalitoria.friends.velocity

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import mc.xalitoria.friends.protocol.Protocol
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * The proxy end of XaliFriends. The servers hold the friendships; the proxy is what sees a player arrive on, leave and
 * move between servers, so it is where friends will be told about it.
 */
class XaliFriends(
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val dataDirectory: Path,
    /** The loader Velocity knows as the plugin, which listeners are registered with. */
    private val plugin: Any,
) : Runnable, AutoCloseable {
    private val channel = MinecraftChannelIdentifier.from(Protocol.CHANNEL)

    override fun run() {
        dataDirectory.createDirectories()
        proxy.channelRegistrar.register(channel)
        proxy.eventManager.register(plugin, this)
        logger.info("XaliFriends enabled")
    }

    override fun close() {
        proxy.channelRegistrar.unregister(channel)
    }
}
