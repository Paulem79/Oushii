package net.paulem.utils;

import net.minecraft.world.level.Level;

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
}
