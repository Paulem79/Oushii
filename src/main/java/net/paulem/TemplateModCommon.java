package net.paulem;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TemplateModCommon {
    /**This logger is used to write text to the console and the log file.
     * It is considered best practice to use your mod id as the logger's name.
     * That way, it's clear which mod wrote info, warnings, and errors.
     */
    public static final Logger LOGGER = LoggerFactory.getLogger("oushii");
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "1.21.1";

    /**
     * Adapts to the {@link ResourceLocation} changes introduced in 1.21.
     */
    public static ResourceLocation id(String namespace, String path) {
        //? if <1.21 {
        /*return new ResourceLocation(namespace, path);
         *///?} else
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
