import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import mc.xalitoria.friends.paper.data.config.MainConfig
import mc.xalitoria.friends.paper.data.db.repository.SettingKey
import mc.xalitoria.friends.paper.external.After
import mc.xalitoria.friends.paper.external.DefaultMenus
import mc.xalitoria.friends.paper.external.FriendActionKinds
import mc.xalitoria.friends.paper.external.FriendMenuKinds
import java.io.File
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
    fun `catalog and condition settings are checked when the menu is read`() {
        assertEquals("online", FriendMenuKinds.filter("ONLINE"))
        assertFailsWith<IllegalArgumentException> { FriendMenuKinds.filter("blocked") }
        assertEquals(7, FriendMenuKinds.repeat(7))
        assertFailsWith<IllegalArgumentException> { FriendMenuKinds.repeat(0) }
        assertFailsWith<IllegalArgumentException> { FriendMenuKinds.repeat(10) }
        assertEquals("on", FriendMenuKinds.settingValue(SettingKey.INVITES, "ON"))
        assertFailsWith<IllegalArgumentException> { FriendMenuKinds.settingValue(SettingKey.INVITES, "friends") }
        assertEquals("friends", FriendMenuKinds.settingValue(SettingKey.MSGS, "friends"))
        assertFailsWith<IllegalArgumentException> { FriendMenuKinds.settingValue(SettingKey.MSGS, "on") }
        assertEquals("favorite", FriendMenuKinds.selectedCheck("Favorite"))
        assertFailsWith<IllegalArgumentException> { FriendMenuKinds.selectedCheck("unknown") }
        assertEquals("Online on Survival", FriendMenuKinds.plain("&aOnline &7on Survival"))
    }

    @Test
    fun `menu style follows packs handler when auto`() {
        assertEquals(MainConfig.Gui.Style.VANILLA, MainConfig().menuStyle())
        assertEquals(
            MainConfig.Gui.Style.NEXO,
            MainConfig(packs = MainConfig.Packs(handler = MainConfig.Packs.Handler.NEXO)).menuStyle(),
        )
        assertEquals(
            MainConfig.Gui.Style.VANILLA,
            MainConfig(
                gui = MainConfig.Gui(style = MainConfig.Gui.Style.VANILLA),
                packs = MainConfig.Packs(handler = MainConfig.Packs.Handler.NEXO),
            ).menuStyle(),
        )
        assertEquals(
            MainConfig.Gui.Style.NEXO,
            MainConfig(gui = MainConfig.Gui(style = MainConfig.Gui.Style.NEXO)).menuStyle(),
        )
    }

    @Test
    fun `both menu styles use only registered actions, catalogs, conditions and valid targets`() {
        val knownActions = FriendActionKinds.TARGETED.keys + listOf("toggle", "status", "clear-status", "select", "page")
        val knownCatalogs = setOf(FriendMenuKinds.FRIENDS_CATALOG, FriendMenuKinds.REQUESTS_CATALOG)
        val knownConditions = setOf(
            FriendMenuKinds.SETTING_CONDITION,
            FriendMenuKinds.HAS_REQUESTS_CONDITION,
            FriendMenuKinds.SELECTED_CONDITION,
        )

        for (style in listOf(MainConfig.Gui.Style.VANILLA, MainConfig.Gui.Style.NEXO)) {
            val menus = DefaultMenus.all("friends", style)
            val allText = menus.values.joinToString("\n")

            val usedAll = Regex("""type\s*=\s*"(xalifriends:[a-z-]+)"""").findAll(allText).map { it.groupValues[1] }.toSet()
            assertTrue(usedAll.isNotEmpty(), "$style: expected xalifriends types")
            val allKnown = knownActions.map { FriendActionKinds.PREFIX + it }.toSet() + knownCatalogs + knownConditions
            assertTrue(allKnown.containsAll(usedAll), "$style: unknown xalifriends types: ${usedAll - allKnown}")

            // Catalogs are used in both styles; no raw player command hack remains.
            assertTrue(FriendMenuKinds.FRIENDS_CATALOG in usedAll, "$style: friends catalog missing")
            assertTrue(FriendMenuKinds.REQUESTS_CATALOG in usedAll, "$style: requests catalog missing")
            assertTrue("command = \"xfriend" !in allText, "$style: legacy /xfriend command still present")

            // Every toggle and setting condition names a real SettingKey and valid value.
            Regex("""type\s*=\s*"xalifriends:toggle",\s*setting\s*=\s*"(\w+)"""").findAll(allText).forEach {
                FriendActionKinds.setting(it.groupValues[1])
            }
            Regex("""type\s*=\s*"xalifriends:setting",\s*setting\s*=\s*"(\w+)",\s*value\s*=\s*"(\w+)"""").findAll(allText).forEach {
                val key = FriendActionKinds.setting(it.groupValues[1])
                FriendMenuKinds.settingValue(key, it.groupValues[2])
            }
            Regex("""type\s*=\s*"xalifriends:selected",\s*check\s*=\s*"(\w+)"""").findAll(allText).forEach {
                FriendMenuKinds.selectedCheck(it.groupValues[1])
            }

            // Every `open` target (both `{ type = "open", menu = "..." }` and `after = "open:..."`) exists in this style.
            val openTargets = Regex("""menu\s*=\s*"([^"]+)"""").findAll(allText).map { it.groupValues[1] }.toSet() +
                Regex("""after\s*=\s*"open:([^"]+)"""").findAll(allText).map { it.groupValues[1] }.toSet()
            assertTrue(menus.keys.containsAll(openTargets), "$style: unknown menu targets: ${openTargets - menus.keys}")
        }
    }

    @Test
    fun `nexo menus reference only items and glyphs shipped in the nexo resource pack`() {
        val menus = DefaultMenus.all("friends", MainConfig.Gui.Style.NEXO)
        assertEquals(
            setOf("friends", "friends-requests", "friends-player", "friends-remove", "friends-settings", "friends-settings2"),
            menus.keys,
        )

        val packDir = File("../resourcepack/nexo")
        val itemsYaml = Yaml.default.parseToYamlNode(File(packDir, "items/friends.yml").readText()) as YamlMap
        val packItems = itemsYaml.entries.keys.map { it.content }.toSet()
        val glyphsYaml = Yaml.default.parseToYamlNode(File(packDir, "glyphs/friends.yml").readText()) as YamlMap
        val packGlyphChars = glyphsYaml.entries.values
            .mapNotNull { ((it as? YamlMap)?.get<YamlNode>("char") as? YamlScalar)?.content }
            .flatMap { it.toList() }
            .toSet()

        val allText = menus.values.joinToString("\n")
        val usedItems = Regex("""material\s*=\s*"nexo:([^"]+)"""").findAll(allText).map { it.groupValues[1] }.toSet()
        assertTrue(usedItems.isNotEmpty(), "nexo menus should use nexo items")
        assertTrue(packItems.containsAll(usedItems), "missing nexo items in pack: ${usedItems - packItems}")

        for ([name, text] in menus) {
            val titleMatch = Regex("""^title\s*=\s*"([^"]+)"""", RegexOption.MULTILINE).find(text)
                ?: error("$name has no title")
            val title = titleMatch.groupValues[1]
            assertTrue("<font:nexo:default>" in title, "$name title should set nexo:default font: $title")
            val glyphRun = Regex("""<font:nexo:default>([^<]+)</font>""").find(title)?.groupValues?.get(1)
                ?: error("$name title has no glyph run: $title")
            assertTrue(glyphRun.all { it in packGlyphChars }, "$name uses unknown glyph chars in '$glyphRun'")
            assertTrue("bedrock {" in text, "$name should configure a Bedrock title/mode")
        }
    }
}
