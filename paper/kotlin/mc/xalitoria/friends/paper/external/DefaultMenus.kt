package mc.xalitoria.friends.paper.external

/**
 * The menus written into XaliMenu's folder the first time: the friend list, the requests, a friend's own menu and the
 * settings. They are plain XaliMenu files from then on, so they are edited there; any `material` can be swapped for
 * another item or for a custom one (`nexo:<id>`, `itemsadder:<id>`). They are made by code only because the lists are
 * one item per place, which would be a long file to write out by hand.
 *
 * They use nothing but what XaliMenu has: `%xalifriends_...%` for what to show, `text` conditions on those to leave a
 * place empty, and the `player` action to run `/xfriend` subcommands. Changes made in the background (an accept, a
 * toggle) are drawn a few ticks after the click.
 */
object DefaultMenus {
    private const val PLAYER = "friends-player"
    private const val REQUESTS = "friends-requests"
    private const val SETTINGS = "friends-settings"
    private const val REMOVE = "friends-remove"

    /** Ticks between a click that changes something in the background and drawing the menu again. */
    private const val REDRAW = 6

    /** The columns 1 to 7 of rows 1 to 4: the places a list can use. */
    private val LIST_SLOTS = (1..4).flatMap { row -> (1..7).map { row * 9 + it } }

    fun all(main: String, friendsPerPage: Int, requestsPerPage: Int): Map<String, String> = linkedMapOf(
        main to friends(main, friendsPerPage),
        REQUESTS to requests(main, requestsPerPage),
        PLAYER to player(main),
        SETTINGS to settings(main),
        REMOVE to remove(main),
    )

    private fun header(comment: String, title: String, size: Int) = """
        # $comment
        # Edit freely: this file is only written when it is missing. A `material` can be any item, such as nexo:<id>.
        title = "$title"
        size = $size
        register = false
        refresh = 5s
    """.trimIndent() + "\n"

    private fun friends(main: String, perPage: Int): String = buildString {
        append(header("The friend list, opened by /friend.", "&8Friends &7(%xalifriends_friends%)", 54))
        appendLine("items {")
        appendLine(filler(54))
        for (n in 1..perPage.coerceIn(1, LIST_SLOTS.size)) {
            appendLine(
                item(
                    "friend_$n",
                    material = "head:%xalifriends_friend_${n}_name%",
                    slot = LIST_SLOTS[n - 1],
                    name = "%xalifriends_friend_${n}_marker% &f%xalifriends_friend_${n}_name%",
                    lore = listOf(
                        "%xalifriends_friend_${n}_seen%",
                        "&7Status: &f%xalifriends_friend_${n}_status%",
                        "&7Friends since &f%xalifriends_friend_${n}_since%",
                        "",
                        "&eClick for options",
                    ),
                    update = true,
                    view = isTrue("%xalifriends_friend_${n}_present%"),
                    click = "click.any.actions = [${command("xfriend menu select $n")}, ${open(PLAYER)}]",
                )
            )
        }
        appendLine(pageNumber("friends"))
        appendLine(pageButton("prev", 45, "friends", "previous", "&ePrevious page"))
        appendLine(pageButton("next", 53, "friends", "next", "&eNext page"))
        appendLine(
            item(
                "requests", "paper", 47, "&eRequests &7(%xalifriends_requests%)",
                listOf("&7Players who asked to be your friend"),
                update = true,
                click = "click.any.actions = [${open(REQUESTS)}]",
            )
        )
        appendLine(
            item(
                "status", "name_tag", 49, "&eYour status",
                listOf("&f%xalifriends_status%", "", "&7Left-click to change it", "&7Right-click to clear it"),
                update = true,
                click = """
                    click.left.actions = [{ type = "close" }, ${command("xfriend status")}]
                    click.right.actions = [${command("xfriend removestatus")}, ${redraw()}]
                """,
            )
        )
        appendLine(
            item(
                "settings", "comparator", 51, "&eSettings", listOf("&7Requests, notifications, messages"),
                click = "click.any.actions = [${open(SETTINGS)}]",
            )
        )
        appendLine("}")
    }

