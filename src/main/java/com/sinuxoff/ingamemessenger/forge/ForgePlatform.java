package com.sinuxoff.ingamemessenger.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.sinuxoff.ingamemessenger.core.server.FileSettings;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.core.server.ModSettings;
import com.sinuxoff.ingamemessenger.Platform;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ForgePlatform implements Platform {
    // 26.1 made ModList's methods static
    //? if >=26.1 {
    private static java.util.Optional<? extends net.minecraftforge.fml.ModContainer> container(String id) {
        return ModList.getModContainerById(id);
    }

    private static boolean loaded(String id) {
        return ModList.isLoaded(id);
    }

    private static List<net.minecraftforge.forgespi.language.IModInfo> allMods() {
        return ModList.getMods();
    }
    //?} else {
    /*private static java.util.Optional<? extends net.minecraftforge.fml.ModContainer> container(String id) {
        return ModList.get().getModContainerById(id);
    }

    private static boolean loaded(String id) {
        return ModList.get().isLoaded(id);
    }

    private static List<net.minecraftforge.forgespi.language.IModInfo> allMods() {
        return ModList.get().getMods();
    }
    *///?}

    private final ModSettings settings = new FileSettings(configDir().resolve("server.toml"), InGameMessenger.LOG::warn);

    @Override
    public String loader() {
        return "Forge " + container("forge").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    @Override
    public String modVersion() {
        return container(InGameMessenger.MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    @Override
    public Path configDir() {
        return Platform.ensureDir(FMLPaths.CONFIGDIR.get().resolve(InGameMessenger.MOD_ID));
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return loaded(modId);
    }

    @Override
    public ModSettings settings() {
        return settings;
    }

    @Override
    public MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    @Override
    public void sendToServer(Packet packet) {
        ForgeNet.toServer(packet);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Packet packet) {
        ForgeNet.toPlayer(player, packet);
    }
}
