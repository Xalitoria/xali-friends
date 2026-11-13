import mc.xalitoria.friends.paper.external.After
import mc.xalitoria.friends.paper.external.DefaultMenus
import mc.xalitoria.friends.paper.external.FriendActionKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MenuActions {
    @Test
    fun `after reads the four forms`() {
        assertEquals(After.Refresh, After.parse("refresh"))
        assertEquals(After.Close, After.parse("CLOSE"))
        assertEquals(After.Leave, After.parse("none"))
        assertEquals(After.Open("friends"), After.parse("open:friends"))
    }

    @Test
    fun `after refuses anything else`() {
        assertFailsWith<IllegalArgumentException> { After.parse("later") }
        assertFailsWith<IllegalArgumentException> { After.parse("open:") }
    }

    @Test
    fun `list, move and setting are checked when the menu is read`() {
        assertTrue(FriendActionKinds.list("Friends"))
        assertEquals(false, FriendActionKinds.list("requests"))
        assertFailsWith<IllegalArgumentException> { FriendActionKinds.list("blocked") }
        assertEquals("previous", FriendActionKinds.move("Previous"))
        assertFailsWith<IllegalArgumentException> { FriendActionKinds.move("last") }
        assertEquals("msgs", FriendActionKinds.setting("msgs").key)
        assertFailsWith<IllegalArgumentException> { FriendActionKinds.setting("nothing") }
    }

    @Test
    fun `the written menus click through registered actions and every toggle is a setting`() {
        val known = FriendActionKinds.TARGETED.keys + listOf("toggle", "clear-status", "select", "page")
        val menus = DefaultMenus.all("friends", 21, 21)
        val used = Regex("""type = "xalifriends:([a-z-]+)"""").findAll(menus.values.joinToString("\n")).map { it.groupValues[1] }.toSet()
        assertTrue(used.isNotEmpty())
        assertTrue(known.containsAll(used), "unknown actions: ${used - known}")
        // The only command left is the one that asks for a status in chat.
        val commands = Regex("""command = "([^"]+)"""").findAll(menus.values.joinToString("\n")).map { it.groupValues[1] }.toList()
        assertEquals(listOf("xfriend status"), commands)
        Regex("""type = "xalifriends:toggle", setting = "(\w+)"""").findAll(menus.getValue("friends-settings")).forEach {
            FriendActionKinds.setting(it.groupValues[1])
        }
    }
}
