plugins {
    id("net.neoforged.moddev.legacyforge") version "2.0.144"
    id("neoforge-mutex")
    id("com.modrinth.minotaur") version "2.+"
    id("com.diffplug.spotless") version "8.0.0"
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
    mavenCentral()

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

dependencies {
    if (hasMidnightLib) {
        val path = if (!midnightlibVersion.contains("+")) "maven.modrinth:midnightlib:$midnightlibVersion"
        else "eu.midnightdust:midnightlib:$midnightlibVersion"

        implementation(path)
        jarJar(path)
    }

    // Mixin
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

    // MixinExtras
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.4")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.4")
    implementation("io.github.llamalad7:mixinextras-forge:0.5.4")
    jarJar("io.github.llamalad7:mixinextras-forge:0.5.4")
}

mixin {
    add(sourceSets.main.get(), "${property("mod.id")}.refmap.json")
    config("${property("mod.id")}.mixins.json")
}

legacyForge {
    version = "${sc.current.version}-${sc.properties.get<String>("deps.forge_loader")}"

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

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))

        from(
            jar.flatMap { it.archiveFile },
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
    changelog.set((project.findProperty("modrinth.changelog") as String?) ?: "No changelog provided.")
    projectId.set("oushii")
    versionNumber.set(project.version.toString())
    versionType.set("release")
    uploadFile.set(tasks.jar)
    additionalFiles = listOf(tasks.named<Jar>("sourcesJar"))
    gameVersions.addAll(sc.properties.raw("mod", "mc_releases").to<List<String>>())
    loaders.add("forge")
    dependencies {
        if(hasMidnightLib) {
            embedded.version("midnightlib", midnightlibVersion)
        }
    }

    syncBodyFrom = rootProject.file("README.md").readText()

    debugMode = (project.findProperty("modrinth.debugMode") as String?).toBoolean()
}

tasks.modrinth.get().dependsOn(tasks.modrinthSyncBody)