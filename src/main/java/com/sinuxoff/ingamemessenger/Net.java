package com.sinuxoff.ingamemessenger;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.PacketType;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;

import java.util.HashMap;
import java.util.Map;

/** Core packets as Minecraft custom payloads, one channel per packet type ("ingamemessenger:id"). */
public final class Net {
    public record Payload(Packet packet) implements CustomPacketPayload {
        @Override
        public Type<Payload> type() {
            return TYPES.get(packet.type());
        }
    }

    private static final Map<PacketType<?>, CustomPacketPayload.Type<Payload>> TYPES = new HashMap<>();

    static {
        for (PacketType<?> t : Packets.ALL) {
            TYPES.put(t, new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Protocol.NAMESPACE, t.id())));
        }
    }

    private Net() {
    }

    public static CustomPacketPayload.Type<Payload> type(PacketType<?> t) {
        return TYPES.get(t);
    }

    /** Passes the core's bytes through unchanged. */
    public static StreamCodec<ByteBuf, Payload> codec(PacketType<?> t) {
        return StreamCodec.of((buf, p) -> buf.writeBytes(Protocol.encode(p.packet())), buf -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new Payload(Protocol.decode(t, data));
        });
    }

    public static void toServer(Packet p) {
        Platform.get().sendToServer(p);
    }

    public static void toPlayer(ServerPlayer player, Packet p) {
        Platform.get().sendToPlayer(player, p);
    }

    public static void toAll(Packet p) {
        MinecraftServer server = Platform.get().server();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                toPlayer(player, p);
            }
        }
    }
}
