package net.paulem.mixin;

import net.paulem.FastExplosionEngine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PrimedTnt.class)
public abstract class PrimedTntMixin extends Entity {
	@Shadow private float explosionPower;

	public PrimedTntMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void onTick(CallbackInfo ci) {
		if (this.level().isClientSide()) return;

		if (((PrimedTnt) (Object) this).getFuse() <= 1) {
			this.discard();
			if (this.level() instanceof ServerLevel serverLevel) {
				FastExplosionEngine.explode(serverLevel, this.position(), explosionPower);
			}
			ci.cancel();
		}
	}
}