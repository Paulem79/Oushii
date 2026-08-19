/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/
package net.paulem.mixin;

import net.paulem.ExplosionClusterManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelTickMixin {

    // Flush all queued TNT explosions at end-of-tick so multi-TNT detonations in same tick get merged
    @Inject(method = "tick", at = @At("TAIL"))
    private void processExplosionsAtTickEnd(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        ExplosionClusterManager.processTick((ServerLevel) (Object) this);
    }
}