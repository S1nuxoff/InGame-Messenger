package com.sinuxoff.ingamemessenger.forge;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.PacketDistributor;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.PacketType;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;

import java.util.HashMap;
import java.util.Map;

/** One Forge event channel per packet type ("ingamemessenger:id"), carrying the core's bytes as they are. */
final class ForgeNet {
    private static final Map<PacketType<?>, EventNetworkChannel> CHANNELS = new HashMap<>();

    private ForgeNet() {
    }

    static void register() {
        int version = Integer.parseInt(Protocol.VERSION);
        for (PacketType<?> t : Packets.ALL) {
            EventNetworkChannel ch = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(Protocol.NAMESPACE, t.id()))
                    .networkProtocolVersion(version)
                    .eventNetworkChannel();
            ch.addListener(e -> {
                var ctx = e.getSource();
                byte[] data = bytes(e.getPayload());
                Packet p = Protocol.decode(t, data);
                if (t.direction() == PacketType.Direction.TO_CLIENT) {
                    ctx.enqueueWork(() -> InGameMessenger.toClient(p));
                } else {
                    ServerPlayer player = ctx.getSender();
                    if (player != null) {
                        ctx.enqueueWork(() -> InGameMessenger.toServer(player, p));
                    }
                }
                ctx.setPacketHandled(true);
            });
            CHANNELS.put(t, ch);
        }
    }

    private static byte[] bytes(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        return data;
    }

    private static FriendlyByteBuf buf(Packet p) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(Protocol.encode(p)));
    }

    static void toServer(Packet p) {
        CHANNELS.get(p.type()).send(buf(p), PacketDistributor.SERVER.noArg());
    }

    static void toPlayer(ServerPlayer player, Packet p) {
        CHANNELS.get(p.type()).send(buf(p), PacketDistributor.PLAYER.with(player));
    }
}
