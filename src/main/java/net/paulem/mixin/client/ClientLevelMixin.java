/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/
package net.paulem.mixin.client;

import net.paulem.config.OushiiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Unique
    private int explosionParticleCount = 0;
    @Unique
    private long lastParticleResetTick = -1L;

    // Stop particle spam from choking client render thread on huge detonations
    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void capExplosionParticles(ParticleOptions particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfo ci) {
        if (particleData.getType() == ParticleTypes.EXPLOSION || particleData.getType() == ParticleTypes.EXPLOSION_EMITTER) {
            ClientLevel self = (ClientLevel) (Object) this;
            long currentTick = self.getGameTime();

            if (currentTick != this.lastParticleResetTick) {
                this.explosionParticleCount = 0;
                this.lastParticleResetTick = currentTick;
            }

            if (this.explosionParticleCount >= OushiiConfig.maxExplosionParticlesPerTick) {
                ci.cancel();
            } else {
                this.explosionParticleCount++;
            }
        }
    }

    // Cull distant TNT draw calls; rendering thousands of 3D entity models melts client GPUs
    @Inject(
            method = "entitiesForRendering",
            at = @At("RETURN"),
            cancellable = true
    )
    private void filterTntByProximity(CallbackInfoReturnable<Iterable<Entity>> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        final int limit = OushiiConfig.maxRenderedTnt;
        final Iterable<Entity> entities = cir.getReturnValue();

        // This runs every frame, so the common case (no TNT storm) must not allocate anything
        int tntCount = 0;
        for (Entity entity : entities) {
            if (entity instanceof PrimedTnt && ++tntCount > limit) break;
        }
        if (tntCount <= limit) return;

        final List<Entity> filteredList = new ArrayList<>();
        // Bounded max-heap: keeps the closest TNT without sorting the whole storm
        final PriorityQueue<Entity> nearest = new PriorityQueue<>(
                Math.max(1, limit),
                // distanceToSqr avoids Math.sqrt overhead on hot render paths
                Comparator.comparingDouble((Entity tnt) -> tnt.distanceToSqr(player)).reversed()
        );

        for (Entity entity : entities) {
            if (!(entity instanceof PrimedTnt)) {
                filteredList.add(entity);
                continue;
            }
            if (limit == 0) continue;

            nearest.add(entity);
            if (nearest.size() > limit) nearest.poll();
        }

        filteredList.addAll(nearest);
        cir.setReturnValue(filteredList);
    }
}