    private fun requests(main: String, perPage: Int): String = buildString {
        append(header("Friend requests: left-click accepts, right-click turns down.", "&8Requests &7(%xalifriends_requests%)", 54))
        appendLine("items {")
        appendLine(filler(54))
        for (n in 1..perPage.coerceIn(1, LIST_SLOTS.size)) {
            appendLine(
                item(
                    "request_$n",
                    material = "head:%xalifriends_request_${n}_name%",
                    slot = LIST_SLOTS[n - 1],
                    name = "&f%xalifriends_request_${n}_name%",
                    lore = listOf("&7Asked on &f%xalifriends_request_${n}_date%", "", "&aLeft-click to accept", "&cRight-click to turn down"),
                    update = true,
                    view = isTrue("%xalifriends_request_${n}_present%"),
                    click = """
                        click.left.actions = [${command("xfriend accept %xalifriends_request_${n}_uuid%")}, ${redraw()}]
                        click.right.actions = [${command("xfriend deny %xalifriends_request_${n}_uuid%")}, ${redraw()}]
                    """,
                )
            )
        }
        appendLine(pageNumber("requests"))
        appendLine(pageButton("prev", 45, "requests", "previous", "&ePrevious page"))
        appendLine(pageButton("next", 53, "requests", "next", "&eNext page"))
        appendLine(back(49, main))
        appendLine("}")
    }

    private fun player(main: String): String = buildString {
        append(header("A friend's own menu: opened by clicking them in the list.", "&8%xalifriends_selected_name%", 27))
        appendLine("items {")
        appendLine(filler(27))
        appendLine(
            item(
                "friend", "head:%xalifriends_selected_name%", 13, "%xalifriends_selected_marker% &f%xalifriends_selected_name%",
                listOf("%xalifriends_selected_seen%", "&7Status: &f%xalifriends_selected_status%", "&7Friends since &f%xalifriends_selected_since%"),
                update = true,
            )
        )
        appendLine(
            item(
                "favourite_off", "gray_dye", 11, "&eMake a favorite", listOf("&7Favorites are listed first"),
                update = true,
                view = isTrue("%xalifriends_selected_favourite%", expected = "false"),
                click = "click.any.actions = [${command("xfriend favourite %xalifriends_selected_uuid%")}, ${redraw()}]",
            )
        )
        appendLine(
            item(
                "favourite_on", "yellow_dye", 11, "&6Favorite", listOf("&7Click to take them off your favorites"),
                update = true,
                view = isTrue("%xalifriends_selected_favourite%"),
                click = "click.any.actions = [${command("xfriend favourite %xalifriends_selected_uuid%")}, ${redraw()}]",
            )
        )
        appendLine(
            item(
                "remove", "barrier", 15, "&cRemove friend", listOf("&7Asks you to confirm first"),
                click = "click.any.actions = [${open(REMOVE)}]",
            )
        )
        appendLine(
            item(
                "jump", "ender_pearl", 22, "&bJump to %xalifriends_selected_name%", listOf("&7%xalifriends_selected_seen%", "", "&eClick to go to their server"),
                update = true,
                view = isTrue("%xalifriends_selected_jumpable%"),
                click = "click.any.actions = [{ type = \"close\" }, ${command("xfriend jump %xalifriends_selected_uuid%")}]",
            )
        )
        appendLine(back(18, main))
        appendLine("}")
    }

