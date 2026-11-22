package mc.xalitoria.friends.paper.external

import mc.xalitoria.friends.paper.data.config.MainConfig.Gui.Style

/**
 * The default menus written into XaliMenu's folder (`vanilla` or `nexo` style). They are ordinary XaliMenu files from
 * then on and can be edited freely (including in `/xmenu edit`).
 *
 * Both styles use XaliMenu's paged catalog system (`xalifriends:friends`, `xalifriends:requests`), addon conditions
 * (`xalifriends:setting`, `xalifriends:has-requests`, `xalifriends:selected`), addon actions (`xalifriends:*`), native
 * `input` prompts (for adding a friend and changing status), `skull` heads and Bedrock-friendly metadata.
 *
 * The `nexo` style draws the resource pack's GUI backgrounds (`<white><font:nexo:default>ᛮᛯ<glyph></font></white>`),
 * custom button items (`nexo:friend_*`) and `player_head` custom model data (`5000` little head, `10050` 2x2 big head).
 */
object DefaultMenus {
    private const val PLAYER = "friends-player"
    private const val REQUESTS = "friends-requests"
    private const val SETTINGS = "friends-settings"
    private const val SETTINGS2 = "friends-settings2"
    private const val REMOVE = "friends-remove"

    /** Nexo glyph characters from `friends/resourcepack/nexo/glyphs/friends.yml`, shifted onto the chest background. */
    private const val NEXO_SHIFT = "ᛮᛯ"
    private const val GLYPH_FRIENDS = "ᛧ"
    private const val GLYPH_PROFILE = "᛫"
    private const val GLYPH_REQUESTS = "᛬"
    private const val GLYPH_SETTINGS = "᛭"
    private const val GLYPH_SETTINGS2 = "ᛸ"

    private const val BIG_HEAD_MODEL = 10050
    private const val LITTLE_HEAD_MODEL = 5000

    fun all(main: String, style: Style = Style.VANILLA): Map<String, String> = when (style) {
        Style.NEXO -> linkedMapOf(
            main to nexoFriends(main),
            REQUESTS to nexoRequests(main),
            PLAYER to nexoPlayer(main),
            REMOVE to nexoRemove(main),
            SETTINGS to nexoSettings(main),
            SETTINGS2 to nexoSettings2(main),
        )
        else -> linkedMapOf(
            main to vanillaFriends(main),
            REQUESTS to vanillaRequests(main),
            PLAYER to vanillaPlayer(main),
            REMOVE to vanillaRemove(main),
            SETTINGS to vanillaSettings(main),
        )
    }

    private fun nexoTitle(glyph: String): String = "<white><font:nexo:default>$NEXO_SHIFT$glyph</font></white>"

