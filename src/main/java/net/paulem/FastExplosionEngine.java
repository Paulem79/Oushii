/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.paulem;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.paulem.config.OushiiConfig;
import net.paulem.utils.SCUtils;

import net.minecraft.world.level.
        //$ if >1.21.2 'ServerExplosion;' else 'Explosion;'
        ServerExplosion;

import java.util.Arrays;
import java.util.List;

/**
 * Blast solver.
 *
 * <p>Vanilla shoots 1352 rays out of the centre and burns {@code 0.225} of ray strength every
 * {@code 0.3} blocks, plus {@code (resistance + 0.3) * 0.3} whenever the sample lands inside matter.
 * That is around 24k block lookups for a single TNT, most of them redundant because every ray re-walks
 * the blocks its neighbours already walked.
 *
 * <p>Oushii solves the very same energy budget, but on the block grid instead of along rays: cells are
 * visited outwards from the centre and each one takes the energy left by the neighbours sitting one
 * step closer to the centre, weighted by the direction it came from, minus what its own block costs.
 * The result keeps what makes a vanilla crater recognisable - shallow in stone, deep in dirt, stopped
 * by obsidian, shadowed behind whatever survived - while touching every block at most once.
 */
public final class FastExplosionEngine {

    private FastExplosionEngine() {}

    /** Energy burnt per block of travel, whatever the medium (vanilla: 0.225 every 0.3 blocks). */
    private static final double TRAVEL_COST = 0.75;
    /** Energy burnt on top of that inside matter (vanilla: (resistance + 0.3) * 0.3 every 0.3 blocks). */
    private static final double MATERIAL_COST = 0.3;
    /** Vanilla rolls each ray at 0.7 to 1.3 times the power; we jitter the local cost instead. */
    private static final double COST_JITTER = 0.25;
    /** How far a vanilla ray steps into a block before deciding whether it breaks. */
    private static final double ENTRY_FRACTION = 0.3;
    /** Rays vanilla would spend on one block of a typical crater, as {@code RAY_COVERAGE / power^2}. */
    private static final double RAY_COVERAGE = 176.0;
    /** Farthest a blast can travel through open air, as a multiple of its power. */
    private static final double MAX_REACH = 1.0 / (TRAVEL_COST * (1.0 - COST_JITTER));
    /** The field is {@code (2r+1)^3} floats, so merged clusters need a ceiling. */
    private static final int MAX_FIELD_RADIUS = 48;
    /** Past that many candidates, line of sight sampling is dropped and everyone counts as exposed. */
    private static final int MAX_EXPOSURE_CHECKS = 256;
    private static final int MAX_EXPLOSION_SOUNDS_PER_TICK = 5;

    /** {@link Direction#values()} clones its array on every call, and this one is read per broken block. */
    private static final Direction[] DIRECTIONS = Direction.values();

    /** Same set vanilla hurts: living or not, but never a spectator. */
    private static final java.util.function.Predicate<Entity> BLAST_TARGETS =
            entity -> entity.isAlive() && !entity.isSpectator();

    private static int soundCountThisTick = 0;
    private static long lastSoundResetTick = -1L;

    // Server thread only: reused between explosions so a chain reaction allocates nothing.
    private static float[] blastField = new float[0];
    private static float[] costField = new float[0];
    private static float[] stepTable = new float[0];
    private static int stepTableRadius = -1;
    private static long[] brokenPositions = new long[4096];
    private static final Object2ObjectOpenHashMap<Item, PendingDrop> PENDING_DROPS = new Object2ObjectOpenHashMap<>();

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

        final RandomSource random = level.getRandom();

        final long currentTick = level.getGameTime();
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

        // Vanilla only spawns the big emitter for blasts of power 2 and above
        level.sendParticles(
                power >= 2.0f
                        ? net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER
                        : net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0
        );

        //? if >1.19.3 {
        final BlockPos centerPos = BlockPos.containing(pos);
         //?} else {
        /*final BlockPos centerPos = new BlockPos(pos);
        *///?}

