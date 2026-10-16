// License header check for the shared sources.
//
// This is applied to the ROOT project on purpose. `src/main/java` lives at the root, every
// loader node compiles that same tree, and Spotless refuses targets outside the project it
// runs in ("All target files must be within the project dir") - so a version node cannot
// check them. Running it once at the root is also 24x less work than once per node.
plugins {
    id("com.diffplug.spotless")
}

spotless {
    java {
        // A fileTree, not `file("src/main/java/**/*.java")`: the latter pushes the glob
        // through File, resolving to a path containing `*` that only blows up once the
        // task actually executes ("Illegal char <*>").
        target(fileTree("src/main/java") { include("**/*.java") })
        licenseHeaderFile(file("HEADER"))
    }
}
