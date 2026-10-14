package com.sinuxoff.ingamemessenger.forge;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.20.2 {
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.PacketDistributor;
//?} else {
/*import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
*///?}
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
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
        // optional, with a fixed version: players join whatever the other side has, Welcome sorts out the rest
        for (PacketType<?> t : com.sinuxoff.ingamemessenger.core.protocol.Protocol.ALL) {
            //? if >=1.20.2 {
            EventNetworkChannel ch = ChannelBuilder.named(InGameMessenger.id(t.id()))
                    .networkProtocolVersion(1)
                    .optional()
                    .eventNetworkChannel();
            ch.addListener(e -> {
                var ctx = e.getSource();
            //?} else {
            /*EventNetworkChannel ch = NetworkRegistry.ChannelBuilder.named(InGameMessenger.id(t.id()))
                    .networkProtocolVersion(() -> "1")
                    .clientAcceptedVersions(NetworkRegistry.acceptMissingOr("1"))
                    .serverAcceptedVersions(NetworkRegistry.acceptMissingOr("1"))
                    .eventNetworkChannel();
            ch.addListener((NetworkEvent e) -> {
                var ctx = e.getSource().get();
                if (e instanceof NetworkEvent.ChannelRegistrationChangeEvent reg) {
                    // Fabric clients list their play channels only after joining, too late for the first Welcome
                    if (t == Packets.Welcome.TYPE && reg.getRegistrationChangeType() == NetworkEvent.RegistrationChangeType.REGISTER
                            && ctx.getDirection().getReceptionSide().isServer()) {
                        var connection = ctx.getNetworkManager();
                        ctx.enqueueWork(() -> {
                            var server = com.sinuxoff.ingamemessenger.Platform.get().server();
                            for (ServerPlayer player : server == null ? java.util.List.<ServerPlayer>of() : server.getPlayerList().getPlayers()) {
                                if (player.connection.connection == connection) {
                                    InGameMessenger.channelsArrived(player);
                                }
                            }
                        });
                    }
                    return;
                }
            *///?}
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
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        EventNetworkChannel ch = CHANNELS.get(p.type());
        if (connection != null && (ch.isRemotePresent(connection.getConnection()) || Net.serverHasMod.getAsBoolean())) {
            //? if >=1.20.2 {
            ch.send(buf(p), PacketDistributor.SERVER.noArg());
            //?} else {
            /*connection.send(new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(Net.id(p.type()), buf(p)));
            *///?}
        }
    }

    static void toPlayer(ServerPlayer player, Packet p) {
        EventNetworkChannel ch = CHANNELS.get(p.type());
        //? if >=1.20.2 {
        if (ch.isRemotePresent(player.connection.getConnection()) || InGameMessenger.hasClient(player)) {
            ch.send(buf(p), PacketDistributor.PLAYER.with(player));
        }
        //?} else {
        /*if (ch.isRemotePresent(player.connection.connection) || InGameMessenger.hasClient(player)) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket(Net.id(p.type()), buf(p)));
        }
        *///?}
    }
}
