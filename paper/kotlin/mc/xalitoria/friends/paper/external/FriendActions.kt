package mc.xalitoria.friends.paper.external

import com.xalitoria.util.message.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mc.xalitoria.friends.paper.XaliFriends
import mc.xalitoria.friends.paper.data.constants.Permissions
import mc.xalitoria.friends.paper.data.db.repository.SettingKey
import net.xalitoria.menu.Action
import net.xalitoria.menu.ActionType
import net.xalitoria.menu.MenuContext
import net.xalitoria.menu.Settings
import net.xalitoria.menu.XaliMenuApi
import org.bukkit.entity.Player

/** What an action does once its work is done: drawn again, closed, left alone, or replaced by another menu. */
sealed interface After {
    data object Refresh : After
    data object Close : After
    data object Leave : After
    data class Open(val menu: String) : After

    companion object {
        /** `refresh`, `close`, `none` or `open:<menu>`; anything else is a mistake in the menu file. */
        fun parse(text: String): After = when {
            text.equals("refresh", ignoreCase = true) -> Refresh
            text.equals("close", ignoreCase = true) -> Close
            text.equals("none", ignoreCase = true) -> Leave
            text.startsWith("open:", ignoreCase = true) && text.length > 5 -> Open(text.substring(5).trim())
            else -> throw IllegalArgumentException("'after' is '$text'; it is refresh, close, none or open:<menu>")
        }
    }
}

/** The settings and the work of each action [FriendActions] adds. */
object FriendActionKinds {
    const val PREFIX = "xalifriends:"

    /** The actions that name another player in `player` (a UUID, a name or, for the answers, `all`), with what they do afterwards when `after` is not written. */
    val TARGETED = mapOf(
        "accept" to After.Refresh,
        "deny" to After.Refresh,
        "remove" to After.Refresh,
        "favorite" to After.Refresh,
        "jump" to After.Close,
    )

    val LISTS = listOf("friends", "requests")
    val MOVES = listOf("next", "previous", "first")

    fun list(text: String): Boolean = when (text.lowercase()) {
        "friends" -> true
        "requests" -> false
        else -> throw IllegalArgumentException("'list' is '$text'; it is friends or requests")
    }

    fun move(text: String): String = text.lowercase().also {
        require(it in MOVES) { "'move' is '$text'; it is next, previous or first" }
    }

    fun setting(text: String): SettingKey = SettingKey.of(text)
        ?: throw IllegalArgumentException("'setting' is '$text'; it is one of ${SettingKey.entries.joinToString { it.key }}")
}

/**
 * XaliMenu's actions for the friend menus. Each does what the `/xfriend` subcommand of the same name does, in the
 * background, and then draws the menu again (or closes it, or opens another: `after`), so a menu needs neither a command
 * nor a guess at how long the change takes.
 *
 * | Action | Keys |
 * |---|---|
 * | `xalifriends:accept`, `deny` | `player`: a UUID, a name or `all`; placeholders are filled in |
 * | `xalifriends:remove`, `favorite`, `jump` | `player` |
 * | `xalifriends:toggle` | `setting`: `invites`, `notifies`, `msgs`, `lastseen`, `jump` or `server` |
 * | `xalifriends:clear-status` | |
 * | `xalifriends:select` | `place`: the friend at that place of the list is the one their menu is about |
 * | `xalifriends:page` | `list`: `friends` or `requests`; `move`: `next`, `previous` or `first` |
 *
 * `after` is `refresh` (the default, `close` for `jump`), `close`, `none` or `open:<menu>`. `select` and `page` only set
 * what the next draw shows, so they run at once and draw the menu again themselves.
 */
class FriendActions(private val plugin: XaliFriends, private val api: XaliMenuApi) {
    fun register() {
        for (entry in FriendActionKinds.TARGETED) {
            val kind = entry.key
            val default = entry.value
            api.registerAction(plugin, FriendActionKinds.PREFIX + kind, ActionType { settings ->
                val player = settings.requireString("player")
                val after = after(settings, default)
                when (kind) {
                    "accept" -> background(after) { actor, ctx ->
                        val target = ctx.parse(player)
                        if (target.equals("all", ignoreCase = true)) plugin.service.answerAll(actor, accept = true) else plugin.service.accept(actor, target)
                    }
                    "deny" -> background(after) { actor, ctx ->
                        val target = ctx.parse(player)
                        if (target.equals("all", ignoreCase = true)) plugin.service.answerAll(actor, accept = false) else plugin.service.deny(actor, target)
                    }
                    "remove" -> background(after) { actor, ctx -> plugin.service.remove(actor, ctx.parse(player)) }
                    "favorite" -> background(after) { actor, ctx -> plugin.service.favorite(actor, ctx.parse(player)) }
                    else -> background(after) { actor, ctx -> plugin.service.jump(actor, ctx.parse(player)) }
                }
            })
        }
        api.registerAction(plugin, FriendActionKinds.PREFIX + "toggle", ActionType { settings ->
            val key = FriendActionKinds.setting(settings.requireString("setting"))
            background(after(settings, After.Refresh)) { actor, _ -> plugin.service.toggle(actor, key) }
        })
        api.registerAction(plugin, FriendActionKinds.PREFIX + "clear-status", ActionType { settings ->
            background(after(settings, After.Refresh)) { actor, _ -> plugin.service.status(actor, "") }
        })
        api.registerAction(plugin, FriendActionKinds.PREFIX + "select", ActionType { settings ->
            val place = settings.int("place") ?: throw IllegalArgumentException("'place' is missing or not a number")
            require(place >= 1) { "'place' is $place; the first is 1" }
            immediate { ctx ->
                val player = ctx.player
                plugin.cache.state(player.uniqueId).selected = plugin.listings.friendAt(player, place)?.profile?.uuid
            }
        })
        api.registerAction(plugin, FriendActionKinds.PREFIX + "page", ActionType { settings ->
            val friends = FriendActionKinds.list(settings.requireString("list"))
            val move = FriendActionKinds.move(settings.string("move", "next"))
            immediate { ctx ->
                plugin.listings.turnPage(ctx.player.uniqueId, friends, move)
                ctx.refresh()
            }
        })
    }

    private fun after(settings: Settings, default: After): After = settings.string("after")?.let(After::parse) ?: default

    /** Whether the viewer may use the friend commands; they are told when not. */
    private fun allowed(ctx: MenuContext): Boolean {
        if (ctx.player.hasPermission(Permissions.FRIEND)) return true
        ctx.player fail plugin.messageConfig.data.commands.noPermission
        return false
    }

    /** An action that does [work] where the click is, once [allowed] says so. */
    private fun immediate(work: (MenuContext) -> Unit): Action = object : Action {
        override fun run(ctx: MenuContext) {
            if (allowed(ctx)) work(ctx)
        }
    }

    /** Runs [work] off the region thread, then does [after] on it, whether the work went through or not. */
    private fun background(after: After, work: suspend (Player, MenuContext) -> Unit): Action = immediate { ctx ->
        val player = ctx.player
        plugin.scope.launch(Dispatchers.IO) {
            try {
                work(player, ctx)
            } catch (e: Exception) {
                plugin.logger.fail("Failed to run a friend menu action for ${player.name}", e)
                player fail plugin.messageConfig.data.commands.unexpectedError
            } finally {
                ctx.runLater(1) { finish(ctx, after) }
            }
        }
    }

    private fun finish(ctx: MenuContext, after: After) = when (after) {
        After.Refresh -> ctx.refresh()
        After.Close -> ctx.close()
        After.Leave -> Unit
        is After.Open -> ctx.open(after.menu)
    }
}
