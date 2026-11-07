package mc.xalitoria.friends.paper.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.xalitoria.util.message.*
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.XaliFriends.Companion.debug
import mc.xalitoria.friends.paper.data.constants.Permissions
import mc.xalitoria.friends.paper.modal.command.XCommand

/** The root command. Every other command is registered on its own and also runs here under its names without the `x`. */
object XaliFriendsCommand : XCommand(XaliFriends.INSTANCE) {
    override val description: String = "Main command for XaliFriends"
    override val mainCommand = "xalifriends"
    override val aliases = ["friends"]

    private val all: List<XCommand> = [
        FriendCommand,
    ]

    val commands: List<XCommand> = all

    override fun builder(): LiteralArgumentBuilder<CommandSourceStack> = Commands
        .literal(mainCommand)
        .executes { ctx ->
            ctx.source.sender send cmdMsg.help(mainCommand)
            Command.SINGLE_SUCCESS
        }
        .then(Commands
            .literal("reload")
            .executes { ctx ->
                val sender = ctx.source.sender
                if (!sender.hasPermission(Permissions.RELOAD)) {
                    debug("/xalifriends reload denied for {0}: missing {1}", sender.name, Permissions.RELOAD)
                    sender fail cmdMsg.noPermission
                } else {
                    debug("{0} reloaded the configuration", sender.name)
                    plugin.reload()
                    sender pass cmdMsg.reload.pass
                }
                Command.SINGLE_SUCCESS
            }
        )
        .then(Commands
            .literal("pack")
            .then(Commands
                .literal("update")
                .executes { ctx ->
                    val sender = ctx.source.sender
                    if (!sender.hasPermission(Permissions.PACK)) {
                        debug("/xalifriends pack update denied for {0}: missing {1}", sender.name, Permissions.PACK)
                        sender fail cmdMsg.noPermission
                    } else {
                        debug("{0} updates the packs", sender.name)
                        // Downloads and unpacks, so not on a region thread.
                        plugin.scope.launch { plugin.installPacks(sender) }
                    }
                    Command.SINGLE_SUCCESS
                }
            )
        )
        .also { root -> commands.forEach { command -> command.subcommands.forEach { root.then(command.subcommand(it)) } } }

    private val cmdMsg get() = XaliFriends.INSTANCE.messageConfig.data.commands
}
