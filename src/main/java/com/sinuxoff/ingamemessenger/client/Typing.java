package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphics; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;
import com.sinuxoff.ingamemessenger.mixin.ChatScreenAccessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typing indicator. While the player types in the chat (not a command), the server is told every few seconds and
 * notifies the others, who see "Name is typing..." with animated dots. The server can disable it for everyone; each
 * player can hide their own and others'.
 */
final class Typing {
    /** Enabled on the server. */
    static boolean serverOn = true;
    private static boolean sent;
    private static long sentMs;
    /** Player -> expiry time; it lapses when no update comes. */
    private static final Map<String, Long> others = new LinkedHashMap<>();
    private static final long KEEP_ALIVE = 3000, LAPSE = 6500;

    private Typing() {
    }

    /** Every tick: tells the server when typing starts, stops, and periodically while it lasts. */
    static void tick(Minecraft mc) {
        boolean want = false;
        if (mc.screen instanceof ChatScreen chat && serverOn && Prefs.showMyTyping() && mc.getConnection() != null) {
            String v = ((ChatScreenAccessor) chat).ingamemessenger$input().getValue();
            want = !v.isBlank() && !v.startsWith("/") && !ChatInput.searching() && !Ai.ID.equals(ChatChannels.channel())
                    && !ChatChannels.SERVER.equals(ChatChannels.channel());
        }
        long now = System.currentTimeMillis();
        if (want && (!sent || now - sentMs > KEEP_ALIVE)) {
            Net.toServer(new Packets.Typing(true));
            sent = true;
            sentMs = now;
        } else if (!want && sent) {
            if (mc.getConnection() != null) {
                Net.toServer(new Packets.Typing(false));
            }
            sent = false;
        }
    }

    /** Who types in which chat (null = public). */
    private static final Map<String, java.util.UUID> where = new java.util.HashMap<>();

    static void onState(Packets.TypingState s, java.util.UUID ch) {
        if (s.on()) {
            others.put(s.name(), System.currentTimeMillis() + LAPSE);
            where.put(s.name(), ch);
        } else {
            others.remove(s.name());
            where.remove(s.name());
        }
    }

    /** Typing in client-side chats (Gemini thinking). */
    static void local(String name, java.util.UUID ch, boolean on) {
        if (on) {
            others.put(name, System.currentTimeMillis() + 120_000);
            where.put(name, ch);
        } else {
            others.remove(name);
            where.remove(name);
        }
    }

    /** First player typing in the visible chat, or null. */
    static String first() {
        List<String> h = here();
        return h.isEmpty() ? null : h.get(0);
    }

    private static List<String> here() {
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        others.forEach((n, until) -> {
            if (until >= now && java.util.Objects.equals(where.get(n), ChatChannels.channel())) {
                out.add(n);
            }
        });
        return out;
    }

    static void onFeatures(Packets.Features f) {
        serverOn = f.typing();
        if (!serverOn) {
            others.clear();
        }
    }

    static boolean showing() {
        long now = System.currentTimeMillis();
        return (serverOn || Ai.ID.equals(ChatChannels.channel())) && Prefs.seeTyping() && !here().isEmpty();
    }

    static void reset() {
        others.clear();
        sent = false;
    }

    /** "Name is typing..." with animated dots; nothing if nobody is or the player hid it. */
    static void render(GuiGraphics g, int x, int y) {
        long now = System.currentTimeMillis();
        others.values().removeIf(until -> until < now);
        List<String> names = here();
        if (names.isEmpty() || !serverOn && !Ai.ID.equals(ChatChannels.channel()) || !Prefs.seeTyping()) {
            return;
        }
        Component label = names.size() == 1 ? Component.translatable("ingamemessenger.typing.one", names.get(0))
                : names.size() == 2 ? Component.translatable("ingamemessenger.typing.two", names.get(0), names.get(1))
                : Component.translatable("ingamemessenger.typing.many");
        var font = Minecraft.getInstance().font;
        float k = Panel.smallScale();
        Gfx.push(g);
        Gfx.move(g, x, y, 400);
        Gfx.scale(g, k, k);
        int w = font.width(label);
        Gfx.text(g, font, label, 0, 0, 0xFFB8BECC, true);
        int phase = (int) (now / 300 % 4);
        for (int i = 0; i < 3; i++) {
            int a = i < phase ? 0xFF : 0x50;
            g.fill(w + 2 + i * 3, 6, w + 4 + i * 3, 8, a << 24 | 0xB8BECC);
        }
        Gfx.pop(g);
    }
}
