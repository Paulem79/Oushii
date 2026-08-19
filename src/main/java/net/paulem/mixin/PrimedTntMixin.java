/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/
package net.paulem.mixin;

//? if >1.21.2
import org.spongepowered.asm.mixin.Shadow;

import net.paulem.ExplosionClusterManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PrimedTnt.class)
public abstract class PrimedTntMixin extends Entity {

	//? if >1.21.2 {
	@Shadow private float explosionPower;
	//?}

	public PrimedTntMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void onTick(CallbackInfo ci) {
		Level level = this.level
				//? if >1.19.4
				()
				;
		if (level.isClientSide()) return;

		PrimedTnt self = (PrimedTnt) (Object) this;

		//? if >1.21.2 {
		float power = this.explosionPower;
		//?} else {
		/*float power = 4.0f;
		*///?}

		// Skip physics for resting TNT to avoid useless ground collision ticks
		boolean isOnGround = self
				//$ if >1.19.4 '.onGround();' else '.isOnGround();'
				.onGround();
		if (isOnGround && self.getDeltaMovement().lengthSqr() < 1.0E-4) {
			int newFuse = self.getFuse() - 1;
			self.setFuse(newFuse);

			if (newFuse <= 1) {
				self.discard();
				if (level instanceof ServerLevel serverLevel) {
					ExplosionClusterManager.enqueue(serverLevel, self.position(), power);
				}
			}
			ci.cancel();
			return;
		}

		// Intercept detonation and hand off to cluster queue instead of running Level#explode
		if (self.getFuse() <= 1) {
			self.discard();
			if (level instanceof ServerLevel serverLevel) {
				ExplosionClusterManager.enqueue(serverLevel, self.position(), power);
			}
			ci.cancel();
		}
	}
}