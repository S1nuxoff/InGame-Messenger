package com.sinuxoff.ingamemessenger.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
//? if >=1.20.2 {
import net.minecraftforge.event.network.ChannelRegistrationChangeEvent;
//?}
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.Platform;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;

/**
 * Forge entry point: registers the packets and forwards server events to the shared code. Forge 1.21.6 replaced its
 * event bus (EventBus 7: a static BUS per event), and 1.21.9 turned many events into records.
 */
@Mod(InGameMessenger.MOD_ID)
public final class ForgeMod {
    public ForgeMod(FMLJavaModLoadingContext context) {
        Platform.set(new ForgePlatform());
        ForgeNet.register();
        //? if >=1.21.9 {
        /*RegisterCommandsEvent.BUS.addListener(e -> InGameMessenger.registerCommands(e.getDispatcher()));
        ServerStartedEvent.BUS.addListener(e -> InGameMessenger.serverStarted(e.getServer()));
        ServerStoppedEvent.BUS.addListener(e -> InGameMessenger.serverStopped());
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> InGameMessenger.serverTick(e.server()));
        ServerChatEvent.BUS.addListener(net.minecraftforge.eventbus.api.listener.Priority.LOWEST,
                (java.util.function.Consumer<ServerChatEvent>) e -> InGameMessenger.chatLine(e.getPlayer(), e.getMessage()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                InGameMessenger.playerJoined(p);
            }
        });
        ChannelRegistrationChangeEvent.BUS.addListener(ForgeMod::channelsChanged);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                InGameMessenger.playerLeft(p);
            }
        });
        *///?} else if >=1.21.6 {
        /*RegisterCommandsEvent.BUS.addListener(e -> InGameMessenger.registerCommands(e.getDispatcher()));
        ServerStartedEvent.BUS.addListener(e -> InGameMessenger.serverStarted(e.getServer()));
        ServerStoppedEvent.BUS.addListener(e -> InGameMessenger.serverStopped());
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> InGameMessenger.serverTick(e.getServer()));
        ServerChatEvent.BUS.addListener(net.minecraftforge.eventbus.api.listener.Priority.LOWEST,
                (java.util.function.Consumer<ServerChatEvent>) e -> InGameMessenger.chatLine(e.getPlayer(), e.getMessage()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                InGameMessenger.playerJoined(p);
            }
        });
        ChannelRegistrationChangeEvent.BUS.addListener(ForgeMod::channelsChanged);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                InGameMessenger.playerLeft(p);
            }
        });
        *///?} else {
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addListener((RegisterCommandsEvent e) -> InGameMessenger.registerCommands(e.getDispatcher()));
        bus.addListener((ServerStartedEvent e) -> InGameMessenger.serverStarted(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> InGameMessenger.serverStopped());
        //? if >=1.20.2 {
        bus.addListener((TickEvent.ServerTickEvent.Post e) -> InGameMessenger.serverTick(e.getServer()));
        //?} else {
        /*bus.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                InGameMessenger.serverTick(e.getServer());
            }
        });
        *///?}
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, false,
                (ServerChatEvent e) -> InGameMessenger.chatLine(e.getPlayer(), e.getMessage()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                InGameMessenger.playerJoined(p);
            }
        });
        //? if >=1.20.2 {
        bus.addListener(ForgeMod::channelsChanged);
        //?}
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                InGameMessenger.playerLeft(p);
            }
        });
        //?}
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init(context);
        }
    }

    //? if >=1.20.2 {
    /** Fabric clients list their play channels only after joining, too late for the first Welcome. */
    private static void channelsChanged(ChannelRegistrationChangeEvent e) {
        var server = Platform.get().server();
        if (server == null || e.getType() != ChannelRegistrationChangeEvent.Type.REGISTER
                || !e.getChannels().contains(Net.id(Packets.Welcome.TYPE))) {
            return;
        }
        server.execute(() -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.connection.getConnection() == e.getSource()) {
                    InGameMessenger.channelsArrived(p);
                }
            }
        });
    }
    //?}
}
