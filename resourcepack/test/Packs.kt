import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What can be checked about the packs without a game client: that every file reads, that every model, texture, glyph and
 * item they name is there, and that the three packs agree on the glyphs. The pack formats (26.1 is 84.0, 26.2 is 88.0,
 * 26.3 is 97.1) and the vanilla files the packs lean on were compared in the client jars of those versions.
 */
private class Source(val prefix: String, val dir: String)

private class Glyph(val char: String, val file: String, val ascent: Int, val height: Int)

class Packs {
    private val root = File(".").absoluteFile.normalize()
    private val vanilla = File(root, "vanilla")
    private val nexo = File(root, "nexo")
    private val itemsAdder = File(root, "itemsadder")

    /** Models and textures of the game itself that the packs build on. */
    private val vanillaModels = setOf("item/generated", "item/paper", "item/template_skull", "builtin/entity")
    private val vanillaTexturePrefixes = listOf("block/", "item/", "entity/")

    private fun json(file: File): JsonElement = Json.parseToJsonElement(file.readText())

    private fun jsonFiles(dir: File) = dir.walkTopDown().filter { it.isFile && it.extension in setOf("json", "mcmeta") }.toList()

    private fun name(id: String) = id.substringAfter(':')

    private fun yaml(file: File): YamlMap = Yaml.default.parseToYamlNode(file.readText()) as YamlMap

    private fun YamlMap.map(key: String): YamlMap? = get<YamlNode>(key) as? YamlMap

    private fun YamlMap.text(key: String): String? = (get<YamlNode>(key) as? YamlScalar)?.content

    private fun YamlMap.list(key: String): List<String> = (get<YamlNode>(key) as? YamlList)?.items?.map { (it as YamlScalar).content }.orEmpty()

    @Test
    fun everyJsonFileReads() {
        val files = jsonFiles(vanilla) + jsonFiles(nexo) + jsonFiles(itemsAdder)
        assertTrue(files.size > 100)
        files.forEach { json(it) }
    }

    @Test
    fun theFormatRangeCoversEveryPack26Version() {
        val pack = json(File(vanilla, "pack.mcmeta")).jsonObject.getValue("pack").jsonObject
        // 26.1 and its patches are 84.0, 26.2 is 88.0 and 26.3 is 97.1. A bare number as max_format stands for all its minors.
        assertEquals(84, pack.getValue("min_format").jsonPrimitive.int)
        assertEquals(97, pack.getValue("max_format").jsonPrimitive.int)
        // Both are deprecated from format 65 on and refused beside min_format and max_format.
        assertTrue("pack_format" !in pack && "supported_formats" !in pack)
        assertTrue(pack.getValue("description").jsonPrimitive.content.isNotBlank())
    }

    @Test
    fun nothingLegacyIsLeft() {
        // Item models lost their `overrides` in 1.21.4; the items/ definitions are what picks a model now.
        jsonFiles(File(vanilla, "assets")).forEach { assertTrue("overrides" !in it.readText(), "${it.relativeTo(root)} has overrides") }
        assertTrue(!File(vanilla, "assets/minecraft/models/item").exists() && !File(vanilla, "assets/minecraft/models/block").exists())
    }

    // ---- vanilla

    private fun vanillaModelFile(id: String) = File(vanilla, "assets/${id.substringBefore(':', "minecraft")}/models/${name(id)}.json")

    private fun modelsOf(element: JsonElement): List<String> = when (element) {
        is JsonObject -> element.flatMap { (key, value) ->
            if ((key == "model" || key == "base") && value is JsonPrimitive) listOf(value.content) else modelsOf(value)
        }
        is JsonArray -> element.flatMap(::modelsOf)
        else -> emptyList()
    }

    @Test
    fun itemDefinitionsPickModelsThatExist() {
        val definitions = File(vanilla, "assets/minecraft/items").listFiles { f -> f.extension == "json" }.orEmpty()
        assertEquals(setOf("paper.json", "player_head.json"), definitions.map { it.name }.toSet())
        for (file in definitions) {
            val root = json(file).jsonObject
            assertTrue(root["oversized_in_gui"]?.jsonPrimitive?.boolean == true, "${file.name} should allow oversized GUI models")
            val model = root.getValue("model").jsonObject
            if (model.getValue("type").jsonPrimitive.content == "minecraft:range_dispatch") {
                assertEquals("minecraft:custom_model_data", model.getValue("property").jsonPrimitive.content)
                val thresholds = model.getValue("entries").jsonArray.map { it.jsonObject.getValue("threshold").jsonPrimitive.int }
                assertEquals(thresholds.sorted().distinct(), thresholds, "${file.name}: thresholds ascend")
            }
            for (id in modelsOf(model).filter { it != "minecraft:player_head" }) {
                val known = name(id) in vanillaModels || vanillaModelFile(id).isFile
                assertTrue(known, "${file.name} names the model $id, which is not there")
            }
        }
    }

