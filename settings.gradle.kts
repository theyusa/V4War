pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    plugins {
        id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
        id("org.jetbrains.compose") version "1.10.2"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":composeApp")

if (file("library/compose-code-editor/codeeditor").isDirectory) {
    include(":library:compose-code-editor:codeeditor")
}
if (file("library/DragDropSwipeLazyColumn").isDirectory) {
    include(":library:DragDropSwipeLazyColumn")
}

rootProject.name = "V4War"
