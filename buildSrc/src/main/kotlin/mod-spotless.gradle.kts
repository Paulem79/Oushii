// Applies the license header check to the shared sources. Every loader node compiles the
// same `src/main/java`, so the target and the header file are identical everywhere and
// the Spotless version is pinned once, in buildSrc.
//
// Wiring `build` to `spotlessCheck` is left to the loader scripts: on the NeoForge nodes
// it races with Stonecutter rewriting `src/main/java` under a parallel build, so those
// nodes only expose the task without running it as part of `build`.
plugins {
    id("com.diffplug.spotless")
}

spotless {
    java {
        target(rootProject.file("src/main/java/**/*.java"))
        licenseHeaderFile(rootProject.file("HEADER"))
    }
}
