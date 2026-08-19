/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/
package net.paulem;

import net.paulem.config.OushiiConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ExplosionClusterManager {

    private ExplosionClusterManager() {}

    // WeakHashMap prevents memory leaks when dimensions unload
    private static final Map<ServerLevel, List<PendingExplosion>> QUEUES = new WeakHashMap<>();

    public record PendingExplosion(Vec3 pos, float power) {}

    public static void enqueue(ServerLevel level, Vec3 pos, float power) {
        QUEUES.computeIfAbsent(level, k -> new ArrayList<>()).add(new PendingExplosion(pos, power));
    }

    public static void processTick(ServerLevel level) {
        List<PendingExplosion> queue = QUEUES.get(level);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        // Snapshot and clear to prevent ConcurrentModificationException if blasts trigger more blasts
        List<PendingExplosion> pending = new ArrayList<>(queue);
        queue.clear();

        List<PendingExplosion> clustered = clusterExplosions(pending);

        for (PendingExplosion exp : clustered) {
            FastExplosionEngine.explode(level, exp.pos(), exp.power());
        }
    }

    private static List<PendingExplosion> clusterExplosions(List<PendingExplosion> input) {
        List<PendingExplosion> result = new ArrayList<>();
        boolean[] used = new boolean[input.size()];
        double clusterRadiusSq = OushiiConfig.clusterRadius * OushiiConfig.clusterRadius;

        for (int i = 0; i < input.size(); i++) {
            if (used[i]) continue;

            PendingExplosion base = input.get(i);
            double sumX = base.pos().x;
            double sumY = base.pos().y;
            double sumZ = base.pos().z;
            int count = 1;
            used[i] = true;

            for (int j = i + 1; j < input.size(); j++) {
                if (used[j]) continue;

                PendingExplosion other = input.get(j);
                if (base.pos().distanceToSqr(other.pos()) <= clusterRadiusSq) {
                    sumX += other.pos().x;
                    sumY += other.pos().y;
                    sumZ += other.pos().z;
                    count++;
                    used[j] = true;
                }
            }

            Vec3 centroid = new Vec3(sumX / count, sumY / count, sumZ / count);
            // Cube root power scaling preserves realistic volumetric destruction
            float mergedPower = (float) (base.power() * Math.cbrt(count));
            result.add(new PendingExplosion(centroid, mergedPower));
        }

        return result;
    }
}