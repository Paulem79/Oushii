package net.paulem;

//? if neoforge {
/*import eu.midnightdust.lib.config.MidnightConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.paulem.config.OushiiConfig;

import static net.paulem.OushiiCommon.*;

@Mod("oushii")
public class OushiiNeoForge {
    public OushiiNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Hello NeoForge world!");

        MidnightConfig.init("oushii", OushiiConfig.class);

        //? if !release
        LOGGER.warn("I'm still a template!");
    }
}
*///?}