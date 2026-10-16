import net.paulem.buildscript.ModBuild

plugins {
    // This plugin applies the correct loom variant based on the Minecraft version
    id("dev.kikugie.loom-back-compat")
    id("com.modrinth.minotaur") version "2.+"
    id("mod-spotless")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-fabric"

val requiredJava: JavaVersion = ModBuild.requiredJava(sc.current.version)

// Placeholders expanded into the loader metadata file (fabric.mod.json / *.mods.toml)
val modMetadata: Map<String, String> = mapOf(
    "id" to sc.properties.get<String>("mod.id"),
    "name" to sc.properties.get<String>("mod.name"),
    "version" to sc.properties.get<String>("mod.version"),
    "description" to sc.properties.get<String>("mod.description"),
    "minecraft" to sc.properties.get<String>("mod.mc_compat"),
)

// This can be used for publishing on Modrinth and Curseforge
val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
    ?.asList().orEmpty().map { it.toString() }

ModBuild.repositories(repositories)

val midnightlibVersion = sc.dependencies["midnightlib"].orEmpty()
val hasMidnightLib = sc.constants["hasMidnightLib"] ?: false

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Applies Mojang Mappings on obfuscated versions
    loomx.applyMojangMappings()

    // Use `mod{dependency type}` even on 26.1+ - loom-back-compat converts them
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    if (hasMidnightLib) {
        val path = ModBuild.midnightLibNotation(midnightlibVersion)

        modImplementation(path)
        include(path)
    }
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json") // Useful for interface injection
    accessWidenerPath = sc.process(
        rootProject.file("src/main/resources/oushii.ct"),
        "build/processed.ct"
    )

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1") // Adds names to lambdas - useful for mixins
    }

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run") // Shares the run directory between versions
        jvmArguments.add("-Dmixin.debug.export=true") // Exports transformed classes for debugging
    }
}

ModBuild.configureJava(project, requiredJava)

tasks {
    processResources {
        val props = ModBuild.resourceProps(this, modMetadata)

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = ModBuild.mixinCompatibility(requiredJava)
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("META-INF/neoforge.mods.toml")
    }
}

// loomx.mod(Sources)Jar returns the jar task for the applied loom variant
ModBuild.registerBuildAndCollect(
    project,
    sc.properties["mod.version"],
    listOf(
        loomx.modJar.flatMap { it.archiveFile },
        loomx.modSourcesJar.flatMap { it.archiveFile },
    )
)

modrinth {
    token.set(ModBuild.secret(project, "MODRINTH_TOKEN"))
    changelog.set(ModBuild.changelog(project))
    projectId.set("oushii")
    versionNumber.set(project.version.toString())
    // Snapshot ids carry a qualifier ("26.3-snapshot-9"), releases never do
    versionType.set(if (sc.current.version.contains('-')) "beta" else "release")
    uploadFile.set(loomx.modJar)
    additionalFiles = listOf(loomx.modSourcesJar)
    gameVersions.addAll(sc.properties.raw("mod", "mc_releases").to<List<String>>())
    loaders.add("fabric")
    dependencies {
        required.project("fabric-api")
        if(hasMidnightLib) {
            embedded.version("midnightlib", midnightlibVersion)
        }
    }

    syncBodyFrom = rootProject.file("README.md").readText()

    debugMode = ModBuild.debugMode(project)
}

tasks.modrinth.get().dependsOn(tasks.modrinthSyncBody)

tasks.build {
    dependsOn("spotlessCheck")
}
