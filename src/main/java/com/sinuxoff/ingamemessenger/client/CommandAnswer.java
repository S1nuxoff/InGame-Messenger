package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * A command typed in a channel other than the game's chat: its answer (which goes to the game's chat) is also shown
 * over the input, "only you see this", like a bot's answer in Discord. Click it to open the game's chat.
 */
final class CommandAnswer {
    private CommandAnswer() {
    }

    /** How long after a command its answer is taken, and how long it stays. */
    private static final long WAIT_MS = 2500, STAY_MS = 20_000;
    private static final int MAX_LINES = 12, LINE = 10;

    private static String command;
    private static long sentAt, shownAt;
    private static Messenger.Entry answer;
    private static int[] box, close;

    /** A command was sent from the open channel. */
    static void sent(String text) {
        Messenger.Chan c = Messenger.open();
        if (c == null || c.game) {
            command = null;
            return;
        }
        command = text.length() > 40 ? text.substring(0, 40) + "…" : text;
        sentAt = System.currentTimeMillis();
        answer = null;
    }

    /** A line of the game's chat came: the answer, if a command was just sent from another channel. */
    static void line(Messenger.Entry e) {
        if (command != null && System.currentTimeMillis() - sentAt < WAIT_MS) {
            answer = e;
            shownAt = System.currentTimeMillis();
        }
    }

    private static boolean showing() {
        Messenger.Chan c = Messenger.open();
        return answer != null && c != null && !c.game && System.currentTimeMillis() - shownAt < STAY_MS;
    }

    static void draw(GuiGraphicsExtractor g, ChatScreen screen, int mx, int my) {
        box = close = null;
        if (!showing()) {
            return;
        }
        var font = Minecraft.getInstance().font;
        // as wide as the game's chat (servers lay their answers out for it), at least as wide as the input
        int wrap = Compat.chatWidth(Minecraft.getInstance().gui.hud.getChat());
        int x = ChatInput.fieldX(screen.width), w = Math.min(screen.width - x - 6, Math.max(ChatInput.fieldW(screen.width), wrap + 16));
        List<FormattedCharSequence> lines = font.split(answer.raw, w - 16);
        int shown = Math.min(MAX_LINES, lines.size()), more = lines.size() - shown;
        int h = 16 + shown * LINE + (more > 0 ? LINE + 2 : 0) + 6;
        int y = ChatInput.fieldY(screen.height) - 6 - h - ChatInput.barH();
        Gfx.push(g);
        Gfx.raise(g, 380);
        ChatInput.glassBox(g, x, y, w, h, 0xF0);
        Gfx.text(g, font, Component.translatable("ingamemessenger.answer.title", command), x + 8, y + 5,
                0xFF9C8CFF, false);
        close = new int[]{x + w - 14, y + 3, 11, 11};
        boolean overClose = in(close, mx, my);
        Gfx.text(g, font, "✕", close[0] + 2, close[1] + 2, overClose ? 0xFFFFFFFF : 0xFF8E95A6, false);
        int ly = y + 17;
        for (int i = 0; i < shown; i++) {
            Gfx.text(g, font, lines.get(i), x + 8, ly, 0xFFE4E6EE, false);
            ly += LINE;
        }
        if (more > 0) {
            Gfx.text(g, font, Component.translatable("ingamemessenger.answer.more", String.valueOf(more)),
                    x + 8, ly + 2, 0xFF6CC6FF, false);
        }
        box = new int[]{x, y, w, h};
        Gfx.pop(g);
    }

    private static boolean in(int[] r, double mx, double my) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    /** Close: hides it; anywhere else on it: opens the game's chat, where the whole answer is. */
    static boolean click(double mx, double my) {
        if (in(close, mx, my)) {
            answer = null;
            return true;
        }
        if (in(box, mx, my)) {
            answer = null;
            Messenger.select(Messenger.game);
            return true;
        }
        return false;
    }
}
