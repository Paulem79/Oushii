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

        //? if !release
        //LOGGER.warn("I'm still a template!");
    }
}
*///?}