package com.sinuxoff.ingamemessenger.client;

import com.google.gson.JsonObject;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Proto;
import com.sinuxoff.ingamemessenger.mixin.ChatScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A test run that plays the messenger by itself, for an outside test script. Off unless the game was started with
 * -Dingamemessenger.autotest=true (it records the microphone, so a file alone mustn't start it); then it starts when
 * the game folder has ingamemessenger-autotest.json, which it deletes. Each step does what a player would (through the
 * same input), waits for the server to confirm it, and writes the result to ingamemessenger-autotest.log; steps that
 * show something ask the script for a screenshot. At the end the game closes.
 */
final class AutoTest {
    private AutoTest() {
    }

    private record Step(String name, Runnable action, BooleanSupplier done, int timeoutTicks, boolean shot) {
    }

    private static final int SHOT_PAUSE = 40, STEP_PAUSE = 15;
    private static List<Step> steps;
    private static Path log;
    private static int at, ticks, pause, passed, failed;
    private static boolean started, finished;
    private static String token;
    /** What the steps act on: our text message and poll. */
    private static long textId, pollId, sentAt;
    private static Messenger.Entry voice;
    /** The test peer's private chat, our answer there, our reply in the game's chat. */
    private static long dmMsgId, replyId;
    /** The test peer's name: the server's -Dingamemessenger.peer, told in the order file. */
    private static String PEER = "TestBot";
    /** Screenshots for the README instead of the test: a friendly conversation. */
    private static boolean showcase;

    private static void peer(String action) {
        Net.toServer(new Proto.TestPeer(action));
    }

    private static Messenger.Chan dm() {
        for (Messenger.Chan c : Messenger.channels.values()) {
            if (c.dm() && PEER.equals(c.withName)) {
                return c;
            }
        }
        return null;
    }

    /** The peer's message in this channel whose text has this. */
    private static Messenger.Entry fromPeer(Messenger.Chan c, String has) {
        if (c == null) {
            return null;
        }
        for (int i = c.list.size() - 1; i >= 0; i--) {
            Messenger.Entry e = c.list.get(i);
            if (PEER.equals(e.name) && !e.mine() && e.text().contains(has)) {
                return e;
            }
        }
        return null;
    }
    private static String typing;
    private static int typed;

    /** Every client tick. */
    private static final boolean ENABLED = Boolean.getBoolean("ingamemessenger.autotest");

    static void tick() {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!started) {
            Path order = mc.gameDirectory.toPath().resolve("ingamemessenger-autotest.json");
            if (!Files.isRegularFile(order) || mc.player == null) {
                return;
            }
            try {
                JsonObject o = com.google.gson.JsonParser.parseString(Files.readString(order)).getAsJsonObject();
                showcase = o.has("showcase") && o.get("showcase").getAsBoolean();
                if (o.has("peer")) {
                    PEER = o.get("peer").getAsString();
                }
                if (o.has("start")) {
                    List<String> start = new ArrayList<>();
                    o.getAsJsonArray("start").forEach(e -> start.add(e.getAsString()));
                    START = start;
                }
                Files.delete(order);
                log = mc.gameDirectory.toPath().resolve("ingamemessenger-autotest.log");
                Files.writeString(log, "");
            } catch (Exception e) {
                return;
            }
            started = true;
            token = "t" + Long.toString(System.currentTimeMillis() % 1_000_000, 36);
            steps = showcase ? showcase() : scenario();
            write("start", true, "");
        }
        if (finished || steps == null) {
            return;
        }
        if (pause > 0) {
            pause--;
            return;
        }
        if (typing != null) { // types like a player, a few letters a tick
            typed = next(typing, typed);
            if (box != null) {
                box.setValue(typing.substring(0, typed));
                if (typed >= typing.length()) {
                    typing = null;
                    box = null;
                }
                return;
            }
            ChatScreen screen = chat();
            if (screen == null) {
                return;
            }
            var input = ((ChatScreenAccessor) screen).ingamemessenger$input();
            input.setValue(typing.substring(0, typed));
            if (typed >= typing.length()) {
                typing = null;
                ChatInput.sendLine(screen);
            }
            return;
        }
        if (at >= steps.size()) {
            finished = true;
            write("done", failed == 0, passed + " passed, " + failed + " failed");
            pause = 0;
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }
                mc.execute(mc::stop);
            }, "InGameMessenger autotest").start();
            return;
        }
        Step s = steps.get(at);
        if (ticks == 0) {
            try {
                s.action().run();
            } catch (RuntimeException e) {
                result(s, false, "threw " + e);
                return;
            }
        }
        ticks++;
        boolean ok;
        try {
            ok = s.done().getAsBoolean();
        } catch (RuntimeException e) {
            ok = false;
        }
        if (ok) {
            result(s, true, "");
        } else if (ticks > s.timeoutTicks()) {
            result(s, false, "no result in " + s.timeoutTicks() / 20 + " s");
        }
    }

    private static void result(Step s, boolean ok, String note) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        write(s.name(), ok, note);
        pause = showcase ? 22 : STEP_PAUSE; // the showcase is filmed: time to see each step
        if (s.shot()) {
            write("shot", true, s.name());
            pause = SHOT_PAUSE;
        }
        at++;
        ticks = 0;
    }

    private static void write(String step, boolean ok, String note) {
        JsonObject o = new JsonObject();
        o.addProperty("step", step);
        o.addProperty("ok", ok);
        o.addProperty("note", note);
        try {
            Files.writeString(log, o + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (Exception e) {
            InGameMessenger.LOG.warn("autotest: can't write the log: {}", e.toString());
        }
    }

    // The scenario

    private static ChatScreen chat() {
        return Minecraft.getInstance().gui.screen() instanceof ChatScreen c ? c : null;
    }

    private static void openChat() {
        if (chat() == null) {
            Minecraft.getInstance().gui.setScreen(Compat.chatScreen(""));
        }
    }

    private static void type(String text) {
        openChat();
        typing = text;
        typed = 0;
    }

    private static Messenger.Chan game() {
        return Messenger.channels.get(Messenger.game);
    }

    /** Our confirmed message in the game's chat whose text has this. */
    private static Messenger.Entry mine(String kind, String has) {
        Messenger.Chan c = game();
        if (c == null) {
            return null;
        }
        for (int i = c.list.size() - 1; i >= 0; i--) {
            Messenger.Entry e = c.list.get(i);
            if (e.mine() && e.id > 0 && kind.equals(e.kind) && (has == null || e.text().contains(has) || e.body.toString().contains(has))) {
                return e;
            }
        }
        return null;
    }

    /** Our confirmed message (any channel's list) with this text. */
    private static Messenger.Entry mineIn(Messenger.Chan c, String text) {
        if (c == null) {
            return null;
        }
        for (int i = c.list.size() - 1; i >= 0; i--) {
            Messenger.Entry e = c.list.get(i);
            if (e.mine() && e.id > 0 && e.text().contains(text)) {
                return e;
            }
        }
        return null;
    }

    /** Where typing goes when it isn't the chat (the poll window). */
    private static net.minecraft.client.gui.components.EditBox box;

    /** How far the typing gets this tick: a letter (two in the test), an emoji code at once. */
    private static int next(String text, int at) {
        if (at < text.length() && text.charAt(at) == ':') {
            int end = text.indexOf(':', at + 1);
            if (end > 0 && text.substring(at + 1, end).matches("[A-Za-z0-9_\\-]+/[A-Za-z0-9_\\-]+")) {
                return end + 1;
            }
        }
        return Math.min(text.length(), at + (showcase ? 1 : 2));
    }

    /** An emoji of the server's pack (by name, else the first pack), as typed: ":pack/id:". */
    private static String emo(String pack, int i) {
        for (Library.Pack p : Library.emojiPacks()) {
            if ((pack == null || p.id().equals(pack)) && !p.stickers().isEmpty()) {
                return ChatDecor.emojiCode(p.stickers().get(Math.min(i, p.stickers().size() - 1)));
            }
        }
        return pack == null ? "" : emo(null, i);
    }

    /** The same as a reaction: "pack/id". */
    private static String reaction(String pack, int i) {
        String code = emo(pack, i);
        return code.isEmpty() ? "1" : code.substring(1, code.length() - 1);
    }

    /** For the script: a server command (run over RCON), or a sound's start for the video. */
    private static void rcon(String command) {
        write("rcon", true, command);
    }

    private static void mark(String what) {
        write("mark", true, what + "@" + System.currentTimeMillis());
    }

    private static Step wait(String name, int ticks) {
        long[] until = {0};
        return new Step(name, () -> until[0] = System.currentTimeMillis() + ticks * 50L, () -> System.currentTimeMillis() >= until[0], ticks + 40, false);
    }

    /** Quiet: the game's sound is filmed too. */
    private static void cue() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT/*? if >=1.19.3 {*/.value()/*?}*/, 1.6f, 0.25f));
    }

    /** The chat closes for a while: the person filming plays. A quiet tick a second before it opens again. */
    private static Step free(String name, int ticks) {
        long[] until = {0};
        boolean[] warned = {false};
        return new Step(name, () -> {
            Minecraft.getInstance().gui.setScreen(null);
            Panel.open = false;
            until[0] = System.currentTimeMillis() + ticks * 50L;
            warned[0] = false;
        }, () -> {
            if (!warned[0] && System.currentTimeMillis() > until[0] - 1200) {
                warned[0] = true;
                cue();
            }
            return System.currentTimeMillis() >= until[0];
        }, ticks + 40, false);
    }

    private static Step peerSays(String name, String who, String action, String has) {
        return new Step(name, () -> peer("as " + who + " " + action), () -> {
            Messenger.Chan c = game();
            if (c == null) {
                return false;
            }
            for (Messenger.Entry e : c.list) {
                if (who.equals(e.name) && e.text().contains(has)) {
                    return true;
                }
            }
            return false;
        }, 200, false);
    }

    private static Step chatOpen() {
        return new Step("chat open", () -> {
            openChat();
            Messenger.select(Messenger.game);
        }, () -> chat() != null && Messenger.current == Messenger.game, 100, false);
    }

    private static Step typeIn(String name, java.util.function.Supplier<net.minecraft.client.gui.components.EditBox> where, String text) {
        return new Step(name, () -> {
            box = where.get();
            typing = text;
            typed = 0;
        }, () -> typing == null, 400, false);
    }

    private static PollScreen poll() {
        return Minecraft.getInstance().gui.screen() instanceof PollScreen p ? p : null;
    }

    private static List<Step> showcase() {
        String me = ChatDecor.myName();
        short[] mine = Mic.FromFile.wav("ingamemessenger-autotest-voice.wav");
        short[] hers = Mic.FromFile.wav("ingamemessenger-autotest-voice2.wav");
        long mineMs = mine == null ? 5000 : mine.length / 48, hersMs = hers == null ? 5000 : hers.length / 48;
        List<Step> s = new ArrayList<>();
        s.add(new Step("messenger on", () -> {
        }, () -> Session.on() && game() != null && game().loaded, 600, false));
        s.add(new Step("ready", () -> {
            for (String c : START) {
                rcon(c);
            }
        }, () -> true, 20, false));
        s.add(wait("settle", 60));
        s.add(new Step("start", () -> mark("start"), () -> true, 20, false));
        s.add(free("look around", 160));
        s.add(new Step("view", () -> {
        }, () -> true, 20, true)); // the script keeps this one as the picture to send
        s.add(new Step("Kenji joins", () -> rcon("tellraw @a {\"text\":\"Kenji joined the game\",\"color\":\"yellow\"}"), () -> true, 20, false));
        s.add(peerSays("Kenji asks", "Kenji", "say anyone got spare iron? " + emo("PepeEmoji", 2), "spare iron"));
        s.add(wait("read it", 30));
        s.add(chatOpen());
        s.add(peerSays("Mia asks", PEER, "say yo who's up for the nether tonight " + emo("PepeEmoji", 0), "nether tonight"));
        s.add(new Step("you answer", () -> type("me!! just dont push me into lava again " + emo("SkullEmoji", 0)),
                () -> mineIn(game(), "into lava") != null, 300, false));
        s.add(new Step("Mia reacts", () -> peer("react " + mineIn(game(), "into lava").id + " " + reaction("PepeEmoji", 1)),
                () -> !mineIn(game(), "into lava").reactions.isEmpty(), 200, false));
        s.add(new Step("Mia replies", () -> peer("reply " + mineIn(game(), "into lava").id + " no promises lol"),
                () -> fromPeer(game(), "no promises") != null, 200, false));
        s.add(peerSays("Kenji too", "Kenji", "say im in, need blaze rods anyway", "blaze rods"));
        s.add(new Step("Lucas joins", () -> rcon("tellraw @a {\"text\":\"Lucas joined the game\",\"color\":\"yellow\"}"), () -> true, 20, false));
        s.add(peerSays("Lucas", "Lucas", "say wait for meee " + emo("CapybaraEmoji", 0), "wait for me"));
        s.add(new Step("sticker panel", () -> {
            openChat();
            Panel.openFresh();
        }, () -> Panel.open, 60, false));
        s.add(wait("picking", 30));
        s.add(new Step("sticker", () -> {
            Library.Sticker st = Library.packs().isEmpty() || Library.packs().get(0).stickers().isEmpty() ? null
                    : Library.packs().get(0).stickers().get(0);
            if (st == null) {
                throw new IllegalStateException("the server has no sticker packs");
            }
            sentAt = System.currentTimeMillis();
            Messenger.sendSticker(st.pack(), st.id(), 0);
            Panel.open = false;
        }, () -> mine("sticker", null) != null && mine("sticker", null).arrived >= sentAt, 200, false));
        s.add(free("play a bit", 160));
        s.add(chatOpen());
        s.add(new Step("picture", () -> {
            sentAt = System.currentTimeMillis();
            Images.add(Minecraft.getInstance().gameDirectory.toPath().resolve("ingamemessenger-autotest.png"), false);
            type("sunset from our house rn");
        }, () -> {
            Messenger.Entry e = mine("images", "our house");
            return e != null && e.arrived >= sentAt;
        }, 600, false));
        s.add(new Step("poll window", () -> Minecraft.getInstance().gui.setScreen(new PollScreen(chat(), -1)), () -> poll() != null, 60, false));
        s.add(typeIn("poll question", () -> poll().questionBox(), "what do we build next"));
        s.add(typeIn("poll answer 1", () -> poll().optionBoxes().get(0), "a farm"));
        s.add(typeIn("poll answer 2", () -> poll().optionBoxes().get(1), "a big tower"));
        s.add(new Step("poll third row", () -> poll().addRow(), () -> poll() != null && poll().optionBoxes().size() == 3, 40, false));
        s.add(typeIn("poll answer 3", () -> poll().optionBoxes().get(2), "a giant cherry tree"));
        s.add(new Step("poll typed", () -> {
        }, () -> true, 20, true));
        s.add(new Step("poll", () -> poll().createNow(), () -> {
            Messenger.Entry e = mine("poll", "build next");
            if (e != null) {
                pollId = e.id;
            }
            return e != null;
        }, 200, false));
        s.add(chatOpen());
        s.add(new Step("vote", () -> Net.toServer(new Proto.Vote(pollId, 2)),
                () -> Messenger.byId(pollId) != null && Messenger.byId(pollId).myVote != 0, 200, false));
        s.add(new Step("Kenji reacts to the poll", () -> peer("as Kenji react " + pollId + " " + reaction("CapybaraEmoji", 2)),
                () -> Messenger.byId(pollId) != null && !Messenger.byId(pollId).reactions.isEmpty(), 200, false));
        s.add(new Step("Mia sends a voice message", () -> {
            sentAt = System.currentTimeMillis();
            peer("voice");
            VoiceClient.sendPcm(hers);
        }, () -> {
            Messenger.Chan c = game();
            for (Messenger.Entry e : c == null ? List.<Messenger.Entry>of() : c.list) {
                if ("voice".equals(e.kind) && PEER.equals(e.name) && e.arrived >= sentAt) {
                    voice = e;
                    return true;
                }
            }
            return false;
        }, 300, false));
        s.add(new Step("you listen", () -> {
            VoiceClient.toggle(voice.body.get("k").getAsString(), voice.name, voice.body.has("ms") ? voice.body.get("ms").getAsInt() : 0);
            sentAt = System.currentTimeMillis();
        }, () -> {
            if (VoiceClient.playing(voice.body.get("k").getAsString()) && !marked) {
                marked = true;
                mark("mia");
            }
            return marked && System.currentTimeMillis() - sentAt > hersMs + 300;
        }, 400, false));
        s.add(new Step("you record", () -> {
            openChat();
            VoiceClient.start();
            mark("me");
        }, () -> {
            if (VoiceClient.recording() && VoiceClient.recordingMs() >= mineMs + 150) {
                sentAt = System.currentTimeMillis();
                VoiceClient.stop(true); // right when the words end: the message is as long as they are
                return true;
            }
            return false;
        }, 400, false));
        s.add(new Step("your voice message", () -> {
        }, () -> {
            Messenger.Entry e = mine("voice", null);
            return e != null && e.arrived >= sentAt;
        }, 300, false));
        s.add(new Step("Mia mentions you", () -> peer("say @" + me + " hurry upp " + emo("SkullEmoji", 1)),
                () -> fromPeer(game(), "hurry upp") != null, 200, false));
        s.add(new Step("chat", () -> {
        }, () -> true, 20, true));
        s.add(free("on the way", 120));
        s.add(new Step("Mia writes privately", () -> peer("dm can i borrow ur diamond pick"),
                () -> fromPeer(dm(), "diamond pick") != null, 200, false));
        s.add(wait("a moment", 20));
        s.add(new Step("private chat", () -> {
            openChat();
            Messenger.select(dm().id);
        }, () -> Messenger.current == dm().id && dm().loaded, 200, false));
        s.add(new Step("you answer her", () -> type("if u break it ur mining me 3 diamonds"), () -> {
            Messenger.Entry e = mineIn(dm(), "3 diamonds");
            if (e != null) {
                dmMsgId = e.id;
            }
            return e != null;
        }, 300, false));
        s.add(new Step("she reads", () -> peer("read " + dm().id + " " + dmMsgId), () -> dm().peerRead >= dmMsgId, 200, false));
        s.add(new Step("she types", () -> peer("typing " + dm().id), () -> Messenger.typingHere().contains(PEER), 100, false));
        s.add(wait("typing", 40));
        s.add(new Step("she agrees", () -> peer("dm deal " + emo("PepeEmoji", 3)), () -> fromPeer(dm(), "deal") != null, 200, false));
        s.add(new Step("you again", () -> type("its in the chest by the door"), () -> mineIn(dm(), "by the door") != null, 300, false));
        s.add(new Step("she thanks", () -> peer("dm ur the best"), () -> fromPeer(dm(), "the best") != null, 200, false));
        s.add(new Step("private", () -> {
        }, () -> true, 20, true));
        s.add(new Step("back to the chat", () -> Messenger.select(Messenger.game), () -> Messenger.current == Messenger.game, 60, false));
        s.add(wait("last look", 30));
        s.add(new Step("end", () -> {
            Minecraft.getInstance().gui.setScreen(null);
            mark("end");
        }, () -> true, 20, false));
        return s;
    }

    private static boolean marked;

    /** Where the showcase starts and what the player has: set by the script ("start" in the order file). */
    static List<String> START = List.of();

    private static List<Step> scenario() {
        List<Step> s = new ArrayList<>();
        s.add(new Step("messenger on", () -> {
        }, () -> Session.on() && game() != null && game().loaded, 600, false));
        s.add(new Step("open chat", () -> {
            openChat();
            Messenger.select(Messenger.game);
        }, () -> chat() != null, 100, true));
        s.add(new Step("text", () -> {
            sentAt = System.currentTimeMillis();
            type("autotest " + token + " https://example.com @everyone");
        }, () -> {
            Messenger.Entry e = mine("text", token);
            if (e != null) {
                textId = e.id;
            }
            return e != null;
        }, 200, true));
        s.add(new Step("sticker", () -> {
            Library.Sticker st = Library.packs().isEmpty() || Library.packs().get(0).stickers().isEmpty() ? null
                    : Library.packs().get(0).stickers().get(0);
            if (st == null) {
                throw new IllegalStateException("the server has no sticker packs");
            }
            Messenger.sendSticker(st.pack(), st.id(), 0);
        }, () -> mine("sticker", null) != null && mine("sticker", null).arrived >= sentAt, 200, true));
        s.add(new Step("reaction", () -> Messenger.react(textId, "1"),
                () -> Messenger.byId(textId) != null && !Messenger.byId(textId).reactions.isEmpty(), 200, true));
        s.add(new Step("reply", () -> {
            ChatInput.setReply((int) textId);
            type("reply " + token);
        }, () -> {
            Messenger.Entry e = mine("text", "reply " + token);
            return e != null && Messenger.replyOf(e) == textId;
        }, 200, true));
        s.add(new Step("edit", () -> {
            openChat();
            ChatInput.startEdit(chat(), (int) textId);
            type("autotest " + token + " edited");
        }, () -> Messenger.byId(textId) != null && Messenger.byId(textId).edited > 0, 200, true));
        s.add(new Step("poll", () -> Messenger.sendPoll("autotest " + token + "?", List.of("yes", "no"), false, 0), () -> {
            Messenger.Entry e = mine("poll", token);
            if (e != null) {
                pollId = e.id;
            }
            return e != null;
        }, 200, false));
        s.add(new Step("vote", () -> Net.toServer(new Proto.Vote(pollId, 0)),
                () -> Messenger.byId(pollId) != null && Messenger.byId(pollId).myVote != 0, 200, true));
        s.add(new Step("search", () -> {
            openChat();
            ChatInput.toggleSearch(chat());
            for (char c : token.toCharArray()) {
                ChatInput.searchChar(c);
            }
        }, () -> ChatInput.foundCount() > 0, 200, true));
        s.add(new Step("search closed", () -> {
            if (chat() != null && ChatInput.searching()) {
                ChatInput.stopSearch(chat());
            }
        }, () -> !ChatInput.searching(), 60, false));
        s.add(new Step("command", () -> {
            sentAt = System.currentTimeMillis();
            type("/help");
        }, () -> {
            Messenger.Chan c = game();
            return c != null && c.list.stream().anyMatch(e -> "line".equals(e.kind) && e.arrived >= sentAt);
        }, 200, true));
        s.add(new Step("attach menu", () -> {
            openChat();
            ChatInput.attachOpen = true;
        }, () -> ChatInput.attachOpen, 40, true));
        s.add(new Step("picture", () -> {
            ChatInput.attachOpen = false;
            sentAt = System.currentTimeMillis();
            Images.add(Minecraft.getInstance().gameDirectory.toPath().resolve("ingamemessenger-autotest.png"), false);
            type("picture " + token);
        }, () -> {
            Messenger.Entry e = mine("images", token);
            return e != null && e.arrived >= sentAt;
        }, 600, true));
        s.add(new Step("voice: recording", () -> {
            openChat();
            VoiceClient.start();
        }, () -> VoiceClient.recording() && VoiceClient.recordingMs() >= 3000, 200, true));
        s.add(new Step("voice: sent", () -> {
            sentAt = System.currentTimeMillis();
            VoiceClient.stop(true);
        }, () -> {
            Messenger.Entry e = mine("voice", null);
            if (e != null && e.arrived >= sentAt) {
                voice = e;
            }
            return voice != null;
        }, 300, true));
        s.add(new Step("voice: playing", () -> VoiceClient.toggle(voice.body.get("k").getAsString(), voice.name,
                voice.body.has("ms") ? voice.body.get("ms").getAsInt() : 0),
                () -> VoiceClient.playing(voice.body.get("k").getAsString()), 200, true));
        s.add(new Step("voice: stopped", VoiceClient::stopPlaying, () -> !VoiceClient.playing(voice.body.get("k").getAsString()), 60, false));
        s.add(new Step("new poll window", () -> Minecraft.getInstance().gui.setScreen(new PollScreen(chat(), -1)),
                () -> Minecraft.getInstance().gui.screen() instanceof PollScreen, 60, true));
        s.add(new Step("bot: private message", () -> {
            openChat();
            peer("dm hi " + token);
        }, () -> fromPeer(dm(), token) != null, 200, true));
        s.add(new Step("private chat open", () -> {
            openChat();
            Messenger.select(dm().id);
        }, () -> Messenger.current == dm().id && dm().loaded, 200, true));
        s.add(new Step("private answer", () -> type("hey bot " + token), () -> {
            for (Messenger.Entry e : dm().list) {
                if (e.mine() && e.id > 0 && e.text().contains("hey bot " + token)) {
                    dmMsgId = e.id;
                    return true;
                }
            }
            return false;
        }, 200, false));
        s.add(new Step("bot read it", () -> peer("read " + dm().id + " " + dmMsgId), () -> dm().peerRead >= dmMsgId, 200, true));
        s.add(new Step("bot is typing", () -> peer("typing " + dm().id), () -> Messenger.typingHere().contains(PEER), 100, true));
        s.add(new Step("back to the game's chat", () -> Messenger.select(Messenger.game), () -> Messenger.current == Messenger.game, 60, false));
        s.add(new Step("bot reacts", () -> {
            Messenger.Entry r = mine("text", "reply " + token);
            replyId = r == null ? textId : r.id;
            peer("react " + replyId + " 1");
        }, () -> Messenger.byId(replyId) != null && Messenger.byId(replyId).reactions.contains(PEER), 200, true));
        s.add(new Step("bot answers you", () -> peer("reply " + replyId + " got it " + token), () -> {
            Messenger.Entry e = fromPeer(game(), "got it " + token);
            return e != null && Messenger.replyOf(e) == replyId;
        }, 200, true));
        s.add(new Step("bot mentions you", () -> peer("say @" + ChatDecor.myName() + " look " + token),
                () -> fromPeer(game(), "look " + token) != null, 200, true));
        s.add(new Step("muted", () -> peer("mute"), () -> Muted.here() > 0, 200, true));
        s.add(new Step("muted: nothing goes out", () -> {
            sentAt = System.currentTimeMillis();
            type("blocked " + token);
        }, () -> System.currentTimeMillis() - sentAt > 4000 && mine("text", "blocked " + token) == null, 200, true));
        s.add(new Step("unmuted", () -> peer("unmute"), () -> Muted.here() == 0, 200, false));
        s.add(new Step("delete", () -> {
            openChat();
            ChatReplies.deleteMine(textId);
        }, () -> {
            Messenger.Entry e = Messenger.byId(textId);
            return e == null || "deleted".equals(e.kind);
        }, 200, true));
        s.add(new Step("sticker panel", () -> {
            openChat();
            Panel.openFresh();
        }, () -> Panel.open, 60, true));
        for (int t = 0; t < 3; t++) {
            int tab = t;
            s.add(new Step("settings, tab " + (t + 1), () -> {
                Panel.open = false;
                openChat();
                SettingsScreen.openOn(tab);
                Minecraft.getInstance().gui.setScreen(new SettingsScreen(chat()));
            }, () -> Minecraft.getInstance().gui.screen() instanceof SettingsScreen, 60, true));
        }
        s.add(new Step("back to the game", () -> Minecraft.getInstance().gui.setScreen(null),
                () -> Minecraft.getInstance().gui.screen() == null, 60, true));
        return s;
    }
}
