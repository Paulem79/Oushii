/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/
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
