package com.sinuxoff.ingamemessenger.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?}
//? if >=26.1 {
/*import net.fabricmc.fabric.api.networking.v1.ClientboundConfigurationChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ClientboundPlayChannelEvents;
*///?} else if >=1.20.2 {
import net.fabricmc.fabric.api.networking.v1.S2CConfigurationChannelEvents;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
*///?}
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.Platform;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.PacketType;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fabric entry point: registers the packets and forwards server events to the shared code. */
public final class FabricMod implements ModInitializer {
    static final FabricPlatform PLATFORM = new FabricPlatform();
    /** Once the client lists this channel, it can get Welcome. */
    private static final ResourceLocation READY = Net.id(Packets.Welcome.TYPE);

    @Override
    public void onInitialize() {
        Platform.set(PLATFORM);
        for (PacketType<?> t : Packets.ALL) {
            //? if <1.20.5 {
            /*if (t.direction() == PacketType.Direction.TO_SERVER) {
                ServerPlayNetworking.registerGlobalReceiver(Net.id(t), (server, player, handler, buf, sender) -> {
                    Packet p = Net.read(t, buf);
                    server.execute(() -> InGameMessenger.toServer(player, p));
                });
            }
            *///?} else {
            if (t.direction() == PacketType.Direction.TO_CLIENT) {
                //? if >=26.1 {
                /*PayloadTypeRegistry.clientboundPlay().register(Net.type(t), Net.codec(t));
                *///?} else {
                PayloadTypeRegistry.playS2C().register(Net.type(t), Net.codec(t));
                //?}
            } else {
                //? if >=26.1 {
                /*PayloadTypeRegistry.serverboundPlay().register(Net.type(t), Net.codec(t));
                *///?} else {
                PayloadTypeRegistry.playC2S().register(Net.type(t), Net.codec(t));
                //?}
                ServerPlayNetworking.registerGlobalReceiver(Net.type(t), (p, ctx) -> InGameMessenger.toServer(ctx.player(), p.packet()));
            }
            //?}
        }
        ServerLifecycleEvents.SERVER_STARTING.register(server -> PLATFORM.server = server);
        ServerLifecycleEvents.SERVER_STARTED.register(InGameMessenger::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            InGameMessenger.serverStopped();
            PLATFORM.server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(InGameMessenger::serverTick);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            InGameMessenger.chatLine(sender, message./*? if >=1.19.3 {*/decoratedContent/*?} else {*//*serverContent*//*?}*/());
            return true;
        });
        // NeoForge and Forge clients list their play channels while still configuring, and Fabric forgets them when
        // play starts
        //? if >=1.20.2 {
        //? if >=26.1 {
        /*ClientboundConfigurationChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
        *///?} else {
        S2CConfigurationChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
        //?}
            if (channels.contains(READY)) {
                early.add(handler.getOwner().getId());
            }
        });
        //?}
        // Packets sent before the client registers its channels are dropped, so the join is handled when it does
        // (or listed them early: on the next tick, once the player is in the list), or after 3 seconds at the latest.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joining.put(handler.getPlayer().getUUID(), server.getTickCount()));
        //? if >=26.1 {
        /*ClientboundPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
        *///?} else {
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
        //?}
            if (channels.contains(READY)) {
                // on the next tick: this can fire while the connection is still being set up
                early.add(handler.getPlayer().getUUID());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> joining.forEach((id, tick) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                joining.remove(id);
            } else if (early.contains(id) || server.getTickCount() - tick > 60 && canSend(player, READY)) {
                greet(player);
            }
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            joining.remove(handler.getPlayer().getUUID());
            greeted.remove(handler.getPlayer().getUUID());
            early.remove(handler.getPlayer().getUUID());
            InGameMessenger.playerLeft(handler.getPlayer());
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> InGameMessenger.registerCommands(dispatcher));
    }

    /** Players whose client listed our channels (while configuring, or before the join was handled). */
    private static final Set<UUID> early = ConcurrentHashMap.newKeySet();

    /** Whether the client can get this packet: Fabric knows it, it listed it early, or it said Hello. */
    static boolean canSend(ServerPlayer player, ResourceLocation channel) {
        return ServerPlayNetworking.canSend(player, channel) || early.contains(player.getUUID()) || InGameMessenger.hasClient(player);
    }

    /** Players whose join was handled. */
    private static final Set<UUID> greeted = ConcurrentHashMap.newKeySet();
    /** Players not handled yet -> tick they joined at. */
    private static final Map<UUID, Integer> joining = new ConcurrentHashMap<>();

    private static void greet(ServerPlayer player) {
        joining.remove(player.getUUID());
        if (greeted.add(player.getUUID())) {
            InGameMessenger.playerJoined(player);
        }
    }
}