    private fun remove(main: String): String = buildString {
        append(header("Asks before ending a friendship.", "&8Remove %xalifriends_selected_name%?", 27))
        appendLine("items {")
        appendLine(filler(27))
        appendLine(
            item(
                "friend", "head:%xalifriends_selected_name%", 13, "&f%xalifriends_selected_name%",
                listOf("&7Friends since &f%xalifriends_selected_since%"),
            )
        )
        appendLine(
            item(
                "confirm", "lime_concrete", 11, "&aRemove", listOf("&7End the friendship with %xalifriends_selected_name%"),
                click = "click.any.actions = [${command("xfriend remove %xalifriends_selected_uuid%")}, { type = \"delay\", ticks = $REDRAW, action = ${open(main)} }]",
            )
        )
        appendLine(
            item(
                "cancel", "red_concrete", 15, "&cKeep", listOf("&7Go back"),
                click = "click.any.actions = [${open(PLAYER)}]",
            )
        )
        appendLine("}")
    }

    private fun settings(main: String): String = buildString {
        append(header("Settings: each item flips one.", "&8Friend settings", 36))
        appendLine("items {")
        appendLine(filler(36))
        val toggles = listOf(
            Toggle("invites", "paper", 10, "Friend requests"),
            Toggle("notifies", "bell", 12, "Notifications"),
            Toggle("msgs", "writable_book", 14, "Private messages from"),
            Toggle("lastseen", "clock", 16, "Show when you were last online"),
            Toggle("jump", "ender_pearl", 20, "Friends may jump to you"),
            Toggle("server", "compass", 24, "Friends see which server you are on"),
        )
        for (toggle in toggles) {
            appendLine(
                item(
                    toggle.key, toggle.material, toggle.slot, "&e${toggle.label}",
                    listOf("&7Now: &f%xalifriends_setting_${toggle.key}%", "", "&eClick to change"),
                    update = true,
                    click = "click.any.actions = [${command("xfriend toggle ${toggle.key}")}, ${redraw()}]",
                )
            )
        }
        appendLine(back(31, main))
        appendLine("}")
    }

    private class Toggle(val key: String, val material: String, val slot: Int, val label: String)

    private fun command(line: String) = """{ type = "player", command = "$line" }"""

    private fun open(menu: String) = """{ type = "open", menu = "$menu" }"""

    private fun redraw() = """{ type = "delay", ticks = $REDRAW, action = { type = "refresh" } }"""

    private fun isTrue(placeholder: String, expected: String = "true") =
        """{ condition { type = "text", input = "$placeholder", op = "EQUALS", value = "$expected" } }"""

    private fun filler(size: Int) =
        "  filler { material = \"gray_stained_glass_pane\", slots = [${(0 until size).joinToString()}], priority = 100, name = \" \" }"

    private fun back(slot: Int, main: String) = item(
        "back", "arrow", slot, "&eBack", emptyList(),
        click = "click.any.actions = [${open(main)}]",
    )

    /** The page the list is on, in an item because a title can't change while the menu is open. */
    private fun pageNumber(list: String) = item(
        "page", "book", 46, "&7Page &f%xalifriends_page_$list%&7/&f%xalifriends_pages_$list%", emptyList(),
        update = true,
    )

    private fun pageButton(key: String, slot: Int, list: String, move: String, name: String) = item(
        key, "arrow", slot, name, emptyList(),
        update = true,
        view = isTrue("%xalifriends_has_${move}_$list%"),
        click = "click.any.actions = [${command("xfriend menu page $list $move")}, { type = \"refresh\" }]",
    )

    /** One item of a menu file; [view] is the gate that decides whether it is shown. */
    private fun item(
        key: String,
        material: String,
        slot: Int,
        name: String,
        lore: List<String>,
        update: Boolean = false,
        view: String? = null,
        click: String = "",
    ): String = buildString {
        appendLine("  $key {")
        appendLine("    material = \"$material\"")
        appendLine("    slot = $slot")
        appendLine("    name = \"$name\"")
        if (lore.isNotEmpty()) appendLine("    lore = [${lore.joinToString { "\"$it\"" }}]")
        if (update) appendLine("    update = true")
        if (view != null) appendLine("    view.require = [$view]")
        click.trimIndent().lines().filter { it.isNotBlank() }.forEach { appendLine("    $it") }
        append("  }")
    }
}
