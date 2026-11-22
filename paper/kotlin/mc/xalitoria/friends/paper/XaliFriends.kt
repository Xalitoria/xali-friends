package mc.xalitoria.friends.paper

import com.xalitoria.util.bukkit.coroutineScope
import com.xalitoria.util.config.YamlFile
import com.xalitoria.util.message.*
import com.xalitoria.util.message.verbatim
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import mc.xalitoria.friends.paper.command.XaliFriendsCommand
import mc.xalitoria.friends.paper.data.config.MainConfig
import mc.xalitoria.friends.paper.data.config.MessageConfig
import mc.xalitoria.friends.paper.data.constants.Permissions
import mc.xalitoria.friends.paper.data.db.DatabaseManager
import mc.xalitoria.friends.paper.data.db.legacy.LegacyImport
import mc.xalitoria.friends.paper.external.ChatHook
import mc.xalitoria.friends.paper.external.FriendsExpansion
import mc.xalitoria.friends.paper.external.MenuHook
import mc.xalitoria.friends.paper.listener.PlayerListener
import mc.xalitoria.friends.paper.pack.HttpDownloader
import mc.xalitoria.friends.paper.pack.PackInstaller
import mc.xalitoria.friends.paper.proxy.ProxyLink
import mc.xalitoria.friends.paper.service.FriendCache
import mc.xalitoria.friends.paper.service.FriendService
import mc.xalitoria.friends.paper.service.Listings
import mc.xalitoria.friends.paper.service.Presentation
import org.bukkit.command.CommandSender
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.java.JavaPlugin

class XaliFriends : JavaPlugin() {
    lateinit var config: YamlFile<MainConfig>
        private set

    lateinit var messageConfig: YamlFile<MessageConfig>
        private set

    lateinit var scope: CoroutineScope
        private set

    lateinit var cache: FriendCache
        private set

    lateinit var service: FriendService
        private set

    lateinit var presentation: Presentation
        private set

    lateinit var listings: Listings
        private set

    /** XaliChat's side, when XaliChat is installed. */
    var chat: ChatHook? = null
        private set

    /** XaliMenu's side, when XaliMenu and PlaceholderAPI are installed and the menus are on. */
    var menu: MenuHook? = null
        private set

    /** The link to the Velocity plugin, when the server is behind Velocity with modern forwarding and `proxy.enabled` is on. */
    var link: ProxyLink? = null
        private set

    private var expansion: FriendsExpansion? = null

