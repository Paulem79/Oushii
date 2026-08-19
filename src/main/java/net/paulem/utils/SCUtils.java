package net.paulem.utils;

import net.minecraft.world.level.Level;

public class SCUtils {
    public static int getLevelMinY(Level level) {
        //? if >1.21.2 {
        return level.getMinY();
        //?} else {
        /*return level.getMinBuildHeight();
        *///?}
    }

    public static int getLevelMaxY(Level level) {
        //? if >1.21.2 {
        return level.getMaxY();
        //?} else {
        /*return level.getMaxBuildHeight();
        *///?}
    }
}
