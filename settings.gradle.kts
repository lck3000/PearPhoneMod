pluginManagement {
    repositories {
        gradlePluginPortal()                         // minotaur + curseforgegradle
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.neoforged.net/snapshots")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        maven("https://libraries.minecraft.net")
    }
}

rootProject.name = "pearphone"
