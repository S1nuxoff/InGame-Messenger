package com.sinuxoff.ingamemessenger;

//? if >=1.20.5 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else {
/*import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
*///?}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.PacketType;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Core packets as Minecraft custom payloads, one channel per packet type ("ingamemessenger:id"). The payload carries
 * the core's bytes unchanged.
 */
public final class Net {
    private static final Map<PacketType<?>, ResourceLocation> IDS = new HashMap<>();

    static {
        for (PacketType<?> t : Packets.ALL) {
            IDS.put(t, InGameMessenger.id(t.id()));
        }
    }

    private Net() {
    }

    /** The channel of a packet type. */
    public static ResourceLocation id(PacketType<?> t) {
        return IDS.get(t);
    }

    //? if >=1.20.5 {
    public record Payload(Packet packet) implements CustomPacketPayload {
        @Override
        public Type<Payload> type() {
            return Net.type(packet.type());
        }
    }

    private static final Map<PacketType<?>, CustomPacketPayload.Type<Payload>> TYPES = new HashMap<>();

    static {
        for (PacketType<?> t : Packets.ALL) {
            TYPES.put(t, new CustomPacketPayload.Type<>(IDS.get(t)));
        }
    }

    public static CustomPacketPayload.Type<Payload> type(PacketType<?> t) {
        return TYPES.get(t);
    }

    public static StreamCodec<ByteBuf, Payload> codec(PacketType<?> t) {
        return StreamCodec.of((buf, p) -> buf.writeBytes(Protocol.encode(p.packet())), buf -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new Payload(Protocol.decode(t, data));
        });
    }
    //?} else {
    /*// before 1.20.5 a custom payload is a channel and a buffer

    public static FriendlyByteBuf buf(Packet p) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(Protocol.encode(p)));
    }

    public static Packet read(PacketType<?> t, FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return Protocol.decode(t, data);
    }
    *///?}

    /** Set by the client: whether the server answered with a matching Welcome. */
    public static volatile BooleanSupplier serverReady = () -> false;

    /**
     * Set by the client: whether the server sent Welcome, so it has the mod. Loaders don't always tell each other
     * about their channels; once the server has said Welcome we send to it anyway.
     */
    public static volatile BooleanSupplier serverHasMod = () -> false;

    /** Only Hello goes out before the server is ready. Returns false if the packet was dropped for that. */
    public static boolean toServer(Packet p) {
        if (p instanceof Packets.Hello || serverReady.getAsBoolean()) {
            Platform.get().sendToServer(p);
            return true;
        }
        return false;
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
