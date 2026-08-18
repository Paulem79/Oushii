package net.paulem;

//? if fabric {
import net.fabricmc.api.ModInitializer;

import static net.paulem.TemplateModCommon.*;

public class TemplateModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        LOGGER.info("Hello Fabric world!");

        //? if !release
        //LOGGER.warn("I'm still a template!");
    }
}
//?}