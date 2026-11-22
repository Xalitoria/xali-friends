package mc.xalitoria.friends.paper.external

import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.data.config.MainConfig
import net.xalitoria.menu.XaliMenuApi
import org.bukkit.entity.Player
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * XaliMenu's side of this plugin: registers the `xalifriends:*` actions, catalogs and conditions, and writes the
 * default friend menus (`vanilla` or `nexo` style) into XaliMenu's `menus` folder. Only made when XaliMenu is
 * installed, since it loads XaliMenu's classes.
 */
class MenuHook(private val plugin: XaliFriends) {
    private val api = XaliMenuApi.get()

    init {
        FriendActions(plugin, api).register()
        FriendMenuExtensions(plugin, api).register()
    }

    /** Whether the main menu exists, which it doesn't until XaliMenu has read its folder. */
    fun hasMenu(): Boolean = plugin.config.data.gui.menu in api.menus

    fun open(player: Player) = api.open(player, plugin.config.data.gui.menu)

    /**
     * Writes the default menus for [style] into XaliMenu's `menus` folder, returning the names written. Existing files
     * are kept unless [overwrite] is true; returns null if XaliMenu keeps no folder to write into.
     */
    fun writeMenus(
        overwrite: Boolean = false,
        style: MainConfig.Gui.Style = plugin.config.data.menuStyle(),
    ): List<String>? {
        val directory = plugin.server.pluginManager.getPlugin("XaliMenu")?.dataPath?.resolve("menus") ?: return null
        directory.createDirectories()
        val main = plugin.config.data.gui.menu
        val written = mutableListOf<String>()
        for (menu in DefaultMenus.all(main, style)) {
            val file = directory.resolve("${menu.key}.conf")
            if (overwrite || !file.exists()) {
                file.writeText(menu.value)
                written.add(menu.key)
            }
        }
        return written
    }
}
