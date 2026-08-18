package net.paulem;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

//? if >1.21.2
//import net.minecraft.world.level.ServerExplosion;

public class FastExplosionEngine {
    private FastExplosionEngine() {
        /* Utility class */
    }

    private static final int MAX_PRIMED_PER_EXPLOSION = 32;

    /**
     * Pseudo-bruit 3D déterministe et ultra-rapide (0 allocation, calculs bitwise).
     * Renvoie une variation comprise entre -0.75 et +0.75 bloc.
     */
    private static double fastNoise(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h & 0xFFFF) / 65535.0 - 0.5) * 1.5;
    }

    public static void explode(ServerLevel level, Vec3 pos, float power) {
        level.playSound(
                null,
                pos.x, pos.y, pos.z,
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                net.minecraft.sounds.SoundSource.BLOCKS,
                4.0f,
                (1.0f + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2f) * 0.7f
        );

        level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z,
                1, 0.0, 0.0, 0.0, 0.0
        );

        // Ajustement des bornes pour inclure la déformation maximale du bruit (+0.75 bloc)
        float maxRadius = power + 0.75f;
        int radiusSq = Math.round(power * power);

        int minX = (int) Math.floor(pos.x - maxRadius);
        int maxX = (int) Math.ceil(pos.x + maxRadius);

        //? if >1.21.2 {
        /*int levelMinY = level.getMinY();
        int levelMaxY = level.getMaxY();*/
        //?} else {
        int levelMinY = level.getMinBuildHeight();
        int levelMaxY = level.getMaxBuildHeight();
        //?}

        int minY = Math.max(levelMinY, (int) Math.floor(pos.y - maxRadius));
        int maxY = Math.min(levelMaxY, (int) Math.ceil(pos.y + maxRadius));
        int minZ = (int) Math.floor(pos.z - maxRadius);
        int maxZ = (int) Math.ceil(pos.z + maxRadius);

        //? if >1.21.2 {
        /*ServerExplosion explosionContext = new ServerExplosion(
                level, null, null, null, pos, power, false, Explosion.BlockInteraction.DESTROY
        );*/
        //?} else {
        Explosion explosionContext = new Explosion(
                level, null, pos.x, pos.y, pos.z, power, false, Explosion.BlockInteraction.DESTROY
        );
        //?}

        // 1. Gestion des entités (Poussée & Dégâts)
        AABB explosionBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        List<Entity> entities = level.getEntities((Entity) null, explosionBox, Entity::isAlive);
        DamageSource damageSource = level.damageSources().explosion(null, null);

        for (Entity entity : entities) {
            if (entity.ignoreExplosion(explosionContext)) continue;

            double distSq = entity.distanceToSqr(pos);
            if (distSq <= radiusSq) {
                double dist = Math.sqrt(distSq);
                double impact = 1.0 - (dist / power);

                Vec3 entityCenter = entity.getBoundingBox().getCenter();
                Vec3 delta = entityCenter.subtract(pos);
                double len = delta.length();

                if (len > 0) {
                    Vec3 dir = delta.normalize();
                    double speed = Math.min(1.0, impact * 1.2);
                    entity.push(dir.x * speed, (dir.y * speed) + (0.25 * impact), dir.z * speed);
                }

                if (entity instanceof PrimedTnt tnt) {
                    int currentFuse = tnt.getFuse();
                    int newFuse = level.getRandom().nextInt(Math.max(1, currentFuse / 4)) + currentFuse / 8;
                    if (currentFuse > newFuse) {
                        tnt.setFuse(newFuse);
                    }
                } else {
                    float damage = (float) ((impact * impact + impact) / 2.0 * 7.0 * power + 1.0);
                    entity.hurt(damageSource, damage);
                }
            }
        }

        // 2. Destruction des blocs & Amorçage des TNT
        Object2IntOpenHashMap<Item> itemDrops = new Object2IntOpenHashMap<>();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

        LootParams.Builder lootBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);

        int spawnedTntCount = 0;

        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {

                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                boolean chunkModified = false;

                int cMinX = Math.max(minX, chunkX << 4);
                int cMaxX = Math.min(maxX, (chunkX << 4) + 15);
                int cMinZ = Math.max(minZ, chunkZ << 4);
                int cMaxZ = Math.min(maxZ, (chunkZ << 4) + 15);

                for (int y = minY; y <= maxY; y++) {
                    int sectionIndex = chunk.getSectionIndex(y);
                    LevelChunkSection section = chunk.getSection(sectionIndex);

                    if (section == null || section.hasOnlyAir()) continue;

                    int localY = y & 15;

                    for (int x = cMinX; x <= cMaxX; x++) {
                        double dx = x + 0.5 - pos.x;
                        int localX = x & 15;

                        for (int z = cMinZ; z <= cMaxZ; z++) {
                            double dz = z + 0.5 - pos.z;
                            double dy = y + 0.5 - pos.y;
                            int localZ = z & 15;

                            double blockDistSq = dx * dx + dy * dy + dz * dz;

                            // Application du bruit 3D par bloc
                            double effectiveRadius = power + fastNoise(x, y, z);
                            if (effectiveRadius <= 0) continue;
                            double effectiveRadiusSq = effectiveRadius * effectiveRadius;

                            if (blockDistSq <= effectiveRadiusSq) {
                                BlockState currentState = section.getBlockState(localX, localY, localZ);

                                if (!currentState.isAir()) {
                                    mutablePos.set(x, y, z);

                                    if (currentState.is(Blocks.TNT)) {
                                        section.setBlockState(localX, localY, localZ, Blocks.AIR.defaultBlockState());
                                        chunkModified = true;

                                        level.getChunkSource().blockChanged(mutablePos);
                                        level.getLightEngine().checkBlock(mutablePos);

                                        PrimedTnt primedTnt = new PrimedTnt(level, x + 0.5, y, z + 0.5, null);

                                        if (spawnedTntCount < MAX_PRIMED_PER_EXPLOSION) {
                                            spawnedTntCount++;
                                            primedTnt.setFuse(level.getRandom().nextInt(15) + 10);
                                        } else {
                                            primedTnt.setFuse(level.getRandom().nextInt(3) + 1);
                                        }

                                        Vec3 blockCenter = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                                        Vec3 pushVec = blockCenter.subtract(pos);
                                        double dist = pushVec.length();

                                        if (dist > 0) {
                                            double impact = 1.0 - (dist / power);
                                            Vec3 dir = pushVec.normalize();
                                            double speed = Math.min(0.8, impact * 0.9);
                                            primedTnt.setDeltaMovement(
                                                    dir.x * speed,
                                                    (dir.y * speed) + 0.25,
                                                    dir.z * speed
                                            );
                                        } else {
                                            primedTnt.setDeltaMovement(0, 0.3, 0);
                                        }

                                        level.addFreshEntity(primedTnt);

                                    } else if (currentState.getBlock().getExplosionResistance() < 100.0f) {
                                        section.setBlockState(localX, localY, localZ, Blocks.AIR.defaultBlockState());
                                        chunkModified = true;

                                        level.getChunkSource().blockChanged(mutablePos);
                                        level.getLightEngine().checkBlock(mutablePos);

                                        // Mise à jour ciblée si le voisin est hors du rayon effectif
                                        for (Direction dir : Direction.values()) {
                                            int nx = x + dir.getStepX();
                                            int ny = y + dir.getStepY();
                                            int nz = z + dir.getStepZ();

                                            double ndx = nx + 0.5 - pos.x;
                                            double ndy = ny + 0.5 - pos.y;
                                            double ndz = nz + 0.5 - pos.z;

                                            if (ndx * ndx + ndy * ndy + ndz * ndz > effectiveRadiusSq) {
                                                neighborPos.set(nx, ny, nz);
                                                BlockState neighborState = level.getBlockState(neighborPos);

                                                if (!neighborState.getFluidState().isEmpty() || neighborState.getBlock() instanceof FallingBlock) {
                                                    level.neighborChanged(neighborPos, currentState.getBlock(), null);
                                                }
                                            }
                                        }

                                        lootBuilder.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(mutablePos));
                                        List<ItemStack> drops = currentState.getDrops(lootBuilder);
                                        for (ItemStack stack : drops) {
                                            itemDrops.addTo(stack.getItem(), stack.getCount());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (chunkModified) {
                    //? if >1.21.2 {
                    //chunk.markUnsaved();
                    //?} else {
                    chunk.setUnsaved(true);
                    //?}
                }
            }
        }

        // 3. Regroupement des items
        itemDrops.forEach((item, count) -> {
            int remaining = count;
            while (remaining > 0) {
                int stackSize = Math.min(remaining, item.getDefaultMaxStackSize());
                remaining -= stackSize;
                ItemEntity itemEntity = new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(item, stackSize));
                level.addFreshEntity(itemEntity);
            }
        });
    }
}