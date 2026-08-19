package net.paulem;

//? if forge {
/*//? if !1.20.1 || !forge
import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraftforge.fml.common.Mod;
import net.paulem.config.OushiiConfig;

import static net.paulem.TemplateModCommon.*;

@Mod("oushii")
public class TemplateModForge {
    public TemplateModForge() {
        LOGGER.info("Hello Forge world!");

        //? if !1.20.1 || !forge {
        MidnightConfig.init("oushii", OushiiConfig.class);
        //?} else {
        /^OushiiConfig.init("oushii", OushiiConfig.class);
        ^///?}

        //? if !release
        //LOGGER.warn("I'm still a template!");
    }
}
*///?}