package net.paulem;

import net.paulem.config.OushiiConfig;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

//? if >1.21.2 {
import net.minecraft.world.level.ServerExplosion;
//?} else {
/*import net.minecraft.world.level.Explosion;
*///?}

import java.util.List;

public final class FastExplosionEngine {

    private FastExplosionEngine() {}

    private static int soundCountThisTick = 0;
    private static long lastSoundResetTick = -1L;
    private static final int MAX_EXPLOSION_SOUNDS_PER_TICK = 5;

    private static double fastNoise(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 2147483647 + seed * 1442968193;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return ((h & 0xFFFF) / 65535.0 - 0.5) * 2.0;
    }

    private static double multiScaleNoise(int x, int y, int z, int seed) {
        double macro = fastNoise(x >> 2, y >> 2, z >> 2, seed) * 0.8;
        double micro = fastNoise(x, y, z, seed ^ 0x5F3759DF) * 0.3;
        return macro + micro;
    }

    public static void explode(ServerLevel level, Vec3 pos, float power) {
        if (power <= 0.0f) return;

        RandomSource random = level.getRandom();

        long currentTick = level.getGameTime();
        if (currentTick != lastSoundResetTick) {
            soundCountThisTick = 0;
            lastSoundResetTick = currentTick;
        }

        if (soundCountThisTick < MAX_EXPLOSION_SOUNDS_PER_TICK) {
            soundCountThisTick++;
            level.playSound(
                    null, pos.x, pos.y, pos.z,
                    net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE
                            //? if >1.20.4
                            .value()
                    ,
                    net.minecraft.sounds.SoundSource.BLOCKS,
                    4.0f, (1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f) * 0.7f
            );
        }

        level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0
        );

        final BlockPos centerPos = BlockPos.containing(pos);
        final boolean isSubmerged = !level.getFluidState(centerPos).isEmpty();

        final int seed = centerPos.hashCode();
        final float maxNoiseOffset = 1.10f;
        final float maxRadius = power + maxNoiseOffset;
        final double maxRadiusSq = maxRadius * maxRadius;

        final int minX = (int) Math.floor(pos.x - maxRadius);
        final int maxX = (int) Math.ceil(pos.x + maxRadius);
        final int minZ = (int) Math.floor(pos.z - maxRadius);
        final int maxZ = (int) Math.ceil(pos.z + maxRadius);

        //? if >1.21.2 {
        final int levelMinY = level.getMinY();
        final int levelMaxY = level.getMaxY();
        //?} else {
        /*final int levelMinY = level.getMinBuildHeight();
        final int levelMaxY = level.getMaxBuildHeight();
        *///?}

        final int minY = Math.max(levelMinY, (int) Math.floor(pos.y - maxRadius));
        final int maxY = Math.min(levelMaxY - 1, (int) Math.ceil(pos.y + maxRadius));

        //? if >1.21.2 {
        final ServerExplosion explosionContext = new ServerExplosion(
                level, null, null, null, pos, power, false, net.minecraft.world.level.Explosion.BlockInteraction.DESTROY
        );
        //?} else {
        
        /*final Explosion explosionContext = new Explosion(
                level, null, pos.x, pos.y, pos.z, power, false, net.minecraft.world.level.Explosion.BlockInteraction.DESTROY
        );
        
        *///?}

        final double entityRadiusSq = (double) power * power;
        final AABB explosionBox = new AABB(
                pos.x - power, pos.y - power, pos.z - power,
                pos.x + power, pos.y + power, pos.z + power
        );

        final List<Entity> entities = level.getEntities((Entity) null, explosionBox, Entity::isAlive);
        final DamageSource damageSource = level.damageSources().explosion(null, null);

