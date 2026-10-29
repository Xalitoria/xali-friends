package mc.xalitoria.friends.paper.external

import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.menu.api.XaliMenuApi
import org.bukkit.entity.Player
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * XaliMenu's side of this plugin: the friend menus are XaliMenu menus, drawn from `%xalifriends_...%` and clicked through
 * XaliMenu's own `player` action, which runs `/xfriend` subcommands. Only made when XaliMenu is installed, since it loads
 * XaliMenu's classes. Any `material` the menus use works, a Nexo item (`nexo:<id>`) included; that is XaliMenu's.
 *
 * Nothing is registered with XaliMenu. Every plugin gets its own copy of the Kotlin runtime, so an action or condition of
 * this plugin (a `KSerializer` of its own copy) could not be linked to XaliMenu's; strings and Java types cross freely.
 */
class MenuHook(private val plugin: XaliFriends) {
    private val api = XaliMenuApi.get()

    /** Whether the main menu exists, which it doesn't until XaliMenu has read its folder. */
    fun hasMenu(): Boolean = plugin.config.data.gui.menu in api.menus

    fun open(player: Player) = api.open(player, plugin.config.data.gui.menu)

    /** Writes the menus into XaliMenu's folder where they are missing; false if XaliMenu keeps no folder to write into. */
    fun writeMenus(): Boolean {
        val directory = plugin.server.pluginManager.getPlugin("XaliMenu")?.dataPath?.resolve("menus") ?: return false
        directory.createDirectories()
        val gui = plugin.config.data.gui
        for (menu in DefaultMenus.all(gui.menu, gui.friendsPerPage, gui.requestsPerPage)) {
            val file = directory.resolve("${menu.key}.conf")
            if (!file.exists()) file.writeText(menu.value)
        }
        return true
    }
}