        //? if >1.21.2 {
        final ServerExplosion explosionContext = new ServerExplosion(
                level, null, null, null, pos, power, false, net.minecraft.world.level.Explosion.BlockInteraction.DESTROY
        );
        //?} else {
        /*final Explosion explosionContext = new Explosion(
                level, null, pos.x, pos.y, pos.z, power, false, net.minecraft.world.level.Explosion.BlockInteraction.DESTROY
        );
        *///?}

        hurtEntities(level, pos, power, explosionContext);
        breakBlocks(level, centerPos, power, random);
    }

    // ------------------------------------------------------------------ entities

    private static void hurtEntities(ServerLevel level, Vec3 pos, float power,
            //? if >1.21.2 {
            ServerExplosion explosionContext
            //?} else {
            /*Explosion explosionContext
            *///?}
    ) {
        // Vanilla reaches twice the power, and scales both damage and knockback on that doubled radius
        final float reach = power * 2.0f;
        final AABB explosionBox = new AABB(
                pos.x - reach - 1.0, pos.y - reach - 1.0, pos.z - reach - 1.0,
                pos.x + reach + 1.0, pos.y + reach + 1.0, pos.z + reach + 1.0
        );

        final List<Entity> entities = level.getEntities((Entity) null, explosionBox, BLAST_TARGETS);
        if (entities.isEmpty()) return;

        //? if >1.19.3 {
        final DamageSource damageSource = level.damageSources().explosion(null, null);
         //?} else {
        /*final DamageSource damageSource = DamageSource.explosion(explosionContext);
        *///?}

        // Sampling line of sight is the one part of vanilla that scales with the entity count, so it is
        // dropped once a blast is packed with entities (TNT chains, item showers) rather than dragging
        // the whole tick down with it.
        final boolean checkExposure = entities.size() <= MAX_EXPOSURE_CHECKS;

        for (Entity entity : entities) {
            if (entity.ignoreExplosion(
                    //? if >1.20.1
                    explosionContext
            )) continue;

            final double relativeDistance = Math.sqrt(entity.distanceToSqr(pos)) / reach;
            if (relativeDistance > 1.0) continue;

            final boolean isTnt = entity instanceof PrimedTnt;

            double dx = entity.getX() - pos.x;
            double dy = (isTnt ? entity.getY() : entity.getEyeY()) - pos.y;
            double dz = entity.getZ() - pos.z;
            final double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length == 0.0) continue;

            dx /= length;
            dy /= length;
            dz /= length;

            // Primed TNT takes no damage anyway, so it never pays for a line of sight check
            final double exposure = (isTnt || !checkExposure) ? 1.0 : seenPercent(level, pos, entity);
            final double impact = (1.0 - relativeDistance) * exposure;

            if (!isTnt) {
                entity.hurt(damageSource, (float) ((int) ((impact * impact + impact) / 2.0 * 7.0 * reach + 1.0)));
            }

            entity.push(dx * impact, dy * impact, dz * impact);
        }
    }

    /** Three samples along the entity instead of the ~200 casts vanilla makes, same 0 to 1 meaning. */
    private static double seenPercent(ServerLevel level, Vec3 origin, Entity entity) {
        final AABB box = entity.getBoundingBox();
        final double centerX = (box.minX + box.maxX) * 0.5;
        final double centerZ = (box.minZ + box.maxZ) * 0.5;
        final double height = box.maxY - box.minY;

        int visible = 0;
        for (int sample = 0; sample < 3; sample++) {
            final Vec3 target = new Vec3(centerX, box.minY + height * (0.05 + 0.45 * sample), centerZ);
            final ClipContext context = new ClipContext(
                    origin, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity
            );
            if (level.clip(context).getType() == HitResult.Type.MISS) visible++;
        }
        return visible / 3.0;
    }

    // ------------------------------------------------------------------ blocks

    private static void breakBlocks(ServerLevel level, BlockPos centerPos, float power, RandomSource random) {
        final double strength = power * rayCoverageScale(power);
        final int radius = Math.min(MAX_FIELD_RADIUS, Math.max(1, Mth.ceil(strength * MAX_REACH)));
        final int radiusSq = radius * radius;
        final int size = radius * 2 + 1;
        final int strideZ = size;
        final int strideY = size * size;

        final int cells = size * strideY;
        final float[] blast = field(cells);
        final float[] cellCost = costs(cells);
        final float[] steps = stepTable(radius);
        final int stepStride = stepTableRadius + 1;

        final int centerX = centerPos.getX();
        final int centerY = centerPos.getY();
        final int centerZ = centerPos.getZ();
        final int seed = centerPos.hashCode();

        final int worldMinY = SCUtils.getLevelMinY(level);
        final int worldMaxY = SCUtils.getLevelMaxY(level) - 1;
        if (centerY < worldMinY || centerY > worldMaxY) return;

        // Vanilla only decays TNT drops when the game rule asks for it, and it does not by default
        //? if >1.18.2 {
        final float dropChance = 1.0f;
        //?} else {
        /*final float dropChance = 1.0f / power;
        *///?}

        final BlockState air = Blocks.AIR.defaultBlockState();
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

        final var chunkSource = level.getChunkSource();
        final var lightEngine = level.getLightEngine();

        LevelChunk chunk = null;
        LevelChunkSection section = null;
        boolean sectionIsAir = true;
        boolean chunkDirty = false;
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;
        int cachedSectionIndex = Integer.MIN_VALUE;

        int brokenCount = 0;
        int spawnedTnt = 0;

        // Octants are walked positive first so the cells a cell reads from are always solved already
        for (int ySign = 1; ySign >= -1; ySign -= 2) {
            for (int zSign = 1; zSign >= -1; zSign -= 2) {
                for (int xSign = 1; xSign >= -1; xSign -= 2) {
                    // The three centre planes belong to the positive octants, so they are solved once
                    final int firstY = ySign > 0 ? 0 : 1;
                    final int firstZ = zSign > 0 ? 0 : 1;
                    final int firstX = xSign > 0 ? 0 : 1;

                    for (int dy = firstY; dy <= radius; dy++) {
                        final int y = centerY + ySign * dy;
                        if (y < worldMinY || y > worldMaxY) break;

                        final int leftAfterY = radiusSq - dy * dy;
                        if (leftAfterY < 0) break;

                        final int gridY = radius + ySign * dy;
                        // Every cell of the next slab draws from this one, so once a whole slab is spent
                        // there is nothing left to carry outwards
                        boolean slabAlive = dy == 0;

                        for (int dz = firstZ; dz <= radius; dz++) {
                            final int leftAfterZ = leftAfterY - dz * dz;
                            if (leftAfterZ < 0) break;

                            final int z = centerZ + zSign * dz;
                            final int gridZ = radius + zSign * dz;
                            final int rowStart = (gridY * size + gridZ) * size + radius;
                            final int stepRow = (dy * stepStride + dz) * stepStride;
                            final int maxDx = (int) Math.sqrt(leftAfterZ);

                            for (int dx = firstX; dx <= maxDx; dx++) {
                                final int x = centerX + xSign * dx;
                                final int index = rowStart + xSign * dx;

                                final int manhattan = dx + dy + dz;
                                final double incoming;
                                final double step;

                                if (manhattan == 0) {
                                    incoming = strength;
                                    step = 0.0;
                                } else {
                                    // Distance covered inside this cell; the table is built so that summing
                                    // it along any inward path gives back the straight line distance
                                    step = steps[stepRow + dx];
                                    final double half = step * 0.5;

                                    // Energy reaching the middle of this cell, taken from the neighbours one
                                    // step closer to the centre and weighted by the direction it came from.
                                    // Neighbours that cannot pay their half of the way are left out rather
                                    // than averaged in, otherwise a wall on one side would dim a clear line
                                    // of sight on another.
                                    double sum = 0.0;
                                    int weight = 0;
                                    if (dx > 0) {
                                        final int parent = index - xSign;
                                        final double delivered = blast[parent] - half * cellCost[parent];
                                        if (delivered > 0.0) {
                                            sum += dx * delivered;
                                            weight += dx;
                                        }
                                    }
                                    if (dy > 0) {
                                        final int parent = index - ySign * strideY;
                                        final double delivered = blast[parent] - half * cellCost[parent];
                                        if (delivered > 0.0) {
                                            sum += dy * delivered;
                                            weight += dy;
                                        }
                                    }
                                    if (dz > 0) {
                                        final int parent = index - zSign * strideZ;
                                        final double delivered = blast[parent] - half * cellCost[parent];
                                        if (delivered > 0.0) {
                                            sum += dz * delivered;
                                            weight += dz;
                                        }
                                    }
                                    if (weight == 0) {
                                        blast[index] = 0.0f;
                                        continue;
                                    }
                                    incoming = sum / weight;
                                }

                                final int chunkX = x >> 4;
                                final int chunkZ = z >> 4;
                                if (chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
                                    if (chunkDirty) {
                                        SCUtils.markChunkUnsaved(chunk);
                                        chunkDirty = false;
                                    }
                                    chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                                    cachedChunkX = chunkX;
                                    cachedChunkZ = chunkZ;
                                    cachedSectionIndex = Integer.MIN_VALUE;
                                }

                                // Never drag a chunk in from disk mid-blast; unloaded terrain just stops it
                                if (chunk == null) {
                                    blast[index] = 0.0f;
                                    continue;
                                }

                                final int sectionIndex = chunk.getSectionIndex(y);
                                if (sectionIndex != cachedSectionIndex) {
                                    cachedSectionIndex = sectionIndex;
                                    final LevelChunkSection[] sections = chunk.getSections();
                                    section = (sectionIndex >= 0 && sectionIndex < sections.length)
                                            ? sections[sectionIndex] : null;
                                    sectionIsAir = section == null || section.hasOnlyAir();
                                }

                                // Empty sections skip the palette read entirely, which is most of an open air blast
                                final BlockState state = sectionIsAir
                                        ? null : section.getBlockState(x & 15, y & 15, z & 15);

                                double cost = TRAVEL_COST;
                                if (state != null && !state.isAir()) {
                                    float resistance = state.getBlock().getExplosionResistance();
                                    final FluidState fluid = state.getFluidState();
                                    if (!fluid.isEmpty()) {
                                        final float fluidResistance = fluid.getExplosionResistance();
                                        if (fluidResistance > resistance) resistance = fluidResistance;
                                    }
                                    cost += resistance + MATERIAL_COST;
                                }
                                cost *= 1.0 + COST_JITTER * multiScaleNoise(x, y, z, seed);
                                cellCost[index] = (float) cost;

                                // Energy left in the middle of the cell
                                final double middle = incoming - cost * step * 0.5;
                                if (middle > 0.0) {
                                    blast[index] = (float) middle;
                                    slabAlive = true;
                                } else {
                                    blast[index] = 0.0f;
                                }

                                if (state == null || state.isAir()) continue;

                                // Vanilla breaks a block as soon as a ray can pay its way *into* it, one
                                // 0.3 sample deep, not once it has paid its way through: that is what gives
                                // craters their rim of blocks the blast could not actually get past
                                if (middle + (step * 0.5 - ENTRY_FRACTION) * cost <= 0.0) continue;

                                // A rim of broken blocks can outlive the energy that broke it, and the
                                // slab past it still has to be solved for the edge pass to read it back
                                slabAlive = true;

                                // ---- this block is destroyed ----
                                mutablePos.set(x, y, z);
                                final boolean hasBlockEntity = state.hasBlockEntity();
                                final BlockPos blockPos = hasBlockEntity ? mutablePos.immutable() : mutablePos;

                                if (brokenCount == brokenPositions.length) {
                                    brokenPositions = Arrays.copyOf(brokenPositions, brokenCount * 2);
                                }
                                brokenPositions[brokenCount++] = BlockPos.asLong(x, y, z);

                                if (state.is(Blocks.TNT)) {
                                    removeBlock(chunk, blockPos, air, chunkSource, lightEngine);
                                    chunkDirty = true;

                                    final PrimedTnt primedTnt = new PrimedTnt(level, x + 0.5, y, z + 0.5, null);
                                    // Vanilla spreads the chain over 10 to 29 ticks; past the cap they are pulled
                                    // closer together so the cluster pass can merge them
                                    primedTnt.setFuse(spawnedTnt++ < OushiiConfig.maxPrimedPerExplosion
                                            ? random.nextInt(20) + 10
                                            : random.nextInt(6) + 10);
                                    level.addFreshEntity(primedTnt);
                                    continue;
                                }

                                if (dropChance >= 1.0f || random.nextFloat() < dropChance) {
                                    final BlockEntity blockEntity = hasBlockEntity ? chunk.getBlockEntity(blockPos) : null;
                                    final List<ItemStack> drops = Block.getDrops(state, level, blockPos, blockEntity);
                                    for (int i = 0; i < drops.size(); i++) {
                                        addDrop(level, drops.get(i), x + 0.5, y + 0.5, z + 0.5);
                                    }
                                }

                                removeBlock(chunk, blockPos, air, chunkSource, lightEngine);
                                chunkDirty = true;
                            }
                        }

                        if (!slabAlive) break;
                    }
                }
            }
        }

        if (chunkDirty) SCUtils.markChunkUnsaved(chunk);

        updateBlastEdge(level, blast, brokenCount, radius, radiusSq, size,
                centerX, centerY, centerZ, worldMinY, worldMaxY, mutablePos, neighborPos);

        flushDrops(level);
    }

    private static void removeBlock(LevelChunk chunk, BlockPos pos, BlockState air,
                                    net.minecraft.server.level.ServerChunkCache chunkSource,
                                    net.minecraft.world.level.lighting.LevelLightEngine lightEngine) {
        SCUtils.setBlockInChunk(chunk, pos, air);
        chunkSource.blockChanged(pos);
        lightEngine.checkBlock(pos);
    }

    /**
     * Vanilla notifies the six neighbours of every broken block; almost all of those updates land on
     * blocks the blast just cleared. Only the ones sitting outside the blast can still react, and of
     * those only fluids and falling blocks actually have something to do.
     */
    private static void updateBlastEdge(ServerLevel level, float[] blast, int brokenCount,
                                        int radius, int radiusSq, int size,
                                        int centerX, int centerY, int centerZ, int worldMinY, int worldMaxY,
                                        BlockPos.MutableBlockPos sourcePos, BlockPos.MutableBlockPos neighborPos) {
        for (int i = 0; i < brokenCount; i++) {
            final long packed = brokenPositions[i];
            final int x = BlockPos.getX(packed);
            final int y = BlockPos.getY(packed);
            final int z = BlockPos.getZ(packed);

            for (Direction direction : DIRECTIONS) {
                final int nx = x + direction.getStepX();
                final int ny = y + direction.getStepY();
                final int nz = z + direction.getStepZ();
                if (ny < worldMinY || ny > worldMaxY) continue;

                final int dx = nx - centerX;
                final int dy = ny - centerY;
                final int dz = nz - centerZ;

                // Inside the blast with energy left means it is already air, nothing to notify
                if (dx * dx + dy * dy + dz * dz <= radiusSq
                        && blast[((radius + dy) * size + (radius + dz)) * size + (radius + dx)] > 0.0f) continue;

                neighborPos.set(nx, ny, nz);
                final BlockState neighbor = level.getBlockState(neighborPos);
                if (!neighbor.getFluidState().isEmpty() || neighbor.getBlock() instanceof FallingBlock) {
                    sourcePos.set(x, y, z);
                    level.neighborChanged(neighborPos, Blocks.AIR,
                            //$ if >1.21.4 'null' else 'sourcePos'
                            null
                    );
                }
            }
        }
    }

    // ------------------------------------------------------------------ drops

    /**
     * Vanilla merges the drops of one explosion by scanning the whole pending list per stack, which is
     * quadratic. One open slot per item is enough to collapse a crater worth of stone into full stacks,
     * and stacks carrying data (shulker boxes, decorated pots, spawners) simply never merge.
     */
    private static final class PendingDrop {
        private ItemStack stack = ItemStack.EMPTY;
        private double x;
        private double y;
        private double z;
    }

    private static void addDrop(ServerLevel level, ItemStack stack, double x, double y, double z) {
        if (stack.isEmpty()) return;

        final Item item = stack.getItem();
        final int maxStackSize = SCUtils.getMaxStackSize(item);

        if (stack.getCount() >= maxStackSize) {
            spawnDrop(level, stack, x, y, z);
            return;
        }

        PendingDrop pending = PENDING_DROPS.get(item);
        if (pending == null) {
            pending = new PendingDrop();
            PENDING_DROPS.put(item, pending);
        } else if (!pending.stack.isEmpty()) {
            if (SCUtils.isSameItemAndData(pending.stack, stack)
                    && pending.stack.getCount() + stack.getCount() <= maxStackSize) {
                pending.stack.grow(stack.getCount());
                pending.x = x;
                pending.y = y;
                pending.z = z;
                if (pending.stack.getCount() >= maxStackSize) {
                    spawnDrop(level, pending.stack, x, y, z);
                    pending.stack = ItemStack.EMPTY;
                }
                return;
            }
            spawnDrop(level, pending.stack, pending.x, pending.y, pending.z);
        }

        pending.stack = stack;
        pending.x = x;
        pending.y = y;
        pending.z = z;
    }

    private static void flushDrops(ServerLevel level) {
        if (PENDING_DROPS.isEmpty()) return;
        for (PendingDrop pending : PENDING_DROPS.values()) {
            if (!pending.stack.isEmpty()) spawnDrop(level, pending.stack, pending.x, pending.y, pending.z);
        }
        PENDING_DROPS.clear();
    }

    private static void spawnDrop(ServerLevel level, ItemStack stack, double x, double y, double z) {
        level.addFreshEntity(new ItemEntity(level, x, y, z, stack));
    }

    private static float[] field(int cells) {
        if (blastField.length < cells) blastField = new float[cells];
        return blastField;
    }

    private static float[] costs(int cells) {
        if (costField.length < cells) costField = new float[cells];
        return costField;
    }

    /**
     * Vanilla samples the blast with a fixed 1352 rays, so a block sitting close to the centre is
     * covered several times over and only needs one lucky roll out of {@code [0.7, 1.3]} to go, while
     * a block far out may not be sampled at all. A field solver has no such luck of the draw, so the
     * power is scaled by what the best of the covering rays would typically have carried. It lands
     * near {@code 1.25} for a lone TNT and drops below {@code 1} for merged clusters, which is also
     * what keeps a big cluster from scanning a needlessly wide field.
     */
    private static double rayCoverageScale(float power) {
        final double rays = RAY_COVERAGE / (power * power);
        return 0.7 + 0.6 * rays / (rays + 1.0);
    }

    /**
     * Distance the blast covers inside each cell, indexed by {@code |dx|, |dy|, |dz|}. Built so that
     * adding it up along any inward path returns the straight line distance to the centre, which is
     * what keeps the front spherical instead of collapsing into a Manhattan diamond. It only depends
     * on the geometry, so it is built once for the largest blast seen so far.
     */
    private static float[] stepTable(int radius) {
        if (radius <= stepTableRadius) return stepTable;

        final int side = radius + 1;
        final float[] table = new float[side * side * side];

        for (int ay = 0; ay <= radius; ay++) {
            for (int az = 0; az <= radius; az++) {
                for (int ax = 0; ax <= radius; ax++) {
                    final int index = (ay * side + az) * side + ax;
                    final int manhattan = ax + ay + az;
                    if (manhattan == 0) continue;

                    final double distance = Math.sqrt(ax * ax + ay * ay + az * az);
                    double inward = 0.0;
                    if (ax > 0) inward += ax * Math.sqrt((ax - 1) * (ax - 1) + ay * ay + az * az);
                    if (ay > 0) inward += ay * Math.sqrt(ax * ax + (ay - 1) * (ay - 1) + az * az);
                    if (az > 0) inward += az * Math.sqrt(ax * ax + ay * ay + (az - 1) * (az - 1));

                    table[index] = (float) (distance - inward / manhattan);
                }
            }
        }

        stepTable = table;
        stepTableRadius = radius;
        return table;
    }
}