        for (Entity entity : entities) {
            if (entity.ignoreExplosion(
                    //? if >1.20.1
                    explosionContext
            )) continue;

            final double distSq = entity.distanceToSqr(pos);
            if (distSq > entityRadiusSq) continue;

            final double dist = Math.sqrt(distSq);
            final double impact = Math.max(0.0, 1.0 - (dist / power));

            final Vec3 entityCenter = entity.getBoundingBox().getCenter();
            final double dx = entityCenter.x - pos.x;
            final double dy = entityCenter.y - pos.y;
            final double dz = entityCenter.z - pos.z;

            final double lengthSq = dx * dx + dy * dy + dz * dz;

            if (lengthSq > 0.0) {
                final double invLength = 1.0 / Math.sqrt(lengthSq);
                final double dirX = dx * invLength;
                final double dirY = dy * invLength;
                final double dirZ = dz * invLength;

                final double speed;
                final double yBoost;

                if (entity instanceof PrimedTnt) {
                    speed = impact * (power * 0.40 + 0.6);
                    yBoost = 0.20 + (impact * 0.30);
                } else {
                    speed = Math.min(1.0, impact * 1.2);
                    yBoost = 0.25 * impact;
                }

                entity.push(dirX * speed, dirY * speed + yBoost, dirZ * speed);
            }

            if (!(entity instanceof PrimedTnt)) {
                final float damage = (float) ((impact * impact + impact) / 2.0 * 7.0 * power + 1.0);
                entity.hurt(damageSource, damage);
            }
        }

        if (isSubmerged) return;

        final Object2IntOpenHashMap<Item> itemDrops = new Object2IntOpenHashMap<>();
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

