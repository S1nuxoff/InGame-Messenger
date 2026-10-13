package com.sinuxoff.ingamemessenger;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
//? if >=1.19.3 {
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
//?}
//? if >=1.19.3 {
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
//?}
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.core.server.ChatServer;
//? if >=1.19.3 {
import com.sinuxoff.ingamemessenger.mixin.PlayerInfoPacketAccessor;
//?}

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /messenger bots (dev only): fake players in the tab list. A bot answers when mentioned and reacts to the message.
 * They never really join, and nothing they say is stored.
 */
final class FakeBots {
    static final List<String> NAMES = List.of("Steve", "Alex", "Kirill_228", "Masha", "Dimon", "Lizka", "Vovan_PRO", "Artem4ik");
    private static final Pattern AT = Pattern.compile("@([A-Za-z0-9_]{1,16})");
    private static final String[] ANSWERS = {
            "I'm here!", "What?", "@{p} yes, listening", "OK, one sec", "Lol", "@{p} coming!", "Busy mining, 5 minutes",
            "@{p} nah, I'll pass", "Who called?", "@{p} how about you?", "Hahaha", "I'm at the base, come over", "Don't distract me, building",
            "@{p} let's go to voice", "Okay", "@{p} on my way", "Me again?", "Could really use some diamonds...", "@{p} thanks!",
            "Has anyone seen my pickaxe?", "@{p} I'm in the Nether, it's hot here", "Hmm, interesting", "@{p} wow, you're something"};
    private static final String[] REACTIONS = {"1", "0", "2", "3", "1", "0"};
    /** Used when a message mentions no bot: one of them answers anyway. */
    private static final String[] CHAT = {
            "Agreed", "Haha, so true", "That's the basics", "Tell me more?", "Sounds like a plan", "I wouldn't do that",
            "Whoa", "Lol, seriously?", "+1", "Where is that?", "Send the coords", "I want that too", "Hmm, let me think",
            "Wow", "Nice one", "Let's go together", "Told you", "No way", "Noted", "LOL"};
    private static final Map<String, GameProfile> bots = new LinkedHashMap<>();
    private static final Random RND = new Random();

    private FakeBots() {
    }

    static boolean any() {
        return !bots.isEmpty();
    }

    static java.util.Collection<GameProfile> profiles() {
        return List.copyOf(bots.values());
    }

    /** A random line, for private chats. */
    static String chatLine() {
        return CHAT[RND.nextInt(CHAT.length)];
    }

    static boolean isBot(UUID id) {
        return nameOf(id) != null;
    }

    /** Null if not a bot. */
    static String nameOf(UUID id) {
        for (GameProfile p : bots.values()) {
            if (ServerCompat.id(p).equals(id)) {
                return ServerCompat.name(p);
            }
        }
        return null;
    }

    /** A real player's public message: the bots "read" it a few seconds later. A private message to a bot gets an answer. */
    static final ChatServer.Listener LISTENER = new ChatServer.Listener() {
        @Override
        public void publicMessage(int id, UUID sender) {
            ChatServer chat = ServerSide.chat;
            MinecraftServer server = Platform.get().server();
            if (bots.isEmpty() || isBot(sender) || chat == null || server == null) {
                return;
            }
            for (GameProfile p : profiles()) {
                TestChat.TIMER.schedule(() -> server.execute(() -> chat.markRead(ServerCompat.id(p), ServerCompat.name(p), id)),
                        1000 + (long) (Math.random() * 6000), TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void privateMessage(UUID from, String fromName, UUID to, String toName, String text) {
            ChatServer chat = ServerSide.chat;
            MinecraftServer server = Platform.get().server();
            if (!isBot(to) || isBot(from) || chat == null || server == null) {
                return;
            }
            TestChat.TIMER.schedule(() -> server.execute(() -> {
                chat.markDmRead(to, from, Integer.MAX_VALUE);
                chat.privateMessage(to, toName, from, fromName, chatLine());
            }), 1500 + (long) (Math.random() * 2500), TimeUnit.MILLISECONDS);
        }
    };

    //? if >=1.19.3 {
    private static ClientboundPlayerInfoUpdatePacket addPacket() {
        var pk = new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE), List.of());
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries = new ArrayList<>();
        for (GameProfile p : bots.values()) {
            //? if >=1.21.4 {
            /*entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(ServerCompat.id(p), p, true, 20 + RND.nextInt(60), GameType.SURVIVAL, null, true, 0, null));
            *///?} else {
            entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(ServerCompat.id(p), p, true, 20 + RND.nextInt(60), GameType.SURVIVAL, null, null));
            //?}
        }
        ((PlayerInfoPacketAccessor) pk).ingamemessenger$setEntries(entries);
        return pk;
    }
    //?}

