plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2.x-fabric"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    properties {
        tags(version, loader)
    }

    constants {
        match(loader, "fabric", "neoforge", "forge")
    }

    swaps["mod_version"] = "\"${properties.get<String>("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    constants["release"] = properties.get<String>("mod.id") != "template"
    dependencies["fapi"] = properties.getOrNull<String>("deps.fabric_api") ?: "0"

    dependencies["midnightlib"] = properties.getOrNull<String>("deps.midnightlib") ?: "0"
    constants["hasMidnightLib"] = dependencies["midnightlib"] != "0"

    replacements {
        string(current.parsed > "1.19.4") {
            replace("LootContext;", "LootParams;")
        }

        string(current.parsed > "1.19.4") {
            replace("LootContext.", "LootParams.")
        }

        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }

        string(current.parsed >= "26.1") {
            replace("classTweaker v2 named", "classTweaker v2 official")
        }
    }
}