        final LootParams.Builder lootBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);

        int spawnedTntCount = 0;

        final int minChunkX = minX >> 4;
        final int maxChunkX = maxX >> 4;
        final int minChunkZ = minZ >> 4;
        final int maxChunkZ = maxZ >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            final int chunkMinX = chunkX << 4;
            final int chunkMaxX = chunkMinX + 15;

            final int currentMinX = Math.max(minX, chunkMinX);
            final int currentMaxX = Math.min(maxX, chunkMaxX);

            if (currentMinX > currentMaxX) continue;

            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                final int chunkMinZ = chunkZ << 4;
                final int chunkMaxZ = chunkMinZ + 15;

                final int currentMinZ = Math.max(minZ, chunkMinZ);
                final int currentMaxZ = Math.min(maxZ, chunkMaxZ);

                if (currentMinZ > currentMaxZ) continue;

                final LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                boolean chunkModified = false;

                for (int y = minY; y <= maxY; y++) {
                    final double dy = (y + 0.5) - pos.y;
                    final double dySq = dy * dy;

                    if (dySq > maxRadiusSq) continue;

                    final double horizontalRadiusSq = maxRadiusSq - dySq;
                    if (horizontalRadiusSq <= 0.0) continue;

                    final int sectionIndex = chunk.getSectionIndex(y);
                    final LevelChunkSection section = chunk.getSection(sectionIndex);

                    if (section == null || section.hasOnlyAir()) continue;

                    final int localY = y & 15;

                    for (int x = currentMinX; x <= currentMaxX; x++) {
                        final double dx = (x + 0.5) - pos.x;
                        final double dxSq = dx * dx;

                        if (dxSq > horizontalRadiusSq) continue;

                        final double remainingZSq = horizontalRadiusSq - dxSq;
                        final double zRadius = Math.sqrt(remainingZSq);

                        final int zStart = Math.max(currentMinZ, (int) Math.ceil(pos.z - zRadius - 0.5));
                        final int zEnd = Math.min(currentMaxZ, (int) Math.floor(pos.z + zRadius - 0.5));

                        if (zStart > zEnd) continue;

                        final int localX = x & 15;

                        for (int z = zStart; z <= zEnd; z++) {
                            final int localZ = z & 15;

                            final double dz = (z + 0.5) - pos.z;
                            final double blockDistSq = dxSq + dySq + dz * dz;

                            if (blockDistSq > maxRadiusSq) continue;

                            final BlockState currentState = section.getBlockState(localX, localY, localZ);
                            if (currentState.isAir()) continue;

                            final double effectiveRadius = power + multiScaleNoise(x, y, z, seed);
                            if (effectiveRadius <= 0.0) continue;

                            final double effectiveRadiusSq = effectiveRadius * effectiveRadius;
                            if (blockDistSq > effectiveRadiusSq) continue;

                            mutablePos.set(x, y, z);

                            if (currentState.is(Blocks.TNT)) {
                                section.setBlockState(localX, localY, localZ, Blocks.AIR.defaultBlockState());
                                chunkModified = true;

                                level.getChunkSource().blockChanged(mutablePos);
                                level.getLightEngine().checkBlock(mutablePos);

                                final PrimedTnt primedTnt = new PrimedTnt(level, x + 0.5, y, z + 0.5, null);

                                if (spawnedTntCount < OushiiConfig.maxPrimedPerExplosion) {
                                    spawnedTntCount++;
                                    primedTnt.setFuse(random.nextInt(20) + 20);
                                } else {
                                    primedTnt.setFuse(random.nextInt(6) + 10);
                                }

                                final double pushX = x + 0.5 - pos.x;
                                final double pushY = y + 0.5 - pos.y;
                                final double pushZ = z + 0.5 - pos.z;
                                final double pushLengthSq = pushX * pushX + pushY * pushY + pushZ * pushZ;

                                if (pushLengthSq > 0.0) {
                                    final double pushLength = Math.sqrt(pushLengthSq);
                                    final double impact = Math.max(0.0, 1.0 - (pushLength / power));
                                    final double invLength = 1.0 / pushLength;

                                    final double dirX = pushX * invLength;
                                    final double dirY = pushY * invLength;
                                    final double dirZ = pushZ * invLength;

                                    final double speed = impact * (power * 0.40 + 0.5);
                                    final double yBoost = 0.20 + (impact * 0.30);

                                    primedTnt.setDeltaMovement(dirX * speed, dirY * speed + yBoost, dirZ * speed);
                                } else {
                                    primedTnt.setDeltaMovement(0, 0.4, 0);
                                }

                                level.addFreshEntity(primedTnt);
                                continue;
                            }

                            final float resistance = currentState.getBlock().getExplosionResistance();
                            if (resistance >= 100.0f) continue;

                            section.setBlockState(localX, localY, localZ, Blocks.AIR.defaultBlockState());
                            chunkModified = true;

                            level.getChunkSource().blockChanged(mutablePos);
                            level.getLightEngine().checkBlock(mutablePos);

                            for (Direction direction : Direction.values()) {
                                final int nx = x + direction.getStepX();
                                final int ny = y + direction.getStepY();
                                final int nz = z + direction.getStepZ();

                                final double ndx = nx + 0.5 - pos.x;
                                final double ndy = ny + 0.5 - pos.y;
                                final double ndz = nz + 0.5 - pos.z;

                                if (ndx * ndx + ndy * ndy + ndz * ndz > effectiveRadiusSq) {
                                    neighborPos.set(nx, ny, nz);
                                    final BlockState neighborState = level.getBlockState(neighborPos);

                                    if (!neighborState.getFluidState().isEmpty() || neighborState.getBlock() instanceof FallingBlock) {
                                        // Passer mutablePos au lieu de null évite le NPE dans CollectingNeighborUpdater
                                        level.neighborChanged(neighborPos, currentState.getBlock(),
                                                //$ if >1.20.4 'null' else 'mutablePos'
                                                null
                                        );
                                    }
                                }
                            }

                            lootBuilder.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(mutablePos));
                            final List<ItemStack> drops = currentState.getDrops(lootBuilder);
                            for (ItemStack stack : drops) {
                                itemDrops.addTo(stack.getItem(), stack.getCount());
                            }
                        }
                    }
                }

                if (chunkModified) {
                    //? if >1.21.2 {
                    chunk.markUnsaved();
                    //?} else {
                    /*chunk.setUnsaved(true);
                     *///?}
                }
            }
        }

        itemDrops.forEach((item, count) -> {
            int remaining = count;

            //? if >1.20.4 {
            final int maxStackSize = item.getDefaultMaxStackSize();
            //?} else {
            /*final int maxStackSize = item.getMaxStackSize();
             *///?}

            while (remaining > 0) {
                final int stackSize = Math.min(remaining, maxStackSize);
                remaining -= stackSize;

                final ItemEntity itemEntity = new ItemEntity(
                        level, pos.x, pos.y, pos.z, new ItemStack(item, stackSize)
                );

                level.addFreshEntity(itemEntity);
            }
        });
    }
}