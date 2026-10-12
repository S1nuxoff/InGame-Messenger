package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;

/** Chat movement: smooth scrolling, slide-in of new messages, position and height, the shade behind the open chat. */
final class ChatMotion {
    private ChatMotion() {
    }

    // newest message slide-in this frame, shared by the bubbles, the text and the stickers
    static float slide;
    /**
     * Smooth scrolling. The target and current positions are in lines (fractional). Minecraft's scroll gets the whole
     * part each frame; the fraction is a pixel offset with one extra line drawn at the top. Anything else that changes
     * Minecraft's scroll (new lines while scrolled up, page keys, opening the chat) moves both.
     */
    static float scrollPos, scrollTarget, scrollFrac;
    static int scrollApplied = -1;
    static long lastFrameMs;

    /** Mouse wheel: lines to scroll, positive = up (older). */
    public static void scrollBy(double lines) {
        scrollTarget += (float) lines;
    }

    /** Our chat scale on top of Minecraft's. */
    public static double scaleMul() {
        return Prefs.chatScale() / 100.0;
    }

    public static float shiftX(ChatComponent chat) {
        if (!Prefs.chatRight()) {
            return (SIDEBAR + 8) * shade; // room for the chat list on the left of the open chat
        }
        float scale = (float) Compat.chatScale(chat);
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return Math.max(0, sw - (Compat.chatWidth(chat) / scale + 4 + 44) * scale);
    }

    /**
     * Vertical offset: the last message ends level with the bottom of the input, open or closed (vanilla keeps the
     * chat 40px up). Changes in the newest message's height are animated.
     */
    static float shiftYNow;
    private static long shiftYMs;
    /** Gap below the newest bubble, in chat pixels. */
    static int newestGap = 6;

    public static void tickShiftY(ChatComponent chat) {
        Minecraft mc = Minecraft.getInstance();
        float target = 40 - ChatInput.BOTTOM - ChatInput.H - 6 - newestGap * (float) Compat.chatScale(chat);
        if (Typing.showing()) { // the typing line gets its own row
            target -= ChatRenderer.TYPING_ROW;
        }
        if (chatOpen(mc)) {
            target -= ChatInput.barH(); // the chat moves up above the reply bar and the picture tray
        }
        long now = System.currentTimeMillis();
        float dt = shiftYMs == 0 ? 1f : Math.min(0.1f, (now - shiftYMs) / 1000f);
        shiftYMs = now;
        shiftYNow += (target - shiftYNow) * (1f - (float) Math.exp(-dt * 20));
        if (Math.abs(target - shiftYNow) < 0.05f) {
            shiftYNow = target;
        }
    }

    private static boolean keepScroll;

    /** A picture is open in full size over the chat. */
    public static boolean viewing() {
        return Minecraft.getInstance().screen instanceof ImageViewScreen;
    }

    /** Keep the scroll position once when the chat screen is replaced by the picture view. */
    static void keepScrollOnce() {
        keepScroll = true;
    }

    public static boolean takeKeepScroll() {
        boolean k = keepScroll;
        keepScroll = false;
        return k;
    }

    /** The chat is open, or a picture from it is shown full size. */
    static boolean chatOpen(Minecraft mc) {
        return mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen || mc.screen instanceof ImageViewScreen;
    }

    public static float shiftY() {
        return shiftYNow;
    }

    /** No wider than the space next to the input, at least 160. */
    public static int clampWidth(int w) {
        int room = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2 - SIDEBAR;
        return Math.min(w, Math.max(room, Math.min(w, 160)));
    }

    /** One extra line while scrolling between two lines. */
    public static int extraLines() {
        return scrollFrac > 0.001f ? 1 : 0;
    }
    static GuiMessageTag newest;
    static float newestAlpha = 1f;
    /** Chat area on screen this frame (top, bottom) for clipping the sliding text; -1 = none. */
    static int clipTop = -1, clipBottom = -1;

    /** Chat height in chat pixels: the setting, capped at the screen. */
    public static int chatHeightPx(ChatComponent chat) {
        Minecraft mc = Minecraft.getInstance();
        int lineH = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
        double scale = Compat.chatScale(chat);
        if (chatOpen(mc)) { // open: almost up to the top of the screen
            return Math.max(lineH * 2, (int) ((mc.getWindow().getGuiScaledHeight() - 40 + shiftYNow - 14) / Math.max(0.1, scale)));
        }
        int want = Prefs.chatHeight() * lineH;
        int room = (int) ((mc.getWindow().getGuiScaledHeight() - 60) / Math.max(0.1, scale));
        return Math.max(lineH * 2, Math.min(want, room));
    }

    /** Width of the chat list next to the open chat. */
    static final int SIDEBAR = 28;

    /** Open animation, 0..1. */
    static float openness() {
        return shade;
    }

    /** Top of the open chat on screen (the tabs go above it). */
    static float chatTopY() {
        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = mc.gui.getChat();
        return mc.getWindow().getGuiScaledHeight() - 40 + shiftYNow - chatHeightPx(chat) * (float) Compat.chatScale(chat);
    }

    /** Messages are hidden while searching (only the shade stays). */
    public static void hiddenForSearch(GuiGraphics g, ChatComponent chat) {
        drawShade(g, chat);
        ChatUi.clearHits();
    }

    // Shade behind the open chat

    static float shade;
    private static long shadeMs;

    /** Darkens the chat's side of the screen while it's open, fading out across, so messages stay readable. */
    public static void drawShade(GuiGraphics g, ChatComponent chat) {
        Minecraft mc = Minecraft.getInstance();
        boolean open = chatOpen(mc);
        long now = System.currentTimeMillis();
        float dt = shadeMs == 0 ? 1f : Math.min(0.1f, (now - shadeMs) / 1000f);
        shadeMs = now;
        shade += ((open ? 1f : 0f) - shade) * (1f - (float) Math.exp(-dt * 12));
        if (shade < 0.01f) {
            return;
        }
        float sw = g.guiWidth(), sh = g.guiHeight();
        float w = Math.min(sw, Compat.chatWidth(chat) + 4 + ChatRenderer.AV + 90);
        boolean right = Prefs.chatRight();
        int full = Math.round(shade * 0xA0), mid = Math.round(shade * 0x50);
        // two stretches: from the edge to the middle of the chat, then out to transparent
        float[] xs = {0, w * 0.55f, w};
        int[] as = {full, mid, 0};
        float[] xy = new float[16];
        int[] col = new int[8];
        for (int i = 0; i < 2; i++) {
            float x0 = right ? sw - xs[i] : xs[i], x1 = right ? sw - xs[i + 1] : xs[i + 1];
            float l = Math.min(x0, x1), r = Math.max(x0, x1);
            int al = right ? as[i + 1] : as[i], ar = right ? as[i] : as[i + 1];
            float[] q = {l, 0, l, sh, r, sh, r, 0};
            System.arraycopy(q, 0, xy, i * 8, 8);
            col[i * 4] = col[i * 4 + 1] = al << 24;
            col[i * 4 + 2] = col[i * 4 + 3] = ar << 24;
        }
        Gfx.shade(g, xy, col);
    }
}
