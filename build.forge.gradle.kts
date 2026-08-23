plugins {
    id("net.minecraftforge.gradle") version "7.+"
    id("net.minecraftforge.jarjar") version "0.2.3"
    id("net.minecraftforge.renamer") version "1.1.7"
    id("neoforge-mutex")
    id("com.modrinth.minotaur") version "2.+"
    id("com.diffplug.spotless") version "8.10.0"
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-forge"

val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

spotless {
    java {
        target(rootProject.file("src/main/java/**/*.java"))
        licenseHeaderFile(rootProject.file("HEADER"))
    }
}

repositories {
    maven { minecraft.mavenizer.execute(this) }
    maven { fg.forgeMaven.execute(this) }
    maven { fg.minecraftLibsMaven.execute(this) }

    mavenCentral()
    maven("https://repo.spongepowered.org/maven")

    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository {
            maven(url) {
                name = alias
            }
        }
        filter {
            groups.forEach(::includeGroup)
        }
    }

    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    strictMaven("https://maven.midnightdust.eu/releases", "MidnightLib", "eu.midnightdust")
    strictMaven("https://maven.terraformersmc.com/", "Terraformers", "com.terraformersmc")
}

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
        val baseNotation = if (!midnightlibVersion.contains("+")) "maven.modrinth:midnightlib"
        else "eu.midnightdust:midnightlib"

        implementation("$baseNotation:$midnightlibVersion")
        val midnightLibJarJarDep = add(jarJarConfig, "$baseNotation:$midnightlibVersion")

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
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.4")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.4")
    implementation("io.github.llamalad7:mixinextras-forge:0.5.4")
    add(jarJarConfig, "io.github.llamalad7:mixinextras-forge:0.5.4")
}

renamer.enableMixinRefmaps {
    config("${project.property("mod.id")}.mixins.json")
}
renamer.mappings(forgeDependency.toSrg)

val srgJar = renamer.classes(tasks.named<Jar>("jarJar")) {
    mappings(renamer.mixin.generatedMappings)
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, property: String) {
            val value: String = sc.properties[property]
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", "mod.id")
            register("name", "mod.name")
            register("version", "mod.version")
            register("description", "mod.description")
            register("minecraft", "mod.mc_compat")
        }

        filesMatching(
            listOf(
                "META-INF/mods.toml",
                "pack.mcmeta"
            )
        ) {
            expand(props)
        }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"

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

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))

        from(
            srgJar.flatMap { it.output },
            named<Jar>("sourcesJar").flatMap { it.archiveFile }
        )

        into(
            rootProject.layout.buildDirectory.file(
                "libs/${project.property("mod.version")}"
            )
        )
    }
}

tasks.build {
    dependsOn("spotlessCheck")
}

modrinth {
    token.set((project.findProperty("MODRINTH_TOKEN") as String?) ?: System.getenv("MODRINTH_TOKEN"))
    changelog.set((project.findProperty("minotaur.changelog") as String?) ?: "No changelog provided.")
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

    debugMode = (project.findProperty("minotaur.debugMode") as String?).toBoolean()
}

tasks.modrinth.get().dependsOn(tasks.modrinthSyncBody)