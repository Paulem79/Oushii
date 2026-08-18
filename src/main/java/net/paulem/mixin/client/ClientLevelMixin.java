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

        Iterable<Entity> originalEntities = cir.getReturnValue();
        List<Entity> filteredList = new ArrayList<>();
        List<PrimedTnt> tntList = new ArrayList<>();

        for (Entity entity : originalEntities) {
            if (entity instanceof PrimedTnt tnt) {
                tntList.add(tnt);
            } else {
                filteredList.add(entity);
            }
        }

        if (tntList.size() <= OushiiConfig.maxRenderedTnt) {
            return;
        }

        // distanceToSqr avoids Math.sqrt overhead on hot render paths
        tntList.sort(Comparator.comparingDouble(tnt -> tnt.distanceToSqr(player)));

        for (int i = 0; i < OushiiConfig.maxRenderedTnt; i++) {
            filteredList.add(tntList.get(i));
        }

        cir.setReturnValue(filteredList);
    }
}