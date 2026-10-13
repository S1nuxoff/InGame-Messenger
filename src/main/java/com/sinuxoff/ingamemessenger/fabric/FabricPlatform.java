package com.sinuxoff.ingamemessenger.fabric;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.sinuxoff.ingamemessenger.FileSettings;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.ModSettings;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.Platform;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class FabricPlatform implements Platform {
    private final ModSettings settings = new FileSettings(configDir().resolve("server.toml"));
    volatile MinecraftServer server;
    /** Set by the client side; stays a no-op on a dedicated server. */
    volatile Consumer<Packet> toServer = p -> {
    };

    @Override
    public String loader() {
        return "Fabric Loader " + FabricLoader.getInstance().getModContainer("fabricloader")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(InGameMessenger.MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    @Override
    public Path configDir() {
        return Platform.ensureDir(FabricLoader.getInstance().getConfigDir().resolve(InGameMessenger.MOD_ID));
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public List<String> mods() {
        List<String> out = new ArrayList<>();
        for (var m : FabricLoader.getInstance().getAllMods()) {
            var meta = m.getMetadata();
            String id = meta.getId();
            // skip Fabric API modules, the loader, Java and Minecraft
            if (m.getContainingMod().isEmpty() && !id.startsWith("fabric") && !id.equals("java") && !id.equals("minecraft")
                    && !id.equals("mixinextras")) {
                out.add(meta.getName());
            }
        }
        return out;
    }

    @Override
    public ModSettings settings() {
        return settings;
    }

    @Override
    public MinecraftServer server() {
        return server;
    }

    @Override
    public void sendToServer(Packet packet) {
        toServer.accept(packet);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Packet packet) {
        if (FabricMod.canSend(player, Net.type(packet.type()))) {
            ServerPlayNetworking.send(player, new Net.Payload(packet));
        }
    }
}
