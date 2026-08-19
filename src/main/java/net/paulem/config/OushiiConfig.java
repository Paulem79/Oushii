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

    //? if >1.18.2 {
    @Comment(category = "client")
            //?} else {
    /*@Comment
     *///?}
    public static Comment clientSection;

    // Hard-cap on explosion particles spawned per tick
    //? if >1.18.2 {
    @Entry(category = "client", min = 0, max = 1000)
            //?} else {
    /*@Entry(min = 0, max = 1000)
     *///?}
    public static int maxExplosionParticlesPerTick = 100;

    // Max TNT entities rendered on screen (sorted by distance)
    //? if >1.18.2 {
    @Entry(category = "client", min = 0, max = 1000)
            //?} else {
    /*@Entry(min = 0, max = 1000)
     *///?}
    public static int maxRenderedTnt = 75;

    //? if >1.18.2 {
    @Comment(category = "server")
            //?} else {
    /*@Comment
     *///?}
    public static Comment serverSection;

    // Limit primed TNT spawned per explosion to prevent entity cascades
    //? if >1.18.2 {
    @Entry(category = "server", min = 1, max = 256)
            //?} else {
    /*@Entry(min = 1, max = 256)
     *///?}
    public static int maxPrimedPerExplosion = 32;

    // Radius in blocks within which same-tick explosions merge into one
    //? if >1.18.2 {
    @Entry(category = "server", min = 0.0, max = 20.0)
            //?} else {
    /*@Entry(min = 0.0, max = 20.0)
     *///?}
    public static double clusterRadius = 3.0;
}
//?} else {
/*import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = "oushii", bus = Mod.EventBusSubscriber.Bus.MOD)
public class OushiiConfig {

    public static int maxExplosionParticlesPerTick = 100;
    public static int maxRenderedTnt = 75;
    public static int maxPrimedPerExplosion = 32;
    public static double clusterRadius = 3.0;

    private static final ForgeConfigSpec CLIENT_SPEC;
    private static final ForgeConfigSpec.IntValue MAX_EXPLOSION_PARTICLES;
    private static final ForgeConfigSpec.IntValue MAX_RENDERED_TNT;

    private static final ForgeConfigSpec COMMON_SPEC;
    private static final ForgeConfigSpec.IntValue MAX_PRIMED_PER_EXPLOSION;
    private static final ForgeConfigSpec.DoubleValue CLUSTER_RADIUS;

    static {
        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        clientBuilder.push("client");
        MAX_EXPLOSION_PARTICLES = clientBuilder
                .comment("Hard-cap on explosion particles spawned per tick")
                .defineInRange("maxExplosionParticlesPerTick", 100, 0, 1000);
        MAX_RENDERED_TNT = clientBuilder
                .comment("Max TNT entities rendered on screen (sorted by distance)")
                .defineInRange("maxRenderedTnt", 75, 0, 1000);
        clientBuilder.pop();
        CLIENT_SPEC = clientBuilder.build();

        ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();
        commonBuilder.push("server");
        MAX_PRIMED_PER_EXPLOSION = commonBuilder
                .comment("Limit primed TNT spawned per explosion to prevent entity cascades")
                .defineInRange("maxPrimedPerExplosion", 32, 1, 256);
        CLUSTER_RADIUS = commonBuilder
                .comment("Radius in blocks within which same-tick explosions merge into one")
                .defineInRange("clusterRadius", 3.0, 0.0, 20.0);
        commonBuilder.pop();
        COMMON_SPEC = commonBuilder.build();
    }

    public static void init(String modId, Class<?> configClass) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    }

    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == CLIENT_SPEC) {
            maxExplosionParticlesPerTick = MAX_EXPLOSION_PARTICLES.get();
            maxRenderedTnt = MAX_RENDERED_TNT.get();
        } else if (event.getConfig().getSpec() == COMMON_SPEC) {
            maxPrimedPerExplosion = MAX_PRIMED_PER_EXPLOSION.get();
            clusterRadius = CLUSTER_RADIUS.get();
        }
    }
}
*///?}