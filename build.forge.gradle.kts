import net.paulem.buildscript.ModBuild

plugins {
    id("net.minecraftforge.gradle") version "7.+"
    id("net.minecraftforge.jarjar") version "0.2.3"
    id("net.minecraftforge.renamer") version "1.1.8"
    id("neoforge-mutex")
    id("com.modrinth.minotaur") version "2.+"
    id("mod-spotless")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-forge"

val requiredJava: JavaVersion = ModBuild.requiredJava(sc.current.version)

// Placeholders expanded into the loader metadata file (fabric.mod.json / *.mods.toml)
val modMetadata: Map<String, String> = mapOf(
    "id" to sc.properties.get<String>("mod.id"),
    "name" to sc.properties.get<String>("mod.name"),
    "version" to sc.properties.get<String>("mod.version"),
    "description" to sc.properties.get<String>("mod.description"),
    "minecraft" to sc.properties.get<String>("mod.mc_compat"),
)

repositories {
    maven { minecraft.mavenizer.execute(this) }
    maven { fg.forgeMaven.execute(this) }
    maven { fg.minecraftLibsMaven.execute(this) }

    maven("https://repo.spongepowered.org/maven")
}

ModBuild.repositories(repositories)

val midnightlibVersion = sc.dependencies["midnightlib"].orEmpty()
val hasMidnightLib = sc.constants["hasMidnightLib"] ?: false

minecraft {
    mappings("official", sc.current.version)

    runs {
        configureEach {
            jvmArgs(
                "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
                "--add-exports", "cpw.mods.bootstraplauncher/cpw.mods.bootstraplauncher=ALL-UNNAMED"
            )
        }

        create("client").apply {
            workingDir.set(project.file("../../run"))
            systemProperty("forge.logging.console.level", "debug")

            mods.create(project.property("mod.id") as String).apply {
                source(sourceSets.main.get())
            }
        }

        create("server").apply {
            workingDir.set(project.file("../../run"))
            systemProperty("forge.logging.console.level", "debug")

            mods.create(project.property("mod.id") as String).apply {
                source(sourceSets.main.get())
            }
        }
    }
}

val jarJarExtension = the<net.minecraftforge.jarjar.gradle.JarJarExtension>()
val jarJarConfig = jarJarExtension.register().configurationName

val forgeDependency = minecraft.dependency("net.minecraftforge:forge:${sc.current.version}-${sc.properties.get<String>("deps.forge_loader")}")

dependencies {
    implementation(forgeDependency)
    annotationProcessor("org.spongepowered:mixin:0.8.7:processor")

    if (hasMidnightLib) {
        val notation = ModBuild.midnightLibNotation(midnightlibVersion)

        implementation(notation)
        val midnightLibJarJarDep = add(jarJarConfig, notation)

        // MidnightLib's Gradle coordinate (e.g. "1.3.0-forge" or "1.9.2+1.20.1-forge")
        // never matches the plain semver its own mods.toml declares (e.g. "1.3.0"/"1.9.2").
        // ForgeGradle's JarJar plugin derives the embed version range straight from the
        // coordinate string, so without this override the generated range never matches
        // and Forge silently refuses to load the embedded jar.
        if (midnightLibJarJarDep != null) {
            jarJarExtension.configure(midnightLibJarJarDep) {
                setRange("[${midnightlibVersion.substringBefore("+").removeSuffix("-forge")},)")
            }
        }
    }

    // MixinExtras
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.5")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.5")
    implementation("io.github.llamalad7:mixinextras-forge:0.5.5")
    add(jarJarConfig, "io.github.llamalad7:mixinextras-forge:0.5.5")
}

renamer.enableMixinRefmaps {
    config("${project.property("mod.id")}.mixins.json")
}
renamer.mappings(forgeDependency.toSrg)

val srgJar = renamer.classes(tasks.named<Jar>("jarJar")) {
    mappings(renamer.mixin.generatedMappings)
}

ModBuild.configureJava(project, requiredJava)

tasks {
    processResources {
        // Forge's own major version differs per Minecraft version (36 on 1.16.5, 47 on 1.20.1)
        val loaderRange = "[${sc.properties.get<String>("deps.forge_loader").substringBefore('.')},)"
        val props = ModBuild.resourceProps(this, modMetadata + ("loader_version" to loaderRange))

        filesMatching(
            listOf(
                "META-INF/mods.toml",
                "pack.mcmeta"
            )
        ) {
            expand(props)
        }

        val mixinJava = ModBuild.mixinCompatibility(requiredJava)

        filesMatching("*.mixins.json") {
            expand("java" to mixinJava)
        }

        exclude(
            "fabric.mod.json",
            "META-INF/neoforge.mods.toml",
            "*.ct",
            "*.classtweaker"
        )
    }

    named<Jar>("jar") {
        manifest {
            attributes(
                "MixinConfigs" to "${project.property("mod.id")}.mixins.json"
            )
        }
    }

    matching { it.name == "createMinecraftArtifacts" }.configureEach {
        dependsOn("stonecutterGenerate")
    }

}

ModBuild.registerBuildAndCollect(
    project,
    sc.properties["mod.version"],
    listOf(
        srgJar.flatMap { it.output },
        tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile },
    )
)

modrinth {
    token.set(ModBuild.secret(project, "MODRINTH_TOKEN"))
    changelog.set(ModBuild.changelog(project))
    projectId.set("oushii")
    versionNumber.set(project.version.toString())
    versionType.set("release")
    uploadFile.set(srgJar.flatMap { it.output })
    additionalFiles = listOf(tasks.named<Jar>("sourcesJar"))
    gameVersions.addAll(sc.properties.raw("mod", "mc_releases").to<List<String>>())
    loaders.add("forge")
    dependencies {
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
