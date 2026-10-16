plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Lets the `mod-spotless` convention plugin configure Spotless with its real types,
    // so the version is declared once instead of in all three loader scripts
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.4")
}
