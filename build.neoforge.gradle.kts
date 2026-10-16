import net.paulem.buildscript.ModBuild

plugins {
    id("net.neoforged.moddev") version "2.0.148"
    id("neoforge-mutex")
    id("com.modrinth.minotaur") version "2.+"
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-neoforge"

val requiredJava: JavaVersion = ModBuild.requiredJava(sc.current.version)

// Placeholders expanded into the loader metadata file (fabric.mod.json / *.mods.toml)
val modMetadata: Map<String, String> = mapOf(
    "id" to sc.properties.get<String>("mod.id"),
    "name" to sc.properties.get<String>("mod.name"),
    "version" to sc.properties.get<String>("mod.version"),
    "description" to sc.properties.get<String>("mod.description"),
    "minecraft" to sc.properties.get<String>("mod.mc_compat"),
)

ModBuild.repositories(repositories)

val midnightlibVersion = sc.dependencies["midnightlib"].orEmpty()
val hasMidnightLib = sc.constants["hasMidnightLib"] ?: false

dependencies {
    if (hasMidnightLib) {
        val path = ModBuild.midnightLibNotation(midnightlibVersion)

        implementation(path)
        jarJar(path)
    }
}

neoForge {
    version = property("deps.neo_loader") as String

    mods {
        register("oushii") {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            gameDirectory = file("../../run/")
            client()
        }

        register("server") {
            gameDirectory = file("../../run/")
            server()
        }
    }
}

ModBuild.configureJava(project, requiredJava)

tasks {
    processResources {
        val props = ModBuild.resourceProps(this, modMetadata)

        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }

        val mixinJava = ModBuild.mixinCompatibility(requiredJava)
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("fabric.mod.json", "META-INF/mods.toml", "*.ct", "*.classtweaker")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }
}

ModBuild.registerBuildAndCollect(
    project,
    sc.properties["mod.version"],
    listOf(
        tasks.jar.flatMap { it.archiveFile },
        tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile },
    )
)

modrinth {
    token.set(ModBuild.secret(project, "MODRINTH_TOKEN"))
    changelog.set(ModBuild.changelog(project))
    projectId.set("oushii")
    versionNumber.set(project.version.toString())
    versionType.set("release")
    uploadFile.set(tasks.jar)
    additionalFiles = listOf(tasks.named<Jar>("sourcesJar"))
    gameVersions.addAll(sc.properties.raw("mod", "mc_releases").to<List<String>>())
    loaders.add("neoforge")
    dependencies {
        if(hasMidnightLib) {
            embedded.version("midnightlib", midnightlibVersion)
        }
    }

    syncBodyFrom = rootProject.file("README.md").readText()

    debugMode = ModBuild.debugMode(project)
}

tasks.modrinth.get().dependsOn(tasks.modrinthSyncBody)

// Spotless runs on the root project, where the shared sources live
tasks.build {
    dependsOn(rootProject.tasks.named("spotlessCheck"))
}
