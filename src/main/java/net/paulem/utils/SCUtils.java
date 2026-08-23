/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/
package net.paulem.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class SCUtils {
    private SCUtils() {
        /* This utility class should not be instantiated */
    }

    /** Worlds were still pinned to {@code y = 0} before the 1.17 height changes. */
    public static int getLevelMinY(Level level) {
        //? if >1.21.2 {
        return level.getMinY();
        //?} elif >1.16.5 {
        /*return level.getMinBuildHeight();
        *///?} else {
        /*return 0;
        *///?}
    }

    public static int getLevelMaxY(Level level) {
        return level
                //$ if >1.21.2 '.getMaxY();' else '.getMaxBuildHeight();'
                .getMaxY();
    }

    /**
     * Writes a block through the chunk instead of {@link Level#setBlock}: heightmaps, sky light sources,
     * section emptiness and block entity removal are still handled, but neighbour updates and the
     * client packet are left to the caller, which is where the explosion engine saves its time.
     */
    public static void setBlockInChunk(LevelChunk chunk, BlockPos pos, BlockState state) {
        chunk.setBlockState(pos, state,
                //$ if >1.21.4 '0);' else 'false);'
                0);
    }

    /** {@code true} when both stacks hold the same item with the same NBT/components. */
    public static boolean isSameItemAndData(ItemStack first, ItemStack second) {
        //? if >1.20.4 {
        return ItemStack.isSameItemSameComponents(first, second);
        //?} elif >1.16.5 {
        /*return ItemStack.isSameItemSameTags(first, second);
        *///?} else {
        /*return ItemStack.isSame(first, second) && ItemStack.tagMatches(first, second);
        *///?}
    }

    public static int getMaxStackSize(Item item) {
        return item.
                //$ if >1.20.4 'getDefaultMaxStackSize();' else 'getMaxStackSize();'
                getDefaultMaxStackSize();
    }

    public static void markChunkUnsaved(LevelChunk chunk) {
        //? if >1.21.2 {
        chunk.markUnsaved();
        //?} else {
        /*chunk.setUnsaved(true);
        *///?}
    }

    /** Index of the section holding {@code y}; sections were a plain {@code y >> 4} before 1.17. */
    public static int getSectionIndex(LevelChunk chunk, int y) {
        //? if >1.16.5 {
        return chunk.getSectionIndex(y);
        //?} else {
        /*return y >> 4;
        *///?}
    }

    /** {@code true} for a missing section as well, since those hold nothing but air. */
    public static boolean isSectionEmpty(LevelChunkSection section) {
        if (section == null) return true;
        //? if >1.17.1 {
        return section.hasOnlyAir();
        //?} else {
        /*return section.isEmpty();
        *///?}
    }

    public static boolean hasBlockEntity(BlockState state) {
        //? if >1.16.5 {
        return state.hasBlockEntity();
        //?} else {
        /*return state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock;
        *///?}
    }

    /** Removes an entity from the world without dropping anything. */
    public static void discard(Entity entity) {
        //? if >1.16.5 {
        entity.discard();
        //?} else {
        /*entity.remove();
        *///?}
    }
}
