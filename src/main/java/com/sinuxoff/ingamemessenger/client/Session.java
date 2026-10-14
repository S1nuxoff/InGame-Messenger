package com.sinuxoff.ingamemessenger.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.Platform;
import com.sinuxoff.ingamemessenger.core.protocol.Packets.Hello;
import com.sinuxoff.ingamemessenger.core.protocol.Packets.Welcome;
import com.sinuxoff.ingamemessenger.core.protocol.Protocol;

/**
 * Whether the server we're on has the mod with our protocol. The chat stays Minecraft's own and nothing is sent to the
 * server until its Welcome matches and it answers our Hello with Features. Otherwise the player gets a one-line notice.
 */
public final class Session {
    /** How long to wait for Welcome before saying the server doesn't have the mod. */
    private static final int WAIT_TICKS = 100;
    /** Without Welcome by then, we say Hello first: a server whose loader didn't see our channels answers it. */
    private static final int HELLO_TICKS = 20, HELLO_AGAIN_TICKS = 60;

    /** The connection that got a matching Welcome and then Features. */
    private static Object welcomed;
    /** The connection that got a matching Welcome. */
    private static Object matched;
    /** The connection that got any Welcome. */
    private static Object answered;
    private static Object current;
    /** The last connection we were on. */
    private static Object last;
    private static int ticks;

    private Session() {
    }

    static void init() {
        Net.serverReady = Session::on;
        Net.serverHasMod = () -> {
            Object c = Minecraft.getInstance().getConnection();
            return c != null && c == answered;
        };
    }

    public static boolean on() {
        Object c = Minecraft.getInstance().getConnection();
        return c != null && c == welcomed;
    }

    /**
     * Each server has its own chat. On a new connection (another server, also one behind the same proxy) the chat
     * and what we know about the last server's messages are dropped, before anything of the new one is shown.
     */
    static void sync() {
        Object c = Minecraft.getInstance().getConnection();
        if (c == null || c == last) {
            return;
        }
        if (last != null) {
            Minecraft.getInstance().gui.hud.getChat().clearMessages(false);
            Messenger.reset();
            ClientEvents.loggedOut();
        }
        last = c;
    }

    static void onWelcome(Welcome w) {
        sync();
        Minecraft mc = Minecraft.getInstance();
        Object c = mc.getConnection();
        if (c == null) {
            return;
        }
        answered = c;
        Net.toServer(new Hello(Protocol.VERSION, Platform.get().modVersion()));
        if (w.protocol().equals(Protocol.VERSION)) {
            matched = c;
        } else {
            notice(Component.translatable("ingamemessenger.server.other_version", w.version(), Platform.get().modVersion()));
        }
    }

    static void onFeatures() {
        Object c = Minecraft.getInstance().getConnection();
        if (c != null && c == matched && welcomed != c) {
            welcomed = c;
            ModCompat.tick();
        }
    }

    /** The channel snapshot: the messenger is on (early lines of the game's chat are taken in by Messenger). */
    static void onSnapshot() {
        Object c = Minecraft.getInstance().getConnection();
        if (c != null && c == matched && welcomed != c) {
            welcomed = c;
            ModCompat.tick();
            var all = ((com.sinuxoff.ingamemessenger.mixin.ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat()).ingamemessenger$allMessages();
            java.util.List<net.minecraft.network.chat.Component> early = new java.util.ArrayList<>();
            for (int i = all.size() - 1; i >= 0; i--) {
                early.add(all.get(i).content());
            }
            pendingEarly = early;
        }
    }

    /** The game's chat lines from before the messenger came on, for Messenger to take after the snapshot. */
    static java.util.List<net.minecraft.network.chat.Component> pendingEarly;

    static void tick() {
        sync();
        Minecraft mc = Minecraft.getInstance();
        Object c = mc.getConnection();
        if (c != current) {
            current = c;
            ticks = 0;
        }
        if (c != null && answered != c && ticks <= WAIT_TICKS && (ticks + 1 == HELLO_TICKS || ticks + 1 == HELLO_AGAIN_TICKS)) {
            Net.toServer(new Hello(Protocol.VERSION, Platform.get().modVersion())); // dropped unless the server has our channels
        }
        if (c != null && ticks <= WAIT_TICKS && ++ticks > WAIT_TICKS) {
            if (answered != c) {
                notice(Component.translatable("ingamemessenger.server.missing"));
            } else if (matched == c && welcomed != c) {
                notice(Component.translatable("ingamemessenger.server.unreachable"));
            }
        }
    }

    private static void notice(Component text) {
        if (!Prefs.serverNotices()) {
            return;
        }
        Compat.addToChat(Minecraft.getInstance().gui.hud.getChat(), text.copy().withStyle(ChatFormatting.GRAY), null, null);
    }
}
