package net.paulem;

public sealed interface ModLoaderAccess {
    ModLoaderAccess INSTANCE =
            /*? if fabric{*/new FabricLoaderAccess();
    /*?} elif neoforge {*///new NeoForgeLoaderAccess();
    /*?} elif forge *///new ForgeLoaderAccess();

    boolean isClient();
    boolean isServer();

    boolean isModLoaded(String id);

    //? if fabric {
    final class FabricLoaderAccess implements ModLoaderAccess {
        private net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();

        @Override
        public boolean isClient() {
            return loader.getEnvironmentType().equals(net.fabricmc.api.EnvType.CLIENT);
        }

        @Override
        public boolean isServer() {
            return loader.getEnvironmentType().equals(net.fabricmc.api.EnvType.SERVER);
        }

        @Override
        public boolean isModLoaded(String id) {
            return loader.isModLoaded(id);
        }
    }
    //?} elif neoforge {
    /*final class NeoForgeLoaderAccess implements ModLoaderAccess {
        private net.neoforged.api.distmarker.Dist dist =
            /^? if >=1.21.9 {^/net.neoforged.fml.loading.FMLEnvironment.getDist();
            /^?} else^///net.neoforged.fml.loading.FMLEnvironment.dist;
        private net.neoforged.fml.loading.LoadingModList mods =
            /^? if >=1.21.9 {^/net.neoforged.fml.loading.FMLLoader.getCurrent().getLoadingModList();
            /^?} else^///net.neoforged.fml.loading.FMLLoader.getLoadingModList();

        @Override
        public boolean isClient() {
            return dist.isClient();
        }

        @Override
        public boolean isServer() {
            return dist.isDedicatedServer();
        }

        @Override
        public boolean isModLoaded(String id) {
            return mods.getModFileById(id) != null;
        }
    }
    *///?} elif forge {
    /*final class ForgeLoaderAccess implements ModLoaderAccess {
        @Override
        public boolean isClient() {
            return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient();
        }

        @Override
        public boolean isServer() {
            return net.minecraftforge.fml.loading.FMLEnvironment.dist.isDedicatedServer();
        }

        @Override
        public boolean isModLoaded(String id) {
            return net.minecraftforge.fml.ModList.get().isLoaded(id);
        }
    }
    *///?}
}