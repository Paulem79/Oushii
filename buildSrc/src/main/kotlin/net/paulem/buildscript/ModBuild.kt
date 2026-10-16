package net.paulem.buildscript

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.register

/**
 * Logic shared by `build.fabric.gradle.kts`, `build.forge.gradle.kts` and
 * `build.neoforge.gradle.kts`.
 *
 * Only plain Gradle types are used here, so buildSrc does not need the Loom,
 * Stonecutter or Minotaur plugins on its classpath. Anything that depends on those
 * plugins stays in the loader scripts and gets its values passed in.
 */
object ModBuild {

    /**
     * Compares two Minecraft ids component-wise, ignoring any qualifier
     * (`26.3-snapshot-9` is treated as `26.3`).
     *
     * The calendar scheme sorts correctly against the old one for free,
     * because `26` is numerically greater than `1`.
     */
    private fun compareVersions(left: String, right: String): Int {
        fun parts(version: String) = version.substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }

        val a = parts(left)
        val b = parts(right)
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private fun atLeast(version: String, target: String) = compareVersions(version, target) >= 0

    /** The Java release a given Minecraft version is compiled and run against. */
    fun requiredJava(minecraftVersion: String): JavaVersion = when {
        atLeast(minecraftVersion, "26.1") -> JavaVersion.VERSION_25
        atLeast(minecraftVersion, "1.20.5") -> JavaVersion.VERSION_21
        atLeast(minecraftVersion, "1.18") -> JavaVersion.VERSION_17
        atLeast(minecraftVersion, "1.17") -> JavaVersion.VERSION_16
        else -> JavaVersion.VERSION_1_8
    }

    /** Mixin's `compatibilityLevel`, which follows the same split. */
    fun mixinCompatibility(java: JavaVersion): String = "JAVA_${java.majorVersion}"

    fun configureJava(project: Project, java: JavaVersion) {
        with(project.extensions.getByType<JavaPluginExtension>()) {
            withSourcesJar()
            targetCompatibility = java
            sourceCompatibility = java

            toolchain {
                vendor.set(JvmVendorSpec.ADOPTIUM)
                languageVersion.set(JavaLanguageVersion.of(java.majorVersion))
            }
        }
    }

    /**
     * Adds Maven Central plus the mod repositories, each restricted to the groups it
     * actually serves so dependency resolution does not probe every repository in turn.
     */
    fun repositories(repositories: RepositoryHandler) {
        repositories.mavenCentral()

        fun strictMaven(url: String, alias: String, vararg groups: String) =
            repositories.exclusiveContent {
                forRepository { repositories.maven(url) { name = alias } }
                filter { groups.forEach(::includeGroup) }
            }

        strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
        strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
        strictMaven("https://maven.midnightdust.eu/releases", "MidnightLib", "eu.midnightdust")
        strictMaven("https://maven.terraformersmc.com/", "Terraformers", "com.terraformersmc")
    }

    /**
     * MidnightLib's own Maven only carries the builds whose version contains a `+`
     * (`1.10.0+26.3-fabric`); the older `1.3.0-fabric` style is only on Modrinth.
     */
    fun midnightLibNotation(version: String): String =
        if (version.contains("+")) "eu.midnightdust:midnightlib:$version"
        else "maven.modrinth:midnightlib:$version"

    /**
     * Registers [values] as task inputs and hands them back, ready for
     * `filesMatching { expand(...) }`.
     */
    fun resourceProps(task: Task, values: Map<String, String>): Map<String, String> {
        values.forEach { (key, value) -> task.inputs.property(key, value) }
        return values
    }

    /**
     * `buildAndCollect`, which gathers the built jars of every version node under
     * `build/libs/{mod version}/`.
     */
    fun registerBuildAndCollect(
        project: Project,
        modVersion: String,
        artifacts: List<Provider<out RegularFile>>,
    ) {
        project.tasks.register<Copy>("buildAndCollect") {
            group = "build"
            description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

            inputs.property("version", modVersion)
            artifacts.forEach { from(it) }
            into(project.rootProject.layout.buildDirectory.file("libs/$modVersion"))
        }
    }

    /** Reads a token from a Gradle property, falling back to the environment. */
    fun secret(project: Project, name: String): String? =
        project.findProperty(name) as String? ?: System.getenv(name)

    fun changelog(project: Project): String =
        project.findProperty("minotaur.changelog") as String? ?: "No changelog provided."

    fun debugMode(project: Project): Boolean =
        (project.findProperty("minotaur.debugMode") as String?).toBoolean()
}