    static int add(MinecraftServer server) {
        for (String n : NAMES) {
            bots.putIfAbsent(n, new GameProfile(UUIDUtil.createOfflinePlayerUUID(n), n));
        }
        //? if >=1.19.3 {
        server.getPlayerList().broadcastAll(addPacket());
        //?}
        return bots.size();
    }

    static void remove(MinecraftServer server) {
        if (bots.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        bots.values().forEach(p -> ids.add(ServerCompat.id(p)));
        bots.clear();
        //? if >=1.19.3 {
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(ids));
        //?}
    }

    /** New players see the bots too. */
    static void joined(ServerPlayer p) {
        //? if >=1.19.3 {
        if (!bots.isEmpty()) {
            p.connection.send(addPacket());
        }
        //?}
    }

    private static ChatServer chat() {
        return ServerSide.chat;
    }

    /** Each bot mentioned in the message answers and reacts. */
    static void heard(MinecraftServer server, ServerPlayer from, String text, int id) {
        if (bots.isEmpty() || chat() == null) {
            return;
        }
        Matcher m = AT.matcher(text);
        long delay = 0;
        List<String> done = new ArrayList<>();
        while (m.find()) {
            String who = null;
            for (String n : bots.keySet()) {
                if (n.equalsIgnoreCase(m.group(1))) {
                    who = n;
                }
            }
            if (who == null || done.contains(who)) {
                continue;
            }
            done.add(who);
            String bot = who, player = from.getGameProfile().getName();
            UUID bid = ServerCompat.id(bots.get(bot));
            String reaction = REACTIONS[RND.nextInt(REACTIONS.length)];
            delay += 700 + RND.nextInt(900);
            TestChat.TIMER.schedule(() -> server.execute(() -> chat().quietly(() -> chat().reactAs(bid, bot, id, reaction))),
                    delay, TimeUnit.MILLISECONDS);
            TestChat.TIMER.schedule(() -> server.execute(() -> chat().typing(bid, bot, true)),
                    delay + 200, TimeUnit.MILLISECONDS);
            delay += 1500 + RND.nextInt(1500);
            String answer = "↩" + id + " " + ANSWERS[RND.nextInt(ANSWERS.length)].replace("{p}", player);
            TestChat.TIMER.schedule(() -> server.execute(() -> {
                chat().typing(bid, bot, false);
                say(server, bot, bid, answer);
            }), delay, TimeUnit.MILLISECONDS);
        }
        if (done.isEmpty() && !text.startsWith("/")) { // nobody mentioned: a random bot replies
            List<String> names = new ArrayList<>(bots.keySet());
            String bot = names.get(RND.nextInt(names.size()));
            UUID bid = ServerCompat.id(bots.get(bot));
            long d = 600 + RND.nextInt(900);
            TestChat.TIMER.schedule(() -> server.execute(() -> chat().typing(bid, bot, true)), d, TimeUnit.MILLISECONDS);
            String answer = "\u21A9" + id + " " + CHAT[RND.nextInt(CHAT.length)];
            TestChat.TIMER.schedule(() -> server.execute(() -> {
                chat().typing(bid, bot, false);
                say(server, bot, bid, answer);
            }), d + 1600 + RND.nextInt(1400), TimeUnit.MILLISECONDS);
        }
    }

    private static void say(MinecraftServer server, String bot, UUID bid, String text) {
        chat().tag(bid, Protocol.print(text), null);
        Component name = Component.literal(bot).withStyle(st -> st.withHoverEvent(
                ServerCompat.showEntity(EntityType.PLAYER, bid, Component.literal(bot))));
        chat().quietly(() -> server.getPlayerList().broadcastSystemMessage(
                Component.translatable("chat.type.text", name, Component.literal(text)), false));
    }
}
