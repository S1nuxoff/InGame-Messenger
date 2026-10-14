package com.sinuxoff.ingamemessenger;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;
import com.sinuxoff.ingamemessenger.core.server.ChatServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * /messenger test (dev only): a scripted conversation between eight fake players with replies, reactions, mentions
 * and stickers, for checking the chat's look alone. Nothing is stored in the history.
 */
final class TestChat {
    static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "InGameMessenger test chat");
        t.setDaemon(true);
        return t;
    });

    private TestChat() {
    }

    /** text null = a sticker; react = label of the message to react to; answers = label of the message replied to. */
    private record Step(long afterMs, String who, String text, String label, String react, int emoji, String answers) {
        static Step say(long after, String who, String text, String label) {
            return new Step(after, who, text, label, null, 0, null);
        }

        static Step reply(long after, String who, String text, String label, String answers) {
            return new Step(after, who, text, label, null, 0, answers);
        }

        static Step sticker(long after, String who, String label) {
            return new Step(after, who, null, label, null, 0, null);
        }

        static Step react(long after, String who, String on, int emoji) {
            return new Step(after, who, null, null, on, emoji, null);
        }
    }

    /** {me} in a text is replaced with the name of the player who started it. */
    static void start(MinecraftServer server, String me) {
        List<String[]> stickers = randomStickers(8);
        List<Step> script = List.of(
                Step.say(0, "Steve", "Hey everyone! Who's online?", "hi"),
                Step.reply(1300, "Alex", "Me, mining diamonds at -59", null, "hi"),
                Step.say(1100, "Kirill_228", "Oh, Steve finally woke up", null),
                Step.react(500, "Alex", "hi", 0),
                Step.say(1200, "Lizka", "Hi all! @{me} are you here too?", "liz1"),
                Step.say(1400, "Steve", "@Alex how many diamonds so far?", "q_dia"),
                Step.reply(1300, "Alex", "12 so far, there's lava right next to me, scary", "dia", "q_dia"),
                Step.react(450, "Masha", "dia", 4),
                Step.react(350, "Steve", "dia", 3),
                Step.say(1200, "Masha", "Share the coords, I want some too", null),
                Step.say(1300, "Vovan_PRO", "Alex, send them in DM or everyone will come running", "vov1"),
                Step.react(400, "Kirill_228", "vov1", 2),
                Step.say(1500, "Dimon", "Guys, I died in the Nether again...", "death"),
                Step.react(450, "Kirill_228", "death", 7),
                Step.react(300, "Steve", "death", 7),
                Step.react(300, "Masha", "death", 5),
                Step.react(300, "Artem4ik", "death", 7),
                Step.sticker(1000, "Dimon", null),
                Step.reply(1300, "Kirill_228", "@Dimon how many times is that today?", "kq", "death"),
                Step.reply(1500, "Dimon", "Fifth. My stuff is next to the portal, anyone coming with me? Ghasts and a ton of wither "
                        + "skeletons there, no chance alone", "items", "kq"),
                Step.react(500, "Alex", "items", 2),
                Step.say(1300, "Dimon", "@{me} help me out, you've got netherite armor", "help"),
                Step.reply(1300, "Steve", "I'll go, just let me finish my armor", "steve_go", "help"),
                Step.react(450, "Dimon", "steve_go", 1),
                Step.say(1100, "Artem4ik", "I'll stand at the portal and provide moral support", "art1"),
                Step.react(400, "Lizka", "art1", 2),
                Step.react(300, "Vovan_PRO", "art1", 2),
                Step.sticker(900, "Alex", "alexst"),
                Step.react(500, "Kirill_228", "alexst", 2),
                Step.say(1300, "Masha", "Who built that thing at spawn? It's awesome", "spawn"),
                Step.react(450, "Steve", "spawn", 0),
                Step.reply(1100, "Kirill_228", "Me, not done yet. @Lizka you promised to help with the roof", "kir_build", "spawn"),
                Step.react(400, "Masha", "kir_build", 1),
                Step.react(300, "Alex", "kir_build", 3),
                Step.reply(1200, "Lizka", "@Kirill_228 I remember, after dinner", null, "kir_build"),
                Step.say(1300, "Vovan_PRO", "@{me} why so quiet? How do you like the spawn?", "vov_me"),
                Step.say(1500, "Artem4ik", "Has anyone seen my horse? White, named Dumpling", "horse"),
                Step.react(400, "Dimon", "horse", 2),
                Step.react(300, "Lizka", "horse", 2),
                Step.react(300, "Steve", "horse", 7),
                Step.reply(1200, "Masha", "@Artem4ik it's in my pen, come get it", null, "horse"),
                Step.sticker(1100, "Artem4ik", null),
                Step.say(1300, "Dimon", "@Steve you still owe me 5 diamonds, remember?", "debt"),
                Step.react(450, "Steve", "debt", 6),
                Step.react(300, "Kirill_228", "debt", 2),
                Step.reply(1100, "Steve", "Don't remember that", null, "debt"),
                Step.reply(1200, "Alex", "@Dimon I can give you 3, you'll work off the rest in the mine", "alexdeal", "debt"),
                Step.react(400, "Dimon", "alexdeal", 1),
                Step.say(1300, "Vovan_PRO", "OK, who's going to the End on Saturday? @Masha @Kirill_228 @{me}", "end"),
                Step.react(450, "Masha", "end", 3),
                Step.react(300, "Kirill_228", "end", 3),
                Step.reply(1100, "Lizka", "Me too! Just give me a decent bow", null, "end"),
                Step.say(1200, "Steve", "Alright, off to the Nether, @Dimon wait at the portal", "go"),
                Step.react(400, "Dimon", "go", 0),
                Step.react(300, "Alex", "go", 0),
                Step.react(300, "Artem4ik", "go", 1),
                Step.sticker(900, "Steve", null),
                Step.sticker(1100, "Masha", "mashast"),
                Step.react(450, "Lizka", "mashast", 1),
                Step.say(1200, "Lizka", "@{me} I'm at the base if you need anything", null));
        Map<String, Integer> ids = new ConcurrentHashMap<>();
        int[] next = {0};
        long at = 0;
        for (Step s : script) {
            at += s.afterMs();
            TIMER.schedule(() -> server.execute(() -> {
                ChatServer chat = ServerSide.chat;
                if (chat == null) {
                    return;
                }
                UUID id = UUIDUtil.createOfflinePlayerUUID(s.who());
                if (s.react() != null) {
                    Integer on = ids.get(s.react());
                    if (on != null) {
                        chat.quietly(() -> chat.reactAs(id, s.who(), on, String.valueOf(s.emoji())));
                    }
                } else if (s.text() != null) {
                    String text = s.text().replace("{me}", me);
                    Integer answered = s.answers() == null ? null : ids.get(s.answers());
                    if (answered != null) {
                        text = "↩" + answered + " " + text;
                    }
                    int mid = chat.tag(id, Protocol.print(text), null);
                    if (s.label() != null) {
                        ids.put(s.label(), mid);
                    }
                    Component name = Component.literal(s.who()).withStyle(st -> st.withHoverEvent(
                            ServerCompat.showEntity(net.minecraft.world.entity.EntityTypes.PLAYER, id, Component.literal(s.who()))));
                    String said = text;
                    chat.quietly(() -> server.getPlayerList().broadcastSystemMessage(
                            Component.translatable("chat.type.text", name, Component.literal(said)), false));
                } else if (!stickers.isEmpty()) {
                    String[] st = stickers.get(next[0]++ % stickers.size());
                    int mid = chat.tag(id, Protocol.print(st[0] + "/" + st[1]), null);
                    if (s.label() != null) {
                        ids.put(s.label(), mid);
                    }
                    Net.toAll(new Packets.Show(id, s.who(), st[0], st[1], -1));
                }
            }), at, TimeUnit.MILLISECONDS);
        }
    }

    /** Random (pack, id) pairs from the server's sticker packs. */
    private static List<String[]> randomStickers(int n) {
        ChatServer chat = ServerSide.chat;
        List<String[]> all = chat == null ? List.of() : chat.stickers().allStickers();
        Random r = new Random();
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < n && !all.isEmpty(); i++) {
            out.add(all.get(r.nextInt(all.size())));
        }
        return out;
    }
}
