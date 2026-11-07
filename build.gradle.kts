import mc.xalitoria.gradle.Load
import mc.xalitoria.gradle.RelativeLoad

plugins {
    kotlin("jvm")
    id("mc.xalitoria.conventions")
    id("mc.xalitoria.minecraft")
    alias(libs.plugins.shadow) apply false
}

subprojects {
    // Named for the package of what the module holds, see the Packages section of the root README.
    group = when (name) {
        "common" -> "com.xalitoria.friends"
        "paper" -> "mc.xalitoria.friends.paper"
        "velocity" -> "mc.xalitoria.friends.velocity"
        "resourcepack" -> "com.xalitoria.friends"
        else -> error("No group for $path")
    }
    version = "0.1.0"
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}

paper {
    name = "XaliFriends"
    main = "mc.xalitoria.friends.paper.XaliFriends"
    prefix = "XaliFriends"
    author = "audizian"
    website = "https://xalitoria.com"
    apiVersion = "26.2"
    load = Load.POSTWORLD
    foliaSupported = true
    relocateSources = true
    serverDependencies {
        // All three load first: the menus and the message gate are registered while this plugin enables.
        register("PlaceholderAPI") { load = RelativeLoad.BEFORE; required = false }
        register("XaliMenu") { load = RelativeLoad.BEFORE; required = false }
        register("XaliChat") { load = RelativeLoad.BEFORE; required = false }
        // The pack installer writes into whichever of these packs.handler names, so it has to have enabled.
        register("Nexo") { load = RelativeLoad.BEFORE; required = false }
        register("ItemsAdder") { load = RelativeLoad.BEFORE; required = false }
    }
}

velocity {
    name = "XaliFriends"
    id = "xalifriends"
    main = "mc.xalitoria.friends.velocity.XaliFriends"
    author = "audizian"
    url = "https://xalitoria.com"
    description = "Tells the servers when a friend joins, leaves or switches server."
    relocateSources = true
}
