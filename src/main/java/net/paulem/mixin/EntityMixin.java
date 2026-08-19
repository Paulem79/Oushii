/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.paulem.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.paulem.utils.SCUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixin {

    // Strip TNT-vs-TNT pushing; N entities colliding creates an O(N^2) CPU nightmare (bruh)
    @WrapOperation(
            method = "collide",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<VoxelShape> skipEntityCollisionsForTnt(Level level, Entity entity, AABB box, Operation<List<VoxelShape>> original) {
        if ((Object) this instanceof PrimedTnt) {
            return Collections.emptyList();
        }
        return original.call(level, entity, box);
    }

    // Bypass VoxelShape physics entirely when flying through empty chunk sections
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void fastAirMove(MoverType moverType, Vec3 delta, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        Level level =
                //? if >1.19.4 {
                self.level();
        //?} else {
        /*self.level;
         *///?}

        if (level.isClientSide() || !(self instanceof PrimedTnt)) {
            return;
        }

        if (delta.lengthSqr() < 1.0E-7) {
            ci.cancel();
            return;
        }

        AABB sweptBox = self.getBoundingBox().expandTowards(delta);

        if (isAreaPureAir(level, sweptBox)) {
            self.setPos(self.getX() + delta.x, self.getY() + delta.y, self.getZ() + delta.z);
            self.setOnGround(false);
            self.horizontalCollision = false;
            self.verticalCollision = false;
            ci.cancel();
        }
    }

    private static boolean isAreaPureAir(Level level, AABB box) {
        int minLevelY = SCUtils.getLevelMinY(level);
        int maxLevelY = SCUtils.getLevelMaxY(level);

        // Entities yeeted above build height or into the void are guaranteed to be in pure air
        if (box.maxY < minLevelY || box.minY >= maxLevelY) {
            return true;
        }

        int minX = Mth.floor(box.minX) >> 4;
        int maxX = Mth.floor(box.maxX) >> 4;
        int minZ = Mth.floor(box.minZ) >> 4;
        int maxZ = Mth.floor(box.maxZ) >> 4;

        // Clamp Y to valid block bounds (0..319) so getSectionIndex doesn't yield out-of-bounds index 24
        int minY = Mth.clamp(Mth.floor(box.minY), minLevelY, maxLevelY - 1);
        int maxY = Mth.clamp(Mth.floor(box.maxY), minLevelY, maxLevelY - 1);

        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) return false;

                LevelChunkSection[] sections = chunk.getSections();
                int minSecIdx = Math.max(0, chunk.getSectionIndex(minY));
                int maxSecIdx = Math.min(sections.length - 1, chunk.getSectionIndex(maxY));

                for (int secIdx = minSecIdx; secIdx <= maxSecIdx; secIdx++) {
                    LevelChunkSection section = sections[secIdx];
                    if (section != null && !section.hasOnlyAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}