plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":friends:common"))
    compileOnly(libs.velocity.api)
}

tasks {
    jar { enabled = false }
    build {
        dependsOn(shadowJar)
    }
}
