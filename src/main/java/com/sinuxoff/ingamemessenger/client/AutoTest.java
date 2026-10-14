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
 * A test run that plays the messenger by itself, for an outside test script: it starts only when the
 * game folder has ingamemessenger-autotest.json, which it deletes. Each step does what a player would (through the
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
    private static final String PEER = "TestBot";

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
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!started) {
            Path order = mc.gameDirectory.toPath().resolve("ingamemessenger-autotest.json");
            if (!Files.isRegularFile(order) || mc.player == null) {
                return;
            }
            try {
                Files.delete(order);
                log = mc.gameDirectory.toPath().resolve("ingamemessenger-autotest.log");
                Files.writeString(log, "");
            } catch (Exception e) {
                return;
            }
            started = true;
            token = "t" + Long.toString(System.currentTimeMillis() % 1_000_000, 36);
            steps = scenario();
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
            ChatScreen screen = chat();
            if (screen == null) {
                return;
            }
            var input = ((ChatScreenAccessor) screen).ingamemessenger$input();
            typed = Math.min(typing.length(), typed + 2);
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
        pause = STEP_PAUSE;
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
        s.add(new Step("bot: private message", () -> peer("dm hi " + token), () -> fromPeer(dm(), token) != null, 200, true));
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
