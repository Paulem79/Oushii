package net.paulem.mixin.client;

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
    private static final int MAX_EXPLOSION_PARTICLES_PER_TICK = 100;
    @Unique
    private static final int MAX_RENDERED_TNT = 75;

    @Unique
    private int explosionParticleCount = 0;
    @Unique
    private long lastParticleResetTick = -1L;

    // Hard-cap particle spawns per tick to protect the client render thread from stalling
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

            if (this.explosionParticleCount >= MAX_EXPLOSION_PARTICLES_PER_TICK) {
                ci.cancel();
            } else {
                this.explosionParticleCount++;
            }
        }
    }

    // Distance-cull TNT entity rendering so distant blasts don't eat draw calls
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

        if (tntList.size() <= MAX_RENDERED_TNT) {
            return;
        }

        // Sort by distance squared to avoid Math.sqrt overhead on the client tick
        tntList.sort(Comparator.comparingDouble(tnt -> tnt.distanceToSqr(player)));

        for (int i = 0; i < MAX_RENDERED_TNT; i++) {
            filteredList.add(tntList.get(i));
        }

        cir.setReturnValue(filteredList);
    }
}