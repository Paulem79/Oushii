/*
 * Copyright (C) 2026 Paulem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*/
package net.paulem;

//? if neoforge {
/*import eu.midnightdust.lib.config.MidnightConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.paulem.config.OushiiConfig;

import static net.paulem.OushiiCommon.*;

@Mod("oushii")
public class OushiiNeoForge {
    public OushiiNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Hello NeoForge world!");

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MidnightConfig.init("oushii", OushiiConfig.class);
        });
    }
}
*///?}