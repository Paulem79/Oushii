/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/
package net.paulem;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.paulem.config.OushiiConfig;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Collects the blasts fired during a tick and detonates them once the tick is over, merging the ones
 * that land close enough to be indistinguishable anyway.
 *
 * <p>Everything is kept in primitive arrays: a cube of TNT going off produces tens of thousands of
 * blasts in a single tick, and neither the queue nor the clustering pass may allocate per blast.
 */
public final class ExplosionClusterManager {

    private ExplosionClusterManager() {}

    // WeakHashMap prevents memory leaks when dimensions unload
    private static final Map<ServerLevel, PendingQueue> QUEUES = new WeakHashMap<>();

    /** Cell to cluster index, reused between ticks. Server thread only. */
    private static final Long2IntOpenHashMap CELLS = new Long2IntOpenHashMap();

    static {
        CELLS.defaultReturnValue(-1);
    }

    private static double[] clusterX = new double[256];
    private static double[] clusterY = new double[256];
    private static double[] clusterZ = new double[256];
    /** Powers add up as volumes, so they are summed cubed and taken back down at the end. */
    private static double[] clusterEnergy = new double[256];
    private static int[] clusterCount = new int[256];

    private static final class PendingQueue {
        private double[] x = new double[64];
        private double[] y = new double[64];
        private double[] z = new double[64];
        private float[] power = new float[64];
        private int size;

        private void add(double px, double py, double pz, float ppower) {
            if (size == x.length) {
                final int capacity = size * 2;
                x = Arrays.copyOf(x, capacity);
                y = Arrays.copyOf(y, capacity);
                z = Arrays.copyOf(z, capacity);
                power = Arrays.copyOf(power, capacity);
            }
            x[size] = px;
            y[size] = py;
            z[size] = pz;
            power[size] = ppower;
            size++;
        }
    }

    public static void enqueue(ServerLevel level, double x, double y, double z, float power) {
        QUEUES.computeIfAbsent(level, key -> new PendingQueue()).add(x, y, z, power);
    }

    public static void processTick(ServerLevel level) {
        final PendingQueue queue = QUEUES.get(level);
        if (queue == null || queue.size == 0) return;

        final int pending = queue.size;
        // Cleared up front so a blast that queues another one lands in the next tick instead of
        // being detonated halfway through this pass
        queue.size = 0;

        final double radius = OushiiConfig.clusterRadius;
        if (radius <= 0.0) {
            for (int i = 0; i < pending; i++) {
                FastExplosionEngine.explode(level, new Vec3(queue.x[i], queue.y[i], queue.z[i]), queue.power[i]);
            }
            return;
        }

        final int clusters = cluster(queue, pending, radius);
        for (int i = 0; i < clusters; i++) {
            final int count = clusterCount[i];
            final Vec3 center = new Vec3(clusterX[i] / count, clusterY[i] / count, clusterZ[i] / count);
            FastExplosionEngine.explode(level, center, (float) Math.cbrt(clusterEnergy[i]));
        }
    }

    /**
     * Buckets the blasts on a grid whose cell is the merge radius. Comparing every blast with every
     * other one is what a cube of TNT cannot afford: this stays linear in the number of blasts.
     *
     * @return how many clusters were written to the accumulator arrays
     */
    private static int cluster(PendingQueue queue, int pending, double radius) {
        CELLS.clear();
        int clusters = 0;

        for (int i = 0; i < pending; i++) {
            final double x = queue.x[i];
            final double y = queue.y[i];
            final double z = queue.z[i];
            final float power = queue.power[i];

            final long key = cellKey(
                    (long) Math.floor(x / radius),
                    (long) Math.floor(y / radius),
                    (long) Math.floor(z / radius)
            );

            int index = CELLS.get(key);
            if (index < 0) {
                index = clusters++;
                if (index == clusterX.length) growClusters(index * 2);
                CELLS.put(key, index);
                clusterX[index] = 0.0;
                clusterY[index] = 0.0;
                clusterZ[index] = 0.0;
                clusterEnergy[index] = 0.0;
                clusterCount[index] = 0;
            }

            clusterX[index] += x;
            clusterY[index] += y;
            clusterZ[index] += z;
            clusterEnergy[index] += (double) power * power * power;
            clusterCount[index]++;
        }

        return clusters;
    }

    /** Mixed rather than bit packed, so a tiny merge radius cannot alias far apart cells together. */
    private static long cellKey(long cellX, long cellY, long cellZ) {
        long key = cellX * 0x9E3779B97F4A7C15L;
        key ^= cellY * 0xC2B2AE3D27D4EB4FL;
        key ^= cellZ * 0x165667B19E3779F9L;
        return key;
    }

    private static void growClusters(int capacity) {
        clusterX = Arrays.copyOf(clusterX, capacity);
        clusterY = Arrays.copyOf(clusterY, capacity);
        clusterZ = Arrays.copyOf(clusterZ, capacity);
        clusterEnergy = Arrays.copyOf(clusterEnergy, capacity);
        clusterCount = Arrays.copyOf(clusterCount, capacity);
    }
}