    private fun header(comment: String, title: String, size: Int, bedrockTitle: String? = null): String = buildString {
        appendLine("# $comment")
        appendLine("# Edit freely: this file is only written when missing (or via /xalifriends menus reset).")
        appendLine("""title = "$title"""")
        appendLine("size = $size")
        appendLine("register = false")
        appendLine("refresh = 5s")
        if (bedrockTitle != null) {
            appendLine("""bedrock { title = "$bedrockTitle" }""")
        }
    }

    private fun addFriendInput(returnMenu: String): String = """
        {
          type = "input"
          title = "Add friend"
          prompt = "&eType the player's name, or cancel"
          submit = "Send request"
          max-length = 16
          match = "[A-Za-z0-9_]{3,16}"
          invalid = "&cEnter a valid player name (3 to 16 letters, digits or underscores)."
          then = [{ type = "xalifriends:add", player = "{input}", after = "open:$returnMenu" }]
          on-cancel = [{ type = "open", menu = "$returnMenu" }]
        }
    """.trimIndent()

    private fun statusInput(returnMenu: String): String = """
        {
          type = "input"
          title = "Change status"
          prompt = "&eType your new status (or leave empty to clear), or cancel"
          initial = "%xalifriends_status_raw%"
          submit = "Save"
          max-length = 64
          then = [{ type = "xalifriends:status", text = "{input}", after = "open:$returnMenu" }]
          on-cancel = [{ type = "open", menu = "$returnMenu" }]
        }
    """.trimIndent()

    // ---- Nexo style

    private fun nexoFriends(main: String): String = buildString {
        append(header("The friend list (Nexo pack), opened by /friend.", nexoTitle(GLYPH_FRIENDS), 54, "&8Friends &7(%xalifriends_friends%)"))
        appendLine(
            """
            pages {
              catalog { type = "xalifriends:friends" }
              layout = [
                "         "
                "         "
                "         "
                " xxxxxxx "
                " xxxxxxx "
                " xxxxxxx "
              ]
              item {
                material = "player_head"
                skull = "{name}"
                model = $LITTLE_HEAD_MODEL
                name = "&6{star}&f{name}"
                lore = [
                  "&7{seen}"
                  "&7Status: &f{status}"
                  "&7Friends since &f{since}"
                  ""
                  "&eClick for options"
                ]
                click.any.actions = [
                  { type = "xalifriends:select", player = "{uuid}" }
                  { type = "open", menu = "$PLAYER" }
                ]
              }
              empty {
                material = "barrier"
                name = "&7No friends yet"
                lore = ["&eClick to add a friend"]
                click.any.actions = [
            ${addFriendInput(main).prependIndent("      ")}
                ]
              }
            }
            items {
              profile {
                material = "player_head"
                skull = "%xalifriends_name%"
                model = $BIG_HEAD_MODEL
                slot = 10
                name = "&e%xalifriends_name%"
                lore = [
                  "&7Friends: &f%xalifriends_online%&7/&f%xalifriends_friends% online"
                  "&7Status: &f%xalifriends_status%"
                  ""
                  "&7Left-click to change status"
                  "&7Right-click to clear status"
                ]
                update = true
                bedrock { left-label = "&eChange status", right-label = "&cClear status" }
                click.left.actions = [
            ${statusInput(main).prependIndent("      ")}
                ]
                click.right.actions = [{ type = "xalifriends:clear-status" }]
              }
              profile_hitbox {
                material = "nexo:friend_empty"
                slots = [0, 1, 9]
                name = "&e%xalifriends_name%"
                lore = [
                  "&7Friends: &f%xalifriends_online%&7/&f%xalifriends_friends% online"
                  "&7Status: &f%xalifriends_status%"
                  ""
                  "&7Left-click to change status"
                  "&7Right-click to clear status"
                ]
                update = true
                bedrock.hidden = true
                click.left.actions = [
            ${statusInput(main).prependIndent("      ")}
                ]
                click.right.actions = [{ type = "xalifriends:clear-status" }]
              }
              settings {
                material = "nexo:friend_settings"
                slot = 2
                name = "&eSettings"
                lore = ["&7Requests, notifications, messages, status"]
                click.any.actions = [{ type = "open", menu = "$SETTINGS" }]
              }
              requests {
                material = "nexo:friend_notify"
                slot = 11
                priority = 0
                name = "&eRequests &7(%xalifriends_requests%)"
                lore = ["&7Players who asked to be your friend"]
                update = true
                view.require = [{ condition { type = "xalifriends:has-requests" } }]
                click.any.actions = [{ type = "open", menu = "$REQUESTS" }]
              }
              no_requests {
                material = "nexo:friend_no_notify"
                slot = 11
                priority = 10
                name = "&eRequests &7(0)"
                lore = ["&7No friend requests waiting"]
                update = true
                click.any.actions = [{ type = "open", menu = "$REQUESTS" }]
              }
              add {
                material = "nexo:friend_invite"
                slot = 5
                name = "&aAdd friend"
                lore = ["&7Send a friend request by player name"]
                click.any.actions = [
            ${addFriendInput(main).prependIndent("      ")}
                ]
              }
              add_hitbox {
                material = "nexo:friend_empty"
                slots = [6, 7]
                name = "&aAdd friend"
                lore = ["&7Send a friend request by player name"]
                bedrock.hidden = true
                click.any.actions = [
            ${addFriendInput(main).prependIndent("      ")}
                ]
              }
              prev {
                material = "nexo:friend_arrow_left"
                slot = 36
                name = "&ePrevious page"
                lore = ["&7Page {page_prev} of {pages}"]
                bedrock.hidden = true
                view.require = [{ condition { type = "compare", left = "{page}", op = "GT", right = "1" } }]
                click.any.actions = [{ type = "page", by = -1 }]
              }
              next {
                material = "nexo:friend_arrow_right"
                slot = 44
                name = "&eNext page"
                lore = ["&7Page {page_next} of {pages}"]
                bedrock.hidden = true
                view.require = [{ condition { type = "compare", left = "{page}", op = "LT", right = "{pages}" } }]
                click.any.actions = [{ type = "page", by = 1 }]
              }
            }
            """.trimIndent()
        )
    }

    private fun nexoRequests(main: String): String = buildString {
        append(header("Friend requests (Nexo pack): one row per request with Accept and Deny buttons.", nexoTitle(GLYPH_REQUESTS), 54, "&8Requests &7(%xalifriends_requests%)"))
        appendLine(
            """
            pages {
              catalog { type = "xalifriends:requests", repeat = 7 }
              matrix {
                cells = [
                  " habbdee "
                  " habbdee "
                  " habbdee "
                  " habbdee "
                  " habbdee "
                ]
                templates {
                  h {
                    material = "player_head"
                    skull = "{name}"
                    model = $LITTLE_HEAD_MODEL
                    name = "&f{name}"
                    lore = ["&7Asked on &f{date}", "", "&aLeft-click to accept", "&cRight-click to turn down"]
                    bedrock { left-label = "&aAccept", right-label = "&cDeny" }
                    click.left.actions = [{ type = "xalifriends:accept", player = "{uuid}" }]
                    click.right.actions = [{ type = "xalifriends:deny", player = "{uuid}" }]
                  }
                  a {
                    material = "nexo:friend_accept"
                    name = "&aAccept &f{name}"
                    lore = ["&7Asked on &f{date}"]
                    bedrock.hidden = true
                    click.any.actions = [{ type = "xalifriends:accept", player = "{uuid}" }]
                  }
                  b {
                    material = "nexo:friend_empty"
                    name = "&aAccept &f{name}"
                    lore = ["&7Asked on &f{date}"]
                    bedrock.hidden = true
                    click.any.actions = [{ type = "xalifriends:accept", player = "{uuid}" }]
                  }
                  d {
                    material = "nexo:friend_deny"
                    name = "&cDeny &f{name}"
                    lore = ["&7Asked on &f{date}"]
                    bedrock.hidden = true
                    click.any.actions = [{ type = "xalifriends:deny", player = "{uuid}" }]
                  }
                  e {
                    material = "nexo:friend_empty"
                    name = "&cDeny &f{name}"
                    lore = ["&7Asked on &f{date}"]
                    bedrock.hidden = true
                    click.any.actions = [{ type = "xalifriends:deny", player = "{uuid}" }]
                  }
                }
              }
              empty {
                material = "barrier"
                name = "&7No friend requests"
                lore = ["&7Nobody has sent you a friend request."]
              }
            }
            items {
              prev {
                material = "nexo:friend_arrow_left"
                slot = 18
                name = "&ePrevious page"
                lore = ["&7Page {page_prev} of {pages}"]
                bedrock.hidden = true
                view.require = [{ condition { type = "compare", left = "{page}", op = "GT", right = "1" } }]
                click.any.actions = [{ type = "page", by = -1 }]
              }
              next {
                material = "nexo:friend_arrow_right"
                slot = 26
                name = "&eNext page"
                lore = ["&7Page {page_next} of {pages}"]
                bedrock.hidden = true
                view.require = [{ condition { type = "compare", left = "{page}", op = "LT", right = "{pages}" } }]
                click.any.actions = [{ type = "page", by = 1 }]
              }
              back {
                material = "nexo:friend_empty"
                slots = [48, 49, 50]
                name = "&eBack"
                click.any.actions = [{ type = "open", menu = "$main" }]
              }
            }
            """.trimIndent()
        )
    }

    private fun nexoPlayer(main: String): String = buildString {
        append(header("A friend's profile menu (Nexo pack): opened by clicking them in the list.", nexoTitle(GLYPH_PROFILE), 54, "&8%xalifriends_selected_name%"))
        appendLine(
            """
            items {
              friend {
                material = "player_head"
                skull = "%xalifriends_selected_name%"
                model = $BIG_HEAD_MODEL
                slot = 10
                name = "%xalifriends_selected_marker% &f%xalifriends_selected_name%"
                lore = [
                  "%xalifriends_selected_seen%"
                  "&7Status: &f%xalifriends_selected_status%"
                  "&7Friends since &f%xalifriends_selected_since%"
                ]
                update = true
              }
              friend_hitbox {
                material = "nexo:friend_empty"
                slots = [0, 1, 9]
                name = "%xalifriends_selected_marker% &f%xalifriends_selected_name%"
                lore = [
                  "%xalifriends_selected_seen%"
                  "&7Status: &f%xalifriends_selected_status%"
                  "&7Friends since &f%xalifriends_selected_since%"
                ]
                update = true
                bedrock.hidden = true
              }
              favorite_on {
                material = "nexo:friend_like"
                slot = 2
                priority = 0
                glow = true
                name = "&6Favorite"
                lore = ["&7Click to take them off your favorites"]
                update = true
                view.require = [{ condition { type = "xalifriends:selected", check = "favorite" } }]
                click.any.actions = [{ type = "xalifriends:favorite", player = "%xalifriends_selected_uuid%" }]
              }
              favorite_off {
                material = "nexo:friend_like"
                slot = 2
                priority = 10
                name = "&eMake a favorite"
                lore = ["&7Favorites are listed first"]
                update = true
                click.any.actions = [{ type = "xalifriends:favorite", player = "%xalifriends_selected_uuid%" }]
              }
              remove {
                material = "nexo:friend_cancel"
                slot = 8
                name = "&cRemove friend"
                lore = ["&7Asks you to confirm first"]
                click.any.actions = [{ type = "open", menu = "$REMOVE" }]
              }
              jump {
                material = "nexo:friend_jump"
                slot = 21
                name = "&bJump to %xalifriends_selected_name%"
                lore = ["&7%xalifriends_selected_seen%", "", "&eClick to go to their server"]
                update = true
                view.require = [{ condition { type = "xalifriends:selected", check = "jumpable" } }]
                click.any.actions = [{ type = "xalifriends:jump", player = "%xalifriends_selected_uuid%" }]
              }
              jump_hitbox {
                material = "nexo:friend_empty"
                slots = [22, 23]
                name = "&bJump to %xalifriends_selected_name%"
                lore = ["&7%xalifriends_selected_seen%", "", "&eClick to go to their server"]
                update = true
                bedrock.hidden = true
                view.require = [{ condition { type = "xalifriends:selected", check = "jumpable" } }]
                click.any.actions = [{ type = "xalifriends:jump", player = "%xalifriends_selected_uuid%" }]
              }
              back {
                material = "nexo:friend_empty"
                slots = [48, 49, 50]
                name = "&eBack"
                click.any.actions = [{ type = "open", menu = "$main" }]
              }
            }
            """.trimIndent()
        )
    }

    private fun nexoRemove(main: String): String = buildString {
        append(header("Asks before ending a friendship (Nexo pack).", nexoTitle(GLYPH_PROFILE), 54))
        appendLine(
            """
            bedrock {
              mode = "MODAL"
              title = "&8Remove %xalifriends_selected_name%?"
              content = "&7End the friendship with &f%xalifriends_selected_name%&7?"
              confirm {
                text = "&aRemove"
                actions = [{ type = "xalifriends:remove", player = "%xalifriends_selected_uuid%", after = "open:$main" }]
              }
              cancel {
                text = "&cKeep"
                actions = [{ type = "open", menu = "$PLAYER" }]
              }
            }
            items {
              friend {
                material = "player_head"
                skull = "%xalifriends_selected_name%"
                model = $BIG_HEAD_MODEL
                slot = 10
                name = "&f%xalifriends_selected_name%"
                lore = ["&7Friends since &f%xalifriends_selected_since%"]
              }
              friend_hitbox {
                material = "nexo:friend_empty"
                slots = [0, 1, 9]
                name = "&f%xalifriends_selected_name%"
                lore = ["&7Friends since &f%xalifriends_selected_since%"]
                bedrock.hidden = true
              }
              confirm {
                material = "nexo:friend_confirm"
                slot = 21
                name = "&aRemove"
                lore = ["&7End the friendship with %xalifriends_selected_name%"]
                click.any.actions = [{ type = "xalifriends:remove", player = "%xalifriends_selected_uuid%", after = "open:$main" }]
              }
              cancel {
                material = "nexo:friend_cancel"
                slot = 23
                name = "&cKeep"
                lore = ["&7Go back"]
                click.any.actions = [{ type = "open", menu = "$PLAYER" }]
              }
              back {
                material = "nexo:friend_empty"
                slots = [48, 49, 50]
                name = "&eBack"
                click.any.actions = [{ type = "open", menu = "$PLAYER" }]
              }
            }
            """.trimIndent()
        )
    }

    private fun nexoSettings(main: String): String = buildString {
        append(header("Friend settings page 1 (Nexo pack): requests, notifications, private messages, jumping and status.", nexoTitle(GLYPH_SETTINGS), 54, "&8Friend settings (1/2)"))
        appendLine("items {")
        appendLine(nexoStatusButton(SETTINGS))
        appendLine(nexoBooleanToggle("invites", 10, [11, 12, 13, 14, 15], "Friend requests"))
        appendLine(nexoBooleanToggle("notifies", 19, [20, 21, 22, 23, 24], "Notifications"))
        appendLine(
            """
              msgs_everyone {
                material = "nexo:friend_toggle_on"
                slot = 28
                priority = 0
                name = "&ePrivate messages"
                lore = ["&7Now: &f%xalifriends_setting_msgs%", "", "&eClick to change"]
                update = true
                view.require = [{ condition { type = "xalifriends:setting", setting = "msgs", value = "everyone" } }]
                click.any.actions = [{ type = "xalifriends:toggle", setting = "msgs" }]
              }
              msgs_friends {
                material = "nexo:friend_toggle_onlyfriends"
                slot = 28
                priority = 5
                name = "&ePrivate messages"
                lore = ["&7Now: &f%xalifriends_setting_msgs%", "", "&eClick to change"]
                update = true
                view.require = [{ condition { type = "xalifriends:setting", setting = "msgs", value = "friends" } }]
                click.any.actions = [{ type = "xalifriends:toggle", setting = "msgs" }]
              }
              msgs_nobody {
                material = "nexo:friend_toggle_off"
                slot = 28
                priority = 10
                name = "&ePrivate messages"
                lore = ["&7Now: &f%xalifriends_setting_msgs%", "", "&eClick to change"]
                update = true
                click.any.actions = [{ type = "xalifriends:toggle", setting = "msgs" }]
              }
              msgs_label {
                material = "nexo:friend_empty"
                slots = [29, 30, 31, 32, 33]
                name = "&ePrivate messages"
                lore = ["&7Now: &f%xalifriends_setting_msgs%", "", "&eClick to change"]
                update = true
                bedrock.hidden = true
                click.any.actions = [{ type = "xalifriends:toggle", setting = "msgs" }]
              }
            """.trimIndent().prependIndent("  ")
        )
        appendLine(nexoBooleanToggle("jump", 37, [38, 39, 40], "Jumping"))
        appendLine(
            """
              next_page {
                material = "nexo:friend_empty"
                slot = 35
                name = "&eMore settings"
                lore = ["&7Last seen, server visibility"]
                click.any.actions = [{ type = "open", menu = "$SETTINGS2" }]
              }
              back {
                material = "nexo:friend_empty"
                slots = [48, 49, 50]
                name = "&eBack"
                click.any.actions = [{ type = "open", menu = "$main" }]
              }
            """.trimIndent().prependIndent("  ")
        )
        appendLine("}")
    }

    private fun nexoSettings2(main: String): String = buildString {
        append(header("Friend settings page 2 (Nexo pack): last seen, server visibility and status.", nexoTitle(GLYPH_SETTINGS2), 54, "&8Friend settings (2/2)"))
        appendLine("items {")
        appendLine(nexoStatusButton(SETTINGS2))
        appendLine(nexoBooleanToggle("lastseen", 10, [11, 12, 13, 14, 15], "Show when you were last online"))
        appendLine(nexoBooleanToggle("server", 19, [20, 21, 22, 23], "Friends see which server you are on"))
        appendLine(
            """
              prev_page {
                material = "nexo:friend_empty"
                slot = 27
                name = "&ePrevious settings"
                lore = ["&7Requests, notifications, messages, jumping"]
                click.any.actions = [{ type = "open", menu = "$SETTINGS" }]
              }
              back {
                material = "nexo:friend_empty"
                slots = [48, 49, 50]
                name = "&eBack"
                click.any.actions = [{ type = "open", menu = "$main" }]
              }
            """.trimIndent().prependIndent("  ")
        )
        appendLine("}")
    }

    private fun nexoStatusButton(returnMenu: String): String = """
        status {
          material = "nexo:friend_empty"
          slots = [2, 3, 4, 5, 6]
          name = "&eChange status"
          lore = ["&7Current: &f%xalifriends_status%", "", "&7Left-click to change it", "&7Right-click to clear it"]
          update = true
          bedrock { left-label = "&eChange status", right-label = "&cClear status" }
          click.left.actions = [
        ${statusInput(returnMenu).prependIndent("    ")}
          ]
          click.right.actions = [{ type = "xalifriends:clear-status" }]
        }
    """.trimIndent().prependIndent("  ")

    private fun nexoBooleanToggle(key: String, slot: Int, labelSlots: List<Int>, label: String): String = """
        ${key}_on {
          material = "nexo:friend_toggle_on"
          slot = $slot
          priority = 0
          name = "&e$label"
          lore = ["&7Now: &f%xalifriends_setting_$key%", "", "&eClick to change"]
          update = true
          view.require = [{ condition { type = "xalifriends:setting", setting = "$key", value = "on" } }]
          click.any.actions = [{ type = "xalifriends:toggle", setting = "$key" }]
        }
        ${key}_off {
          material = "nexo:friend_toggle_off"
          slot = $slot
          priority = 10
          name = "&e$label"
          lore = ["&7Now: &f%xalifriends_setting_$key%", "", "&eClick to change"]
          update = true
          click.any.actions = [{ type = "xalifriends:toggle", setting = "$key" }]
        }
        ${key}_label {
          material = "nexo:friend_empty"
          slots = [${labelSlots.joinToString()}]
          name = "&e$label"
          lore = ["&7Now: &f%xalifriends_setting_$key%", "", "&eClick to change"]
          update = true
          bedrock.hidden = true
          click.any.actions = [{ type = "xalifriends:toggle", setting = "$key" }]
        }
    """.trimIndent().prependIndent("  ")

    // ---- Vanilla style

    private fun vanillaFriends(main: String): String = buildString {
        append(header("The friend list, opened by /friend.", "&8Friends &7(%xalifriends_friends%)", 54))
        appendLine(
            """
            matrix {
              cells = [
                "#########"
                "#       #"
                "#       #"
                "#       #"
                "#       #"
                "#########"
              ]
              map { "#" = border }
            }
            pages {
              catalog { type = "xalifriends:friends" }
              layout = [
                "         "
                " xxxxxxx "
                " xxxxxxx "
                " xxxxxxx "
                " xxxxxxx "
                "         "
              ]
              item {
                material = "player_head"
                skull = "{name}"
                name = "&6{star}&f{name}"
                lore = [
                  "&7{seen}"
                  "&7Status: &f{status}"
                  "&7Friends since &f{since}"
                  ""
                  "&eClick for options"
                ]
                click.any.actions = [
                  { type = "xalifriends:select", player = "{uuid}" }
                  { type = "open", menu = "$PLAYER" }
                ]
              }
              empty {
                material = "barrier"
                name = "&7No friends yet"
                lore = ["&eClick to add a friend"]
                click.any.actions = [
            ${addFriendInput(main).prependIndent("      ")}
                ]
              }
            }
            items {
              border { material = "gray_stained_glass_pane", priority = 100, name = " " }
              prev {
                material = "arrow"
                slot = 45
                name = "&ePrevious page"
                lore = ["&7Page {page_prev} of {pages}"]
                bedrock.hidden = true
                view.require = [{ condition { type = "compare", left = "{page}", op = "GT", right = "1" } }]
                click.any.actions = [{ type = "page", by = -1 }]
              }
              page {
                material = "book"
                slot = 46
                name = "&7Page &f{page}&7/&f{pages}"
                bedrock.hidden = true
              }
              requests {
                material = "paper"
                slot = 47
                name = "&eRequests &7(%xalifriends_requests%)"
                lore = ["&7Players who asked to be your friend"]
                update = true
                click.any.actions = [{ type = "open", menu = "$REQUESTS" }]
              }
              add {
                material = "emerald"
                slot = 48
                name = "&aAdd friend"
                lore = ["&7Send a friend request by player name"]
                click.any.actions = [
            ${addFriendInput(main).prependIndent("      ")}
                ]
              }
              status {
                material = "name_tag"
                slot = 49
                name = "&eYour status"
                lore = ["&f%xalifriends_status%", "", "&7Left-click to change it", "&7Right-click to clear it"]
                update = true
                bedrock { left-label = "&eChange status", right-label = "&cClear status" }
                click.left.actions = [
            ${statusInput(main).prependIndent("      ")}
                ]
                click.right.actions = [{ type = "xalifriends:clear-status" }]
              }
              settings {
                material = "comparator"
                slot = 51
                name = "&eSettings"
                lore = ["&7Requests, notifications, messages"]
                click.any.actions = [{ type = "open", menu = "$SETTINGS" }]
              }
              next {
                material = "arrow"
                slot = 53
                name = "&eNext page"
                lore = ["&7Page {page_next} of {pages}"]
                bedrock.hidden = true
                view.require = [{ condition { type = "compare", left = "{page}", op = "LT", right = "{pages}" } }]
                click.any.actions = [{ type = "page", by = 1 }]
              }
            }
            """.trimIndent()
        )
    }

    private fun vanillaRequests(main: String): String = buildString {
        append(header("Friend requests: left-click accepts, right-click turns down.", "&8Requests &7(%xalifriends_requests%)", 54))
        appendLine(
            """
            matrix {
              cells = [
                "#########"
                "#       #"
                "#       #"
                "#       #"
                "#       #"
                "#########"
              ]
              map { "#" = border }
            }
            pages {
              catalog { type = "xalifriends:requests" }
              layout = [
                "         "
                " xxxxxxx "
                " xxxxxxx "
                " xxxxxxx "
                " xxxxxxx "
                "         "
              ]
              item {
                material = "player_head"
                skull = "{name}"
                name = "&f{name}"
                lore = ["&7Asked on &f{date}", "", "&aLeft-click to accept", "&cRight-click to turn down"]
                bedrock { left-label = "&aAccept", right-label = "&cDeny" }
                click.left.actions = [{ type = "xalifriends:accept", player = "{uuid}" }]
                click.right.actions = [{ type = "xalifriends:deny", player = "{uuid}" }]
              }
              empty {
                material = "barrier"
                name = "&7No friend requests"
                lore = ["&7Nobody has sent you a friend request."]
              }
            }
            items {
              border { material = "gray_stained_glass_pane", priority = 100, name = " " }
              prev {
                material = "arrow"
                slot = 45
                name = "&ePrevious page"
                lore = ["&7Page {page_prev} of {pages}"]
                bedrock.hidden = true
                view.require = [{ condition { type = "compare", left = "{page}", op = "GT", right = "1" } }]
                click.any.actions = [{ type = "page", by = -1 }]
              }
              page {
                material = "book"
                slot = 46
                name = "&7Page &f{page}&7/&f{pages}"
                bedrock.hidden = true
              }
              back {
                material = "arrow"
                slot = 49
                name = "&eBack"
                click.any.actions = [{ type = "open", menu = "$main" }]
              }
              next {
                material = "arrow"
                slot = 53
                name = "&eNext page"
                lore = ["&7Page {page_next} of {pages}"]
                bedrock.hidden = true
                view.require = [{ condition { type = "compare", left = "{page}", op = "LT", right = "{pages}" } }]
                click.any.actions = [{ type = "page", by = 1 }]
              }
            }
            """.trimIndent()
        )
    }

    private fun vanillaPlayer(main: String): String = buildString {
        append(header("A friend's own menu: opened by clicking them in the list.", "&8%xalifriends_selected_name%", 27))
        appendLine(
            """
            items {
              filler { material = "gray_stained_glass_pane", slots = [${(0 until 27).joinToString()}], priority = 100, name = " " }
              friend {
                material = "player_head"
                skull = "%xalifriends_selected_name%"
                slot = 13
                name = "%xalifriends_selected_marker% &f%xalifriends_selected_name%"
                lore = [
                  "%xalifriends_selected_seen%"
                  "&7Status: &f%xalifriends_selected_status%"
                  "&7Friends since &f%xalifriends_selected_since%"
                ]
                update = true
              }
              favorite_on {
                material = "yellow_dye"
                slot = 11
                priority = 0
                name = "&6Favorite"
                lore = ["&7Click to take them off your favorites"]
                update = true
                view.require = [{ condition { type = "xalifriends:selected", check = "favorite" } }]
                click.any.actions = [{ type = "xalifriends:favorite", player = "%xalifriends_selected_uuid%" }]
              }
              favorite_off {
                material = "gray_dye"
                slot = 11
                priority = 10
                name = "&eMake a favorite"
                lore = ["&7Favorites are listed first"]
                update = true
                click.any.actions = [{ type = "xalifriends:favorite", player = "%xalifriends_selected_uuid%" }]
              }
              remove {
                material = "barrier"
                slot = 15
                name = "&cRemove friend"
                lore = ["&7Asks you to confirm first"]
                click.any.actions = [{ type = "open", menu = "$REMOVE" }]
              }
              jump {
                material = "ender_pearl"
                slot = 22
                name = "&bJump to %xalifriends_selected_name%"
                lore = ["&7%xalifriends_selected_seen%", "", "&eClick to go to their server"]
                update = true
                view.require = [{ condition { type = "xalifriends:selected", check = "jumpable" } }]
                click.any.actions = [{ type = "xalifriends:jump", player = "%xalifriends_selected_uuid%" }]
              }
              back {
                material = "arrow"
                slot = 18
                name = "&eBack"
                click.any.actions = [{ type = "open", menu = "$main" }]
              }
            }
            """.trimIndent()
        )
    }

    private fun vanillaRemove(main: String): String = buildString {
        append(header("Asks before ending a friendship.", "&8Remove %xalifriends_selected_name%?", 27))
        appendLine(
            """
            bedrock {
              mode = "MODAL"
              title = "&8Remove %xalifriends_selected_name%?"
              content = "&7End the friendship with &f%xalifriends_selected_name%&7?"
              confirm {
                text = "&aRemove"
                actions = [{ type = "xalifriends:remove", player = "%xalifriends_selected_uuid%", after = "open:$main" }]
              }
              cancel {
                text = "&cKeep"
                actions = [{ type = "open", menu = "$PLAYER" }]
              }
            }
            items {
              filler { material = "gray_stained_glass_pane", slots = [${(0 until 27).joinToString()}], priority = 100, name = " " }
              friend {
                material = "player_head"
                skull = "%xalifriends_selected_name%"
                slot = 13
                name = "&f%xalifriends_selected_name%"
                lore = ["&7Friends since &f%xalifriends_selected_since%"]
              }
              confirm {
                material = "lime_concrete"
                slot = 11
                name = "&aRemove"
                lore = ["&7End the friendship with %xalifriends_selected_name%"]
                click.any.actions = [{ type = "xalifriends:remove", player = "%xalifriends_selected_uuid%", after = "open:$main" }]
              }
              cancel {
                material = "red_concrete"
                slot = 15
                name = "&cKeep"
                lore = ["&7Go back"]
                click.any.actions = [{ type = "open", menu = "$PLAYER" }]
              }
            }
            """.trimIndent()
        )
    }

    private fun vanillaSettings(main: String): String = buildString {
        append(header("Settings: each item flips one.", "&8Friend settings", 36))
        appendLine("items {")
        appendLine("  filler { material = \"gray_stained_glass_pane\", slots = [${(0 until 36).joinToString()}], priority = 100, name = \" \" }")
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
                """
                  ${toggle.key} {
                    material = "${toggle.material}"
                    slot = ${toggle.slot}
                    name = "&e${toggle.label}"
                    lore = ["&7Now: &f%xalifriends_setting_${toggle.key}%", "", "&eClick to change"]
                    update = true
                    click.any.actions = [{ type = "xalifriends:toggle", setting = "${toggle.key}" }]
                  }
                """.trimIndent().prependIndent("  ")
            )
        }
        appendLine(
            """
              status {
                material = "name_tag"
                slot = 22
                name = "&eYour status"
                lore = ["&f%xalifriends_status%", "", "&7Left-click to change it", "&7Right-click to clear it"]
                update = true
                bedrock { left-label = "&eChange status", right-label = "&cClear status" }
                click.left.actions = [
            ${statusInput(SETTINGS).prependIndent("      ")}
                ]
                click.right.actions = [{ type = "xalifriends:clear-status" }]
              }
              back {
                material = "arrow"
                slot = 31
                name = "&eBack"
                click.any.actions = [{ type = "open", menu = "$main" }]
              }
            """.trimIndent().prependIndent("  ")
        )
        appendLine("}")
    }

    private class Toggle(val key: String, val material: String, val slot: Int, val label: String)
}