    @Test
    fun modelsHaveTheirTexturesInAnAtlas() {
        val atlas = json(File(vanilla, "assets/minecraft/atlases/items.json")).jsonObject.getValue("sources").jsonArray.map { it.jsonObject }
        val directories = atlas.filter { it.getValue("type").jsonPrimitive.content == "minecraft:directory" }
            .map { Source(it.getValue("prefix").jsonPrimitive.content, it.getValue("source").jsonPrimitive.content) }
        val singles = atlas.filter { it.getValue("type").jsonPrimitive.content == "minecraft:single" }.map { name(it.getValue("resource").jsonPrimitive.content) }
        for (source in directories) assertTrue(File(vanilla, "assets/minecraft/textures/${source.dir}").isDirectory, "atlas directory ${source.dir}")
        for (single in singles) assertTrue(File(vanilla, "assets/minecraft/textures/$single.png").isFile, "atlas texture $single")

        for (file in jsonFiles(File(vanilla, "assets/minecraft/models")) + jsonFiles(File(vanilla, "assets/friends/models"))) {
            val model = json(file).jsonObject
            model["parent"]?.jsonPrimitive?.content?.let { parent ->
                assertTrue(name(parent) in vanillaModels || vanillaModelFile(parent).isFile, "${file.name}: parent $parent")
            }
            val textures = model["textures"]?.jsonObject.orEmpty().values.map { name(it.jsonPrimitive.content) }.filter { !it.startsWith("#") }
            for (texture in textures) {
                if (vanillaTexturePrefixes.any { texture.startsWith(it) }) continue
                assertTrue(File(vanilla, "assets/minecraft/textures/$texture.png").isFile, "${file.name}: texture $texture is missing")
                val covered = texture in singles || directories.any { it.dir.isNotEmpty() && texture.startsWith(it.prefix) && File(vanilla, "assets/minecraft/textures/${it.dir}/${texture.removePrefix(it.prefix)}.png").isFile }
                assertTrue(covered, "${file.name}: texture $texture is in no atlas source")
            }
        }
    }

    private fun glyphs(): List<Glyph> =
        json(File(vanilla, "assets/minecraft/font/default.json")).jsonObject.getValue("providers").jsonArray.map {
            val provider = it.jsonObject
            assertEquals("bitmap", provider.getValue("type").jsonPrimitive.content)
            val chars = provider.getValue("chars").jsonArray.map { c -> c.jsonPrimitive.content }
            assertTrue(chars.size == 1 && chars.single().codePointCount(0, chars.single().length) == 1, "one character per glyph: $chars")
            val ascent = provider.getValue("ascent").jsonPrimitive.int
            val height = provider.getValue("height").jsonPrimitive.int
            assertTrue(ascent <= height, "ascent ($ascent) is at most the height ($height)")
            Glyph(chars.single(), provider.getValue("file").jsonPrimitive.content, ascent, height)
        }

    @Test
    fun glyphsPointAtTextures() {
        val glyphs = glyphs()
        assertEquals(glyphs.size, glyphs.map { it.char }.distinct().size, "no character twice")
        for (glyph in glyphs) assertTrue(File(vanilla, "assets/minecraft/textures/${glyph.file}").isFile, "glyph texture ${glyph.file}")
    }

    @Test
    fun languageFilesAreFlat() {
        for (file in File(vanilla, "assets/minecraft/lang").listFiles().orEmpty()) {
            assertTrue(json(file).jsonObject.values.all { it is JsonPrimitive }, file.name)
        }
    }

    // ---- nexo

    @Test
    fun nexoItemsHaveTheirFiles() {
        val items = yaml(File(nexo, "items/friends.yml"))
        assertEquals(19, items.entries.size)
        val wideButtons = setOf("friend_accept", "friend_deny", "friend_invite", "friend_jump", "friend_send", "friend_teleport")
        for ((key, value) in items.entries) {
            val pack = (value as YamlMap).map("Pack") ?: error("${key.content} has no Pack")
            pack.text("texture")?.let { assertTrue(File(nexo, "pack/assets/minecraft/textures/$it").isFile, "${key.content}: texture $it") }
            pack.text("model")?.let { assertTrue(File(nexo, "pack/assets/minecraft/models/$it.json").isFile, "${key.content}: model $it") }
            assertTrue(pack.text("texture") != null || pack.text("model") != null, "${key.content} names a texture or a model")
            if (key.content in wideButtons) {
                assertEquals("true", pack.text("oversized_in_gui"), "${key.content} should set Pack.oversized_in_gui: true")
            }
        }
        val nexoHead = json(File(nexo, "pack/assets/minecraft/items/player_head.json")).jsonObject
        assertTrue(nexoHead["oversized_in_gui"]?.jsonPrimitive?.boolean == true, "nexo player_head.json should set oversized_in_gui: true")
    }

