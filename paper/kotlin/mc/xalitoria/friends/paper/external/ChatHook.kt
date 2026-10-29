package mc.xalitoria.friends.paper.external

import mc.xalitoria.chat.api.NetworkPlayers
import mc.xalitoria.chat.api.PrivateMessageGate
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.service.MessageRule
import org.bukkit.entity.Player
import org.bukkit.plugin.ServicePriority

/**
 * XaliChat's side of this plugin: it asks the gate registered here before it delivers a private message, and it knows
 * who is online on the other servers. Only made when XaliChat is installed, since it loads XaliChat's classes.
 */
class ChatHook(private val plugin: XaliFriends) {
    private val gate = PrivateMessageGate { sender, receiver -> MessageRule.allows(plugin.cache.get(receiver.uniqueId), sender) }

    fun register() {
        plugin.server.servicesManager.register(PrivateMessageGate::class.java, gate, plugin, ServicePriority.Normal)
    }

    fun isOnline(name: String, viewer: Player?): Boolean =
        plugin.server.servicesManager.load(NetworkPlayers::class.java)?.isOnline(name, viewer) == true
}
