plugins {
    id("net.neoforged.moddev") version "2.0.140"
    id("neoforge-mutex")
    id("maven-publish")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-neoforge"

val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

repositories {
    /**
     * Restricts dependency search of the given [groups] to the [maven URL][url],
     * improving the setup speed.
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

dependencies {
    val midnightlibVersion = sc.properties.get<String>("deps.midnightlib")
    implementation("maven.modrinth:midnightlib:$midnightlibVersion")
    jarJar("maven.modrinth:midnightlib:$midnightlibVersion")
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

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

// Modrinth publishing configuration

publishing {
    repositories {
        maven {
            name = "Modrinth"
            url = uri("https://api.modrinth.com/maven")
            credentials {
                username = "token"
                password = findProperty("MODRINTH_TOKEN")?.toString() ?: System.getenv("MODRINTH_TOKEN") ?: ""
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = property("mod.group") as String
            artifactId = property("mod.id") as String
            version = property("mod.version") as String

            from(components["java"])

            pom {
                name.set(property("mod.name") as String)
                description.set("A Minecraft mod")
                url.set("https://github.com/Paulem79/Oushii")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("Paulem79")
                        name.set("Paul")
                    }
                }

                scm {
                    url.set("https://github.com/Paulem79/Oushii")
                    connection.set("scm:git:github.com/Paulem79/Oushii.git")
                    developerConnection.set("scm:git:ssh:git@github.com:Paulem79/Oushii.git")
                }
            }
        }
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

        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("fabric.mod.json", "*.ct", "*.classtweaker")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))
        from(jar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