    @Test
    fun nexoModelsHaveTheirTextures() {
        for (file in jsonFiles(File(nexo, "pack/assets/minecraft/models"))) {
            for (texture in json(file).jsonObject["textures"]?.jsonObject.orEmpty().values) {
                val id = name(texture.jsonPrimitive.content)
                if (vanillaTexturePrefixes.any { id.startsWith(it) }) continue
                assertTrue(File(nexo, "pack/assets/minecraft/textures/$id.png").isFile, "${file.name}: texture $id")
            }
        }
    }

    @Test
    fun nexoGlyphsAreTheVanillaOnes() {
        val ours = yaml(File(nexo, "glyphs/friends.yml")).entries.values.map {
            val glyph = it as YamlMap
            Glyph(glyph.text("char")!!, glyph.text("texture")!!, glyph.text("ascent")!!.toInt(), glyph.text("height")!!.toInt())
        }
        for (glyph in ours) assertTrue(File(nexo, "pack/assets/minecraft/textures/${glyph.file}").isFile, "glyph texture ${glyph.file}")
        fun Glyph.key() = "$char $ascent $height ${file.removeSuffix(".png")}"
        assertEquals(glyphs().map { it.key() }.toSet(), ours.map { it.key() }.toSet())
    }

    @Test
    fun nexoHeadsHaveAnOverlayForTheClientsThatCallThemHead() {
        // Nexo's pack covers clients down to 1.21.4 (format 46) when ViaBackwards is installed. Those up to format 62 (1.21.5)
        // know the player head as `head` with kind `player`; `player_head` is from format 63 (1.21.6).
        val dir = File(nexo, "pack/external_packs/friends-gui")
        val entry = json(File(dir, "pack.mcmeta")).jsonObject.getValue("overlays").jsonObject.getValue("entries").jsonArray.single().jsonObject
        assertEquals(listOf(46, 62), entry.getValue("formats").jsonArray.map { it.jsonPrimitive.int })
        val overlay = json(File(dir, "${entry.getValue("directory").jsonPrimitive.content}/assets/minecraft/items/player_head.json")).jsonObject

        fun models(root: JsonObject) = root.getValue("model").jsonObject.let { dispatch ->
            dispatch.getValue("entries").jsonArray.associate {
                it.jsonObject.getValue("threshold").jsonPrimitive.int to it.jsonObject.getValue("model").jsonObject
            } + (-1 to dispatch.getValue("fallback").jsonObject)
        }
        val current = models(json(File(nexo, "pack/assets/minecraft/items/player_head.json")).jsonObject)
        val old = models(overlay)
        assertEquals(current.keys, old.keys)
        for (key in current.keys) {
            assertEquals(current.getValue(key).getValue("base"), old.getValue(key).getValue("base"), "base of $key")
            assertEquals("minecraft:player_head", current.getValue(key).getValue("model").jsonObject.getValue("type").jsonPrimitive.content)
            assertEquals(
                mapOf("type" to "minecraft:head", "kind" to "player"),
                old.getValue(key).getValue("model").jsonObject.mapValues { it.value.jsonPrimitive.content },
            )
        }
    }

    // ---- itemsadder

    @Test
    fun itemsAdderItemsAndGlyphsHaveTheirFiles() {
        val config = yaml(File(itemsAdder, "contents/friendsystem/configs/items.yml"))
        val textures = File(itemsAdder, "contents/friendsystem/textures")
        val images = config.map("font_images")!!.entries.values.map { it as YamlMap }
        // The shift characters are blank and only here and in Oraxen; the rest are the vanilla glyphs.
        for (image in images) assertTrue(File(textures, image.text("path")!!).isFile, "font image ${image.text("path")}")
        // y_position is the glyph's ascent and scale_ratio its height.
        val ours = images.map { Glyph(it.text("symbol")!!, it.text("path")!!, it.text("y_position")!!.toInt(), it.text("scale_ratio")!!.toInt()) }
        fun Glyph.key() = "$char $ascent $height ${file.removeSuffix(".png")}"
        assertEquals(glyphs().map { it.key() }.toSet(), ours.map { it.key() }.toSet())
        for ((key, value) in config.map("items")!!.entries) {
            val resource = (value as YamlMap).map("resource") ?: error("${key.content} has no resource")
            resource.list("textures").forEach { assertTrue(File(textures, it).isFile, "${key.content}: texture $it") }
            resource.text("model_path")?.let { assertTrue(File(itemsAdder, "contents/friendsystem/models/$it").isFile, "${key.content}: model $it") }
        }
    }

    @Test
    fun itemsAdderModelsHaveTheirTextures() {
        for (file in jsonFiles(File(itemsAdder, "contents/friendsystem/models"))) {
            for (texture in json(file).jsonObject["textures"]?.jsonObject.orEmpty().values) {
                val value = texture.jsonPrimitive.content
                if (value.startsWith("#") || vanillaTexturePrefixes.any { name(value).startsWith(it) }) continue
                assertTrue(File(itemsAdder, "contents/friendsystem/textures/${name(value)}.png").isFile, "${file.name}: texture $value")
            }
        }
    }
}
