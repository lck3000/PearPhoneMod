plugins {
    id("java")
    id("maven-publish")
    id("net.neoforged.gradle.userdev") version "7.1.21"
    id("com.modrinth.minotaur") version "2.9.0"
    id("net.darkhax.curseforgegradle") version "1.1.28"
}

val modVersion: String by project
val minecraftVersion: String by project
val neoforgeVersion: String by project
val modId: String by project
val modName: String by project
val modAuthor: String by project
val modSourcesUrl: String by project
val modIssuesUrl: String by project
val modrinthProjectId: String by project
val curseforgeProjectId: String by project

version = modVersion
group = "com.pearphone.mod"

base {
    archivesName = "$modId-$minecraftVersion-neoforge"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases")
}

dependencies {
    implementation("net.neoforged:neoforge:$neoforgeVersion")
    implementation("org.jsoup:jsoup:1.17.2")
}

// Apply texture generation tasks from Groovy script
apply(from = "build_textures.gradle")

tasks.named<Jar>("jar") {
    dependsOn("generateTextures")
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to modName,
                "Specification-Vendor" to modAuthor,
                "Specification-Version" to "1",
                "Implementation-Title" to modName,
                "Implementation-Version" to modVersion,
                "Implementation-Vendor" to modAuthor,
                "Implementation-Timestamp" to System.currentTimeMillis().toString()
            )
        )
    }
}

// ── Modrinth ────────────────────────────────────────────────────────────────
modrinth {
    token = System.getenv("MODRINTH_TOKEN") ?: "undefined"
    projectId = modrinthProjectId
    versionNumber = modVersion
    versionType = "release"              // "alpha" | "beta" | "release"
    uploadFile.set(tasks.jar)
    gameVersions = listOf(minecraftVersion)
    loaders = listOf("neoforge")
    changelog = System.getenv("CHANGELOG") ?: ""
    syncBodyFrom = rootProject.file("MODRINTH.md").takeIf { it.exists() }?.readText()
}

tasks.named("modrinth") {
    dependsOn(tasks.jar)
}

// ── CurseForge ──────────────────────────────────────────────────────────────
tasks.register<net.darkhax.curseforgegradle.TaskPublishCurseForge>("publishCurseForge") {
    dependsOn(tasks.jar)

    apiToken = System.getenv("CURSEFORGE_TOKEN") ?: "undefined"

    val mainFile = upload(curseforgeProjectId, tasks.jar.get().archiveFile)
    mainFile.apply {
        displayName = "$modName $modVersion"
        releaseType = "release"         // "alpha" | "beta" | "release"
        addGameVersion(minecraftVersion)
        addModLoader("NeoForge")
        changelog = System.getenv("CHANGELOG") ?: ""
        changelogType = "markdown"
    }
}

// ── Maven publish ────────────────────────────────────────────────────────────
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = modName
                url = modSourcesUrl
                licenses {
                    license { name = "MIT" }
                }
            }
        }
    }
    repositories {
        maven {
            url = uri("file://${project.projectDir}/mcmodsrepo")
        }
    }
}
