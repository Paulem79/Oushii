/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/
package net.paulem;

import net.minecraft.resources.Identifier;

// SLF4J only joined the Minecraft library set in 1.17, so 1.16.5 logs through Log4j directly
//? if >1.16.5 {
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//?} else {
/*import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
*///?}

public class OushiiCommon {
    /**This logger is used to write text to the console and the log file.
     * It is considered best practice to use your mod id as the logger's name.
     * That way, it's clear which mod wrote info, warnings, and errors.
     */
    public static final Logger LOGGER =
            //$ if >1.16.5 'LoggerFactory.getLogger("oushii");' else 'LogManager.getLogger("oushii");'
            LoggerFactory.getLogger("oushii");
    public static final String VERSION = /*$ mod_version*/ "1.0.1";
    public static final String MINECRAFT = /*$ minecraft*/ "26.2";

    /**
     * Adapts to the {@link Identifier} changes introduced in 1.21.
     */
    public static Identifier id(String namespace, String path) {
        //? if <1.21 {
        /*return new Identifier(namespace, path);
         *///?} else
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
