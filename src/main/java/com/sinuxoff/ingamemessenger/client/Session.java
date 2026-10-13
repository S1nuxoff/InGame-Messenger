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

    /** The connection that got a matching Welcome and then Features. */
    private static Object welcomed;
    /** The connection that got a matching Welcome. */
    private static Object matched;
    /** The connection that got any Welcome. */
    private static Object answered;
    private static Object current;
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

    static void onWelcome(Welcome w) {
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
        if (c != null && c == matched) {
            welcomed = c;
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Object c = mc.getConnection();
        if (c != current) {
            current = c;
            ticks = 0;
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
        Compat.addToChat(Minecraft.getInstance().gui.getChat(), text.copy().withStyle(ChatFormatting.GRAY), null, null);
    }
}
