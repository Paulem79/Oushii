/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/
package net.paulem;

//? if forge {
/*//? if hasMidnightLib
import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraftforge.fml.common.Mod;
import net.paulem.config.OushiiConfig;

import static net.paulem.OushiiCommon.*;

@Mod("oushii")
public class OushiiForge {
    public OushiiForge() {
        LOGGER.info("Hello Forge world!");

        //? if hasMidnightLib {
        MidnightConfig.init("oushii", OushiiConfig.class);
        //?} else {
        /^OushiiConfig.init("oushii", OushiiConfig.class);
        ^///?}
    }
}
*///?}