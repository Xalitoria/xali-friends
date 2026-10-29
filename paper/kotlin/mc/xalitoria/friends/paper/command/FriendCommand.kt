package mc.xalitoria.friends.paper.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.XaliFriends.Companion.debug
import mc.xalitoria.friends.paper.data.constants.Permissions
import mc.xalitoria.friends.paper.data.db.repository.SettingKey
import mc.xalitoria.friends.paper.modal.command.XCommand
import mc.xalitoria.friends.paper.service.FriendService
import mc.xalitoria.util.bukkit.runTask
import mc.xalitoria.util.message.*
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.entity.Player

/** `/xfriend`: the friend menu, and everything it does as commands. */
object FriendCommand : XCommand(XaliFriends.INSTANCE) {
    override val description: String = "Manage your friends"
    override val mainCommand = "xfriend"
    override val aliases: List<String> = ["friend", "f"]

    override fun builder(): LiteralArgumentBuilder<CommandSourceStack> = Commands
        .literal(mainCommand)
        .executes { ctx -> run(ctx) { player -> openMenu(player) } }
        .then(menu())
        .then(Commands.literal("help").executes { ctx -> ctx.source.sender send cmdMsg.help(mainCommand); Command.SINGLE_SUCCESS })
        .then(withPlayer("add", online = true) { player, name -> plugin.service.add(player, name) })
        .then(withPlayer("remove", friends = true) { player, name -> plugin.service.remove(player, name) })
        .then(withPlayer("favourite", friends = true) { player, name -> plugin.service.favourite(player, name) })
        .then(withPlayer("accept", requests = true) { player, name ->
            if (name.equals("all", ignoreCase = true)) plugin.service.answerAll(player, accept = true) else plugin.service.accept(player, name)
        })
        .then(withPlayer("deny", requests = true) { player, name ->
            if (name.equals("all", ignoreCase = true)) plugin.service.answerAll(player, accept = false) else plugin.service.deny(player, name)
        })
        .then(paged("list") { player, page -> list(player, page) })
        .then(paged("requests") { player, page -> requests(player, page) })
        .then(Commands.literal("status")
            .executes { ctx -> run(ctx) { player -> statusHint(player) } }
            .then(Commands.argument("text", StringArgumentType.greedyString()).executes { ctx ->
                val text = StringArgumentType.getString(ctx, "text")
                run(ctx) { player -> plugin.service.status(player, text) }
            })
        )
        .then(Commands.literal("removestatus").executes { ctx -> run(ctx) { player -> plugin.service.status(player, "") } })
        .then(Commands.literal("toggle").then(
            Commands.argument("setting", StringArgumentType.word())
                .suggests { _, builder -> SettingKey.entries.map { it.key }.filter { it.startsWith(builder.remainingLowerCase) }.forEach(builder::suggest); builder.buildFuture() }
                .executes { ctx ->
                    val key = SettingKey.of(StringArgumentType.getString(ctx, "setting"))
                    if (key == null) {
                        ctx.source.sender fail cmdMsg.help(mainCommand)
                        Command.SINGLE_SUCCESS
                    } else run(ctx) { player -> plugin.service.toggle(player, key) }
                }
        ))

