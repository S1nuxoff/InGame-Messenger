package com.sinuxoff.ingamemessenger.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.sinuxoff.ingamemessenger.core.server.FileSettings;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.core.server.ModSettings;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.Platform;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class NeoForgePlatform implements Platform {
    // read by the mod itself: NeoForge sends its own server configs to clients, and this one holds keys
    private final ModSettings settings = new FileSettings(configDir().resolve("server.toml"), InGameMessenger.LOG::warn);

    @Override
    public String loader() {
        return "NeoForge " + ModList.get().getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    @Override
    public String modVersion() {
        return ModList.get().getModContainerById(InGameMessenger.MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
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
        return ModList.get().isLoaded(modId);
    }

    @Override
    public List<String> mods() {
        List<String> out = new ArrayList<>();
        for (var m : ModList.get().getMods()) {
            if (!m.getModId().equals("minecraft") && !m.getModId().equals("neoforge")) {
                out.add(m.getDisplayName());
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
        return ServerLifecycleHooks.getCurrentServer();
    }

    @Override
    public void sendToServer(Packet packet) {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        var type = Net.type(packet.type());
        if (!connection.hasChannel(type)) {
            if (!Net.serverHasMod.getAsBoolean()) {
                return;
            }
            NetworkRegistry.onMinecraftRegister(connection.getConnection(), Set.of(type.id()));
        }
        //? if >=1.21.6 {
        /*net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new Net.Payload(packet));
        *///?} else {
        PacketDistributor.sendToServer(new Net.Payload(packet));
        //?}
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Packet packet) {
        var type = Net.type(packet.type());
        if (!player.connection.hasChannel(type)) {
            if (!InGameMessenger.hasClient(player)) {
                return;
            }
            NetworkRegistry.onMinecraftRegister(player.connection.getConnection(), Set.of(type.id()));
        }
        PacketDistributor.sendToPlayer(player, new Net.Payload(packet));
    }
}
