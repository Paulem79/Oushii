/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/
package net.paulem.config;

//? if hasMidnightLib {
import eu.midnightdust.lib.config.MidnightConfig;

public class OushiiConfig extends MidnightConfig {

    //? if !legacyMidnightLib {
    @Comment(category = "client")
            //?} else {
    /*@Comment
     *///?}
    public static Comment clientSection;

    // Hard-cap on explosion particles spawned per tick
    //? if !legacyMidnightLib {
    @Entry(category = "client", min = 0, max = 1000)
            //?} else {
    /*@Entry(min = 0, max = 1000)
     *///?}
    public static int maxExplosionParticlesPerTick = 100;

    // Max TNT entities rendered on screen (sorted by distance)
    //? if !legacyMidnightLib {
    @Entry(category = "client", min = 0, max = 1000)
            //?} else {
    /*@Entry(min = 0, max = 1000)
     *///?}
    public static int maxRenderedTnt = 75;

    //? if !legacyMidnightLib {
    @Comment(category = "server")
            //?} else {
    /*@Comment
     *///?}
    public static Comment serverSection;

    // Limit primed TNT spawned per explosion to prevent entity cascades
    //? if !legacyMidnightLib {
    @Entry(category = "server", min = 1, max = 256)
            //?} else {
    /*@Entry(min = 1, max = 256)
     *///?}
    public static int maxPrimedPerExplosion = 32;

    // Radius in blocks within which same-tick explosions merge into one
    //? if !legacyMidnightLib {
    @Entry(category = "server", min = 0.0, max = 20.0)
            //?} else {
    /*@Entry(min = 0.0, max = 20.0)
     *///?}
    public static double clusterRadius = 3.0;
}
//?} else {
/*import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.paulem.OushiiCommon;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Standalone config for the versions MidnightLib has no build for (1.16.5, and 26.3 snapshots
// until one ships). It reads and writes the very file MidnightLib would - config/<modid>.json,
// one flat object - so moving between the two keeps the settings.
public class OushiiConfig {

    // Hard-cap on explosion particles spawned per tick
    public static int maxExplosionParticlesPerTick = 100;
    // Max TNT entities rendered on screen (sorted by distance)
    public static int maxRenderedTnt = 75;
    // Limit primed TNT spawned per explosion to prevent entity cascades
    public static int maxPrimedPerExplosion = 32;
    // Radius in blocks within which same-tick explosions merge into one
    public static double clusterRadius = 3.0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Signature matches MidnightConfig#init so the entrypoints read the same on every version
    public static void init(String modId, Class<?> configClass) {
        Path path = Paths.get("config", modId + ".json");
        read(path);
        write(path);
    }

    private static void read(Path path) {
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;
            maxExplosionParticlesPerTick = readInt(json, "maxExplosionParticlesPerTick", maxExplosionParticlesPerTick, 0, 1000);
            maxRenderedTnt = readInt(json, "maxRenderedTnt", maxRenderedTnt, 0, 1000);
            maxPrimedPerExplosion = readInt(json, "maxPrimedPerExplosion", maxPrimedPerExplosion, 1, 256);
            clusterRadius = readDouble(json, "clusterRadius", clusterRadius, 0.0, 20.0);
        } catch (Exception e) {
            // A config we cannot parse must not take the game down: the defaults are rewritten over it
            OushiiCommon.LOGGER.warn("Could not read {}, falling back to the defaults", path, e);
        }
    }

    // Values out of range are clamped rather than rejected, matching what MidnightLib's bounds do
    private static int readInt(JsonObject json, String key, int fallback, int min, int max) {
        if (!json.has(key)) return fallback;
        try {
            return Math.max(min, Math.min(max, json.get(key).getAsInt()));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double readDouble(JsonObject json, String key, double fallback, double min, double max) {
        if (!json.has(key)) return fallback;
        try {
            return Math.max(min, Math.min(max, json.get(key).getAsDouble()));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static void write(Path path) {
        JsonObject json = new JsonObject();
        json.addProperty("maxExplosionParticlesPerTick", maxExplosionParticlesPerTick);
        json.addProperty("maxRenderedTnt", maxRenderedTnt);
        json.addProperty("maxPrimedPerExplosion", maxPrimedPerExplosion);
        json.addProperty("clusterRadius", clusterRadius);

        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            OushiiCommon.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
*///?}