    /**
     * `/xfriend menu`: opens the menu, and is what the menus' clicks run to change the page or pick a friend. Those two
     * only set what the next draw shows, so they run at once, where the click is, and not in the background.
     */
    private fun menu(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("menu")
        .executes { ctx -> run(ctx) { player -> openMenu(player) } }
        .then(Commands.literal("page").then(
            Commands.argument("list", StringArgumentType.word())
                .suggests { _, builder -> listOf("friends", "requests").filter { it.startsWith(builder.remaining) }.forEach(builder::suggest); builder.buildFuture() }
                .then(Commands.argument("move", StringArgumentType.word())
                    .suggests { _, builder -> listOf("next", "previous", "first").filter { it.startsWith(builder.remaining) }.forEach(builder::suggest); builder.buildFuture() }
                    .executes { ctx ->
                        val list = StringArgumentType.getString(ctx, "list")
                        val move = StringArgumentType.getString(ctx, "move")
                        guard(ctx)?.let { turnPage(it, list.equals("friends", ignoreCase = true), move.lowercase()) }
                        Command.SINGLE_SUCCESS
                    })
        ))
        .then(Commands.literal("select").then(
            Commands.argument("place", IntegerArgumentType.integer(1)).executes { ctx ->
                val place = IntegerArgumentType.getInteger(ctx, "place")
                guard(ctx)?.let { plugin.cache.state(it.uniqueId).selected = plugin.listings.friendAt(it, place)?.profile?.uuid }
                Command.SINGLE_SUCCESS
            }
        ))

    private fun turnPage(player: Player, friends: Boolean, move: String) {
        val snapshot = plugin.cache.get(player.uniqueId) ?: return
        val state = plugin.cache.state(player.uniqueId)
        val listings = plugin.listings
        val size = if (friends) snapshot.friends.size else snapshot.requests.size
        val perPage = if (friends) listings.friendsPerPage() else listings.requestsPerPage()
        val current = listings.clamp(if (friends) state.friendsPage else state.requestsPage, size, perPage)
        val next = listings.clamp(
            when (move) {
                "next" -> current + 1
                "previous" -> current - 1
                else -> 0
            },
            size, perPage,
        )
        if (friends) state.friendsPage = next else state.requestsPage = next
    }

    /** Opens the menu when there is one, and prints the help otherwise. */
    private suspend fun openMenu(player: Player) {
        plugin.cache.load(player.uniqueId)
        val menu = plugin.menu
        if (menu != null && menu.hasMenu()) menu.open(player)
        else player send (if (plugin.config.data.gui.enabled) msg.noMenu else cmdMsg.help(mainCommand))
    }

    private fun statusHint(player: Player) {
        player.sendMessage(
            (INFO_COLOR + "Type your status after /friend status, up to ${FriendService.MAX_STATUS} characters. [Click here to start]").toComponent()
                .clickEvent(ClickEvent.suggestCommand("/friend status "))
        )
    }

    private suspend fun list(player: Player, page: Int) {
        val snapshot = plugin.cache.get(player.uniqueId) ?: plugin.cache.load(player.uniqueId) ?: return
        val listings = plugin.listings
        val friends = listings.friends(player, snapshot)
        if (friends.isEmpty()) return player send msg.noFriends
        val perPage = plugin.config.data.friends.perPage.coerceAtLeast(1)
        val pages = listings.pages(friends.size, perPage)
        val shown = page.coerceIn(1, pages)
        player send msg.listHeader(shown, pages)
        val words = plugin.messageConfig.data.words
        friends.drop((shown - 1) * perPage).take(perPage).forEach { entry ->
            val online = plugin.presentation.isOnline(player, entry.profile)
            player send msg.listEntry(
                if (entry.favourite) words.favouriteMarker else words.plainMarker,
                entry.profile.name,
                if (online) words.online else plugin.presentation.lastSeen(entry.profile),
            )
        }
        if (shown < pages) player send msg.pageFooter("friend list", shown + 1)
    }

    private suspend fun requests(player: Player, page: Int) {
        val snapshot = plugin.cache.load(player.uniqueId) ?: return
        if (snapshot.requests.isEmpty()) return player send msg.noRequests
        val perPage = plugin.config.data.friends.perPage.coerceAtLeast(1)
        val pages = plugin.listings.pages(snapshot.requests.size, perPage)
        val shown = page.coerceIn(1, pages)
        player send msg.requestsHeader(shown, pages)
        snapshot.requests.drop((shown - 1) * perPage).take(perPage).forEach {
            player send msg.requestsEntry(it.profile.name, plugin.presentation.date(it.date))
        }
        if (shown < pages) player send msg.pageFooter("friend requests", shown + 1)
    }

    private fun paged(label: String, action: suspend (Player, Int) -> Unit): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(label)
            .executes { ctx -> run(ctx) { player -> action(player, 1) } }
            .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes { ctx ->
                val page = IntegerArgumentType.getInteger(ctx, "page")
                run(ctx) { player -> action(player, page) }
            })

    /** `/xfriend <label> <player>`; the suggestions are the online players, the player's friends, or those who asked them. */
    private fun withPlayer(
        label: String,
        online: Boolean = false,
        friends: Boolean = false,
        requests: Boolean = false,
        action: suspend (Player, String) -> Unit,
    ): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(label).then(
        RequiredArgumentBuilder.argument<CommandSourceStack, String>("player", StringArgumentType.word())
            .suggests { ctx, builder ->
                val player = ctx.source.sender as? Player
                val names = buildList {
                    if (online) plugin.server.onlinePlayers.filter { it != player }.mapTo(this) { it.name }
                    val snapshot = player?.let { plugin.cache.get(it.uniqueId) }
                    if (friends) snapshot?.friends?.mapTo(this) { it.profile.name }
                    if (requests) {
                        add("all")
                        snapshot?.requests?.mapTo(this) { it.profile.name }
                    }
                }
                names.filter { it.startsWith(builder.remaining, ignoreCase = true) }.forEach(builder::suggest)
                builder.buildFuture()
            }
            .executes { ctx ->
                val name = StringArgumentType.getString(ctx, "player")
                run(ctx) { player -> action(player, name) }
            }
    )

    /** The sender if they may use the command and are a player; otherwise they are told why and it is null. */
    private fun guard(ctx: CommandContext<CommandSourceStack>): Player? {
        val sender = ctx.source.sender
        if (!sender.hasPermission(Permissions.FRIEND)) {
            debug("/xfriend denied for {0}: missing {1}", sender.name, Permissions.FRIEND)
            sender fail cmdMsg.noPermission
            return null
        }
        if (sender !is Player) {
            sender fail cmdMsg.playerCommandOnly
            return null
        }
        return sender
    }

    /** Runs [block] for the sender in the plugin's scope, once [guard] lets them. */
    private fun run(ctx: CommandContext<CommandSourceStack>, block: suspend (Player) -> Unit): Int {
        val sender = guard(ctx) ?: return Command.SINGLE_SUCCESS
        plugin.scope.launch(Dispatchers.IO) {
            try {
                block(sender)
            } catch (e: Exception) {
                plugin.logger.fail("Failed to run /xfriend for ${sender.name}", e)
                sender fail cmdMsg.unexpectedError
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private val cmdMsg get() = XaliFriends.INSTANCE.messageConfig.data.commands
    private val msg get() = XaliFriends.INSTANCE.messageConfig.data.friend
}
