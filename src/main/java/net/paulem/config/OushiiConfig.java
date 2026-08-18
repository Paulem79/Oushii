package net.paulem.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class OushiiConfig extends MidnightConfig {

    @Comment(category = "client")
    public static Comment clientSection;

    // Hard-cap on explosion particles spawned per tick
    @Entry(category = "client", min = 0, max = 1000)
    public static int maxExplosionParticlesPerTick = 100;

    // Max TNT entities rendered on screen (sorted by distance)
    @Entry(category = "client", min = 0, max = 1000)
    public static int maxRenderedTnt = 75;

    @Comment(category = "server")
    public static Comment serverSection;

    // Limit primed TNT spawned per explosion to prevent entity cascades
    @Entry(category = "server", min = 1, max = 256)
    public static int maxPrimedPerExplosion = 32;

    // Radius in blocks within which same-tick explosions merge into one
    @Entry(category = "server", min = 0.0, max = 20.0)
    public static double clusterRadius = 3.0;
}