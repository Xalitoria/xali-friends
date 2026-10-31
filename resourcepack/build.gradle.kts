import java.security.MessageDigest

// The GUI packs of the friend menus, one per place they can be installed: `vanilla` (a resource pack to host or set in
// server.properties), `nexo` (the contents of plugins/Nexo) and `itemsadder` (the contents of plugins/ItemsAdder). The
// folders are the sources; each is zipped, with its SHA-256 beside it, into build/libs/friends-packs, ready to be attached
// to a release for XaliFriends' pack installer to download. The tests check them.
dependencies {
    testImplementation(libs.kotlinx.serialization)
    testImplementation(libs.kaml)
}

tasks.test {
    useJUnitPlatform()
}

val output = rootProject.layout.buildDirectory.dir("libs/friends-packs")

val zips = listOf("vanilla", "nexo", "itemsadder").map { name ->
    tasks.register<Zip>("zip${name.replaceFirstChar(Char::uppercase)}Pack") {
        group = "build"
        description = "Zips the $name pack to friends-gui-$name.zip, with its checksum."
        from(layout.projectDirectory.dir(name))
        archiveFileName = "friends-gui-$name.zip"
        destinationDirectory = output
        // The same files make the same zip, so a checksum only changes when the pack does.
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        val archive = archiveFile
        doLast {
            val file = archive.get().asFile
            val sum = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            file.resolveSibling(file.name + ".sha256").writeText("$sum  ${file.name}\n")
        }
    }
}

tasks {
    jar { enabled = false }
    register("zipPacks") {
        group = "build"
        description = "Zips every pack."
        dependsOn(zips)
    }
    build {
        dependsOn("zipPacks")
    }
}
