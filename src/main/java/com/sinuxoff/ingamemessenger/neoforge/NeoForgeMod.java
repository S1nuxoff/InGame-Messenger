package com.sinuxoff.ingamemessenger.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.Platform;
import com.sinuxoff.ingamemessenger.core.protocol.PacketType;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;

/** NeoForge entry point: registers the packets and forwards server events to the shared code. */
@Mod(InGameMessenger.MOD_ID)
public final class NeoForgeMod {
    public NeoForgeMod(IEventBus modBus, ModContainer container) {
        Platform.set(new NeoForgePlatform());
        modBus.addListener(NeoForgeMod::registerPayloads);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> InGameMessenger.registerCommands(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> InGameMessenger.serverStarted(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> InGameMessenger.serverStopped());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> InGameMessenger.serverTick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (ServerChatEvent e) -> InGameMessenger.chatLine(e.getPlayer(), e.getMessage()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                InGameMessenger.playerJoined(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                InGameMessenger.playerLeft(p);
            }
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(Protocol.VERSION);
        for (PacketType<?> t : Packets.ALL) {
            if (t.direction() == PacketType.Direction.TO_CLIENT) {
                r.playToClient(Net.type(t), Net.codec(t), (p, ctx) -> InGameMessenger.toClient(p.packet()));
            } else {
                r.playToServer(Net.type(t), Net.codec(t), (p, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        InGameMessenger.toServer(player, p.packet());
                    }
                });
            }
        }
    }
}
