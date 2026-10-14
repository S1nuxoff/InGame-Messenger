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

    /** A resource location in the mod's namespace. */
    public static net.minecraft.resources.ResourceLocation id(String path) {
        //? if >=1.21 {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        //?} else {
        /*return new net.minecraft.resources.ResourceLocation(MOD_ID, path);
        *///?}
    }

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

    /** False (and the player is told) while they're muted in the chat: the line must not go out. */
    public static boolean mayChat(ServerPlayer player) {
        ChatServer chat = ServerSide.chat;
        return chat == null || chat.mutes().mayChat(player.getUUID());
    }

    /** False (and the player is told) if the command writes where they're muted (/msg, /me...). */
    public static boolean mayCommand(ServerPlayer player, String command) {
        ChatServer chat = ServerSide.chat;
        return chat == null || chat.mutes().mayCommand(player.getUUID(), command);
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

    /** The client listed our channels after joining: Welcome again, it couldn't get the first one. */
    public static void channelsArrived(ServerPlayer player) {
        ChatServer chat = ServerSide.chat;
        if (chat != null && !chat.hasClient(player.getUUID())) {
            chat.joined(player.getUUID());
        }
    }

    /** Whether the player's client said Hello with our protocol, so it has our channels whatever its loader reported. */
    public static boolean hasClient(ServerPlayer player) {
        ChatServer chat = ServerSide.chat;
        return chat != null && chat.hasClient(player.getUUID());
    }

    public static void toServer(ServerPlayer player, Packet packet) {
        ChatServer chat = ServerSide.chat;
        if (chat != null) {
            chat.handle(player.getUUID(), packet);
        }
    }

    // Client packet handlers (no-ops on a dedicated server)

    public static volatile Consumer<Welcome> onWelcome = w -> {
    };
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
    public static volatile Consumer<MuteState> onMuteState = v -> {
    };
    public static volatile Consumer<Names> onNames = v -> {
    };

    public static void toClient(Packet packet) {
        if (packet instanceof Welcome p) {
            onWelcome.accept(p);
        } else if (packet instanceof StickerIndex p) {
            onStickerIndex.accept(p);
        } else if (packet instanceof StickerData p) {
            onStickerData.accept(p);
        } else if (packet instanceof Show p) {
            onShow.accept(p);
        } else if (packet instanceof Tag p) {
            onTag.accept(p);
        } else if (packet instanceof Reactions p) {
            onReactions.accept(p);
        } else if (packet instanceof HistoryPage p) {
            onHistory.accept(p);
        } else if (packet instanceof TypingState p) {
            onTypingState.accept(p);
        } else if (packet instanceof Features p) {
            onFeatures.accept(p);
        } else if (packet instanceof VoiceShow p) {
            onVoiceShow.accept(p);
        } else if (packet instanceof Edited p) {
            onEdited.accept(p);
        } else if (packet instanceof Deleted p) {
            onDeleted.accept(p);
        } else if (packet instanceof VoiceData p) {
            onVoiceData.accept(p);
        } else if (packet instanceof ImageShow p) {
            onImageShow.accept(p);
        } else if (packet instanceof ImageData p) {
            onImageData.accept(p);
        } else if (packet instanceof SearchRes p) {
            onSearch.accept(p);
        } else if (packet instanceof PollShow p) {
            onPollShow.accept(p);
        } else if (packet instanceof ReadState p) {
            onReadState.accept(p);
        } else if (packet instanceof DmShow p) {
            onDmShow.accept(p);
        } else if (packet instanceof Route p) {
            onRoute.accept(p);
        } else if (packet instanceof DmHistory p) {
            onDmHistory.accept(p);
        } else if (packet instanceof DmReadState p) {
            onDmReadState.accept(p);
        } else if (packet instanceof DmChats p) {
            onDmChats.accept(p);
        } else if (packet instanceof ReadAll p) {
            onReadAll.accept(p);
        } else if (packet instanceof PollState p) {
            onPollState.accept(p);
        } else if (packet instanceof MuteState p) {
            onMuteState.accept(p);
        } else if (packet instanceof Names p) {
            onNames.accept(p);
        } else {
            LOG.warn("Unexpected packet for the client: {}", packet.type().id());
        }
    }
}
