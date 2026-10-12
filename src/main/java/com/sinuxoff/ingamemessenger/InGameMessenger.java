package com.sinuxoff.ingamemessenger;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sinuxoff.ingamemessenger.core.protocol.Packet;
import com.sinuxoff.ingamemessenger.core.protocol.Packets.*;
import com.sinuxoff.ingamemessenger.core.server.ChatServer;

import java.util.function.Consumer;

/**
 * Shared entry point. Loaders forward their events here (server start/stop/tick, players joining, leaving and
 * chatting, system messages, commands, packets); the client side registers its packet handlers below.
 */
public final class InGameMessenger {
    public static final String MOD_ID = "ingamemessenger";
    public static final Logger LOG = LoggerFactory.getLogger("InGameMessenger");

    private InGameMessenger() {
    }

    // Server

    public static void serverStarted(MinecraftServer server) {
        ServerSide.start(server);
    }

    public static void serverStopped() {
        ServerSide.stop();
    }

    public static void serverTick(MinecraftServer server) {
        ChatServer chat = ServerSide.chat;
        if (chat != null && server.getTickCount() % 200 == 0) {
            chat.tick();
        }
    }

    /** A chat line about to be broadcast: its id goes out first, on the same connections. */
    public static void chatLine(ServerPlayer player, Component message) {
        ChatServer chat = ServerSide.chat;
        if (chat != null) {
            String name = player.getGameProfile().getName();
            int id = chat.chatLine(player.getUUID(), name, message.getString(), ServerSide.json(Platform.get().server(), message));
            FakeBots.heard(Platform.get().server(), player, message.getString(), id);
        }
    }

    public static void playerJoined(ServerPlayer player) {
        ChatServer chat = ServerSide.chat;
        if (chat != null) {
            chat.joined(player.getUUID());
            FakeBots.joined(player);
        }
    }

    public static void playerLeft(ServerPlayer player) {
        ChatServer chat = ServerSide.chat;
        if (chat != null) {
            chat.left(player.getUUID(), player.getGameProfile().getName());
        }
    }

    /** A system message about to be broadcast: gets an id and goes into the history. */
    public static void systemMessage(Component message) {
        ChatServer chat = ServerSide.chat;
        MinecraftServer server = Platform.get().server();
        if (chat != null && server != null) {
            chat.systemMessage(ServerSide.json(server, message));
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        MessengerCommands.register(dispatcher);
    }

    public static void toServer(ServerPlayer player, Packet packet) {
        ChatServer chat = ServerSide.chat;
        if (chat != null) {
            chat.handle(player.getUUID(), packet);
        }
    }

    // Client packet handlers (no-ops on a dedicated server)

    public static volatile Consumer<StickerIndex> onStickerIndex = h -> {
    };
    public static volatile Consumer<StickerData> onStickerData = h -> {
    };
    public static volatile Consumer<Show> onShow = s -> {
    };
    public static volatile Consumer<Tag> onTag = t -> {
    };
    public static volatile Consumer<Reactions> onReactions = r -> {
    };
    public static volatile Consumer<HistoryPage> onHistory = h -> {
    };
    public static volatile Consumer<TypingState> onTypingState = t -> {
    };
    public static volatile Consumer<Features> onFeatures = f -> {
    };
    public static volatile Consumer<VoiceShow> onVoiceShow = v -> {
    };
    public static volatile Consumer<Edited> onEdited = v -> {
    };
    public static volatile Consumer<Deleted> onDeleted = v -> {
    };
    public static volatile Consumer<VoiceData> onVoiceData = v -> {
    };
    public static volatile Consumer<ImageShow> onImageShow = v -> {
    };
    public static volatile Consumer<SearchRes> onSearch = v -> {
    };
    public static volatile Consumer<PollShow> onPollShow = v -> {
    };
    public static volatile Consumer<ReadState> onReadState = v -> {
    };
    public static volatile Consumer<DmShow> onDmShow = v -> {
    };
    public static volatile Consumer<Route> onRoute = v -> {
    };
    public static volatile Consumer<DmHistory> onDmHistory = v -> {
    };
    public static volatile Consumer<DmReadState> onDmReadState = v -> {
    };
    public static volatile Consumer<DmChats> onDmChats = v -> {
    };
    public static volatile Consumer<ReadAll> onReadAll = v -> {
    };
    public static volatile Consumer<PollState> onPollState = v -> {
    };
    public static volatile Consumer<ImageData> onImageData = v -> {
    };

    public static void toClient(Packet packet) {
        switch (packet) {
            case StickerIndex p -> onStickerIndex.accept(p);
            case StickerData p -> onStickerData.accept(p);
            case Show p -> onShow.accept(p);
            case Tag p -> onTag.accept(p);
            case Reactions p -> onReactions.accept(p);
            case HistoryPage p -> onHistory.accept(p);
            case TypingState p -> onTypingState.accept(p);
            case Features p -> onFeatures.accept(p);
            case VoiceShow p -> onVoiceShow.accept(p);
            case Edited p -> onEdited.accept(p);
            case Deleted p -> onDeleted.accept(p);
            case VoiceData p -> onVoiceData.accept(p);
            case ImageShow p -> onImageShow.accept(p);
            case ImageData p -> onImageData.accept(p);
            case SearchRes p -> onSearch.accept(p);
            case PollShow p -> onPollShow.accept(p);
            case ReadState p -> onReadState.accept(p);
            case DmShow p -> onDmShow.accept(p);
            case Route p -> onRoute.accept(p);
            case DmHistory p -> onDmHistory.accept(p);
            case DmReadState p -> onDmReadState.accept(p);
            case DmChats p -> onDmChats.accept(p);
            case ReadAll p -> onReadAll.accept(p);
            case PollState p -> onPollState.accept(p);
            default -> LOG.warn("Unexpected packet for the client: {}", packet.type().id());
        }
    }
}
