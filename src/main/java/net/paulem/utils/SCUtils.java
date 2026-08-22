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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public class SCUtils {
    private SCUtils() {
        /* This utility class should not be instantiated */
    }

    public static int getLevelMinY(Level level) {
        return level
                //$ if >1.21.2 '.getMinY();' else '.getMinBuildHeight();'
                .getMinY();
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
        return ItemStack.
                //$ if >1.20.4 'isSameItemSameComponents(first, second);' else 'isSameItemSameTags(first, second);'
                isSameItemSameComponents(first, second);
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
}