    override fun onEnable() {
        INSTANCE = this
        scope = coroutineScope()

        config = YamlFile(dataPath.resolve("config.yml"), MainConfig.serializer(), ::MainConfig)
        messageConfig = YamlFile(
            dataPath.resolve("language", config.data.language + ".yml"),
            MessageConfig.serializer(),
            ::MessageConfig,
        )
        reload()

        val pm = server.pluginManager
        try {
            DatabaseManager.connect(this, config.data.database)
        } catch (e: Exception) {
            logger.fail("Failed to connect to the database; disabling.", e)
            pm.disablePlugin(this)
            return
        }
        debug("Connected to the {0} database", config.data.database.type)

        try {
            LegacyImport.run(config.data, dataFolder) { logger.info(it) }
        } catch (e: Exception) {
            logger.fail("Failed to move the original FriendSystem's data in; disabling. Nothing of it was changed, so fix the cause and start again.", e)
            pm.disablePlugin(this)
            return
        }

        // Everyone may use the friend commands unless a permissions plugin says otherwise.
        pm.addPermission(Permission(Permissions.FRIEND, PermissionDefault.TRUE))
        pm.addPermission(Permission(Permissions.JUMP, PermissionDefault.TRUE))

        cache = FriendCache(this)
        presentation = Presentation(this)
        listings = Listings(this)
        service = FriendService(this)
        cache.startRefreshing()

        if (config.data.proxy.enabled) {
            val secret = ProxyLink.readSecret(this)
            if (secret.isNotEmpty()) {
                link = ProxyLink(this, secret).also { it.start() }
                logger.info("Velocity forwarding found: friends are told of arrivals across the network, and can jump to each other")
            } else debug("No Velocity secret in paper-global.yml, so this server works alone")
        }

        if (pm.isPluginEnabled("XaliChat")) {
            chat = ChatHook(this).also { it.register() }
            logger.info("XaliChat found: private messages follow each player's friends-only setting")
        }
        if (pm.isPluginEnabled("PlaceholderAPI")) {
            expansion = FriendsExpansion(this).also { it.register() }
            if (config.data.gui.enabled && pm.isPluginEnabled("XaliMenu")) enableMenus()
        } else if (config.data.gui.enabled) {
            logger.info("PlaceholderAPI and XaliMenu are needed for the friend menus; /friend prints its help instead")
        }

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            (listOf(XaliFriendsCommand) + XaliFriendsCommand.commands).forEach { it.register(registrar) }
        }
        pm.registerEvents(PlayerListener(this), this)
        debug("Enabled: chat={0}, menus={1}", chat != null, menu != null)
        // The handler plugin has enabled by now (build.gradle.kts loads this after it); the download is off the main thread.
        if (config.data.packs.handler != MainConfig.Packs.Handler.NONE) scope.launch { installPacks() }
    }

    /**
     * Installs the packs that `packs` in the configuration asks for into the plugin that serves them, and tells the
     * console and [sender] how it went. Does nothing, but say so to [sender], when no handler is set.
     */
    suspend fun installPacks(sender: CommandSender? = null) {
        val packs = config.data.packs
        val msg = messageConfig.data.commands.pack
        fun pass(text: String) { logger pass text; sender?.pass(text) }
        fun warn(text: String) { logger warn text; sender?.warn(text) }
        fun fail(text: String) { logger fail text; sender?.fail(text) }

        val handler = packs.handler
        if (handler.folder == null) {
            sender?.info(msg.disabled)
            return
        }
        if (!packs.url.startsWith("https://")) return fail(msg.badUrl(packs.url.verbatim()))
        if (server.pluginManager.getPlugin(handler.folder) == null) return warn(msg.notInstalled(handler.folder))

        val installer = PackInstaller(HttpDownloader(), server.pluginsFolder.toPath(), dataPath.resolve("packs"))
        val reload = if (handler == MainConfig.Packs.Handler.NEXO) "Nexo (/nexo reload)" else "ItemsAdder (/iazip)"
        for (result in installer.install(handler, packs.url, packs.install)) {
            when (result.status) {
                PackInstaller.Status.INSTALLED -> pass(msg.installed(result.pack, result.detail, reload))
                PackInstaller.Status.CURRENT -> sender?.info(msg.current(result.pack))
                PackInstaller.Status.FAILED -> fail(msg.failed(result.pack, result.detail.verbatim()))
            }
            debug("Pack {0}: {1} ({2})", result.pack, result.status, result.detail)
        }
    }

    private fun enableMenus() {
        val hook = MenuHook(this)
        menu = hook
        if (config.data.gui.writeMenus) {
            val written = hook.writeMenus()
            if (!written.isNullOrEmpty()) {
                logger.info("Wrote ${written.size} friend menu(s) (${config.data.menuStyle().name.lowercase()} style) to XaliMenu's menus folder; /xmenu reload reads them if XaliMenu has already started")
            }
        }
    }

    override fun onDisable() {
        expansion?.unregister()
        link?.stop()
        server.servicesManager.unregisterAll(this)
        if (::scope.isInitialized) scope.cancel("XaliFriends is disabling")
        DatabaseManager.close()
    }

    fun reload() {
        config.load()
        config.save()
        debugger =
            if (config.data.debug) { message: String, args: Array<out Any?> -> logger.info(message.formatArgs(*args)) }
            else { message: String, args: Array<out Any?> -> }
        debug("Reloaded: language={0}, database={1}", config.data.language, config.data.database.type)
        messageConfig.load()
        messageConfig.save()
        logger.pass(messageConfig.data.commands.reload.pass)
    }

    /** Requests sent before this (milliseconds since the epoch) are gone; null when they never expire. */
    fun requestCutoff(): Long? {
        val minutes = config.data.friends.requestExpiry
        return if (minutes > 0) System.currentTimeMillis() - minutes * 60_000L else null
    }

    companion object {
        internal lateinit var INSTANCE: XaliFriends
        private var debugger = { message: String, args: Array<out Any?> -> }
        fun debug(message: String, vararg args: Any?) = debugger(message, args)
    }
}
