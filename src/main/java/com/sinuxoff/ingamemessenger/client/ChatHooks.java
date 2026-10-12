package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;

import java.util.function.IntSupplier;

/**
 * The only public entry into this package for the chat mixins.
 * <p>
 * Every incoming message is rebuilt and tagged ({@link ChatModel}): player messages get a colored name above and the
 * face beside, stickers float next to the face, other lines (joins, server messages) get a plain bubble with a matching
 * face or icon. Each message ends with an empty spacer line. {@link ChatRenderer} draws bubbles, faces and reactions,
 * then Minecraft draws the shifted text, then the stickers. New messages slide in ({@link ChatMotion}).
 */
public final class ChatHooks {
    /** Text starts after the face. */
    public static final int AV = ChatRenderer.AV;
    /** Lines the chat keeps (vanilla keeps 100). */
    public static final int MAX_KEPT = ChatModel.MAX_KEPT, MAX_LINES = ChatModel.MAX_LINES;

    private ChatHooks() {
    }

    // Incoming

    /** True if the message was rebuilt (and the original must be dropped). */
    public static boolean transform(ChatComponent chat, Component content, MessageSignature sig, GuiMessageTag tag) {
        return ChatMessages.transform(chat, content, sig, tag);
    }

    // Layout

    /** True if the line's text is shifted right of the face. */
    public static boolean shifted(GuiMessageTag tag) {
        return ChatModel.shifted(tag);
    }

    public static int textX(GuiMessageTag tag) {
        return ChatRenderer.textX(tag);
    }

    /** True if the message has a reaction row (one more line when wrapping). */
    public static boolean reactionRow(GuiMessageTag tag) {
        return ChatModel.reactionRow(tag);
    }

    // Size and position

    public static double scaleMul() {
        return ChatMotion.scaleMul();
    }

    public static int clampWidth(int w) {
        return ChatMotion.clampWidth(w);
    }

    public static int chatHeightPx(ChatComponent chat) {
        return ChatMotion.chatHeightPx(chat);
    }

    public static float shiftX(ChatComponent chat) {
        return ChatMotion.shiftX(chat);
    }

    public static float shiftY() {
        return ChatMotion.shiftY();
    }

    public static void tickShiftY(ChatComponent chat) {
        ChatMotion.tickShiftY(chat);
    }

    public static int extraLines() {
        return ChatMotion.extraLines();
    }

    /** Slide offset of the newest message this frame, in pixels. */
    public static float slide() {
        return ChatMotion.slide;
    }

    public static boolean viewing() {
        return ChatMotion.viewing();
    }

    public static boolean takeKeepScroll() {
        return ChatMotion.takeKeepScroll();
    }

    // Drawing

    public static void drawShade(GuiGraphics g, ChatComponent chat) {
        ChatMotion.drawShade(g, chat);
    }

    public static void hiddenForSearch(GuiGraphics g, ChatComponent chat) {
        ChatMotion.hiddenForSearch(g, chat);
    }

    public static void renderBubbles(ChatComponent chat, GuiGraphics g, int tickCount, boolean focused) {
        ChatRenderer.renderBubbles(chat, g, tickCount, focused);
    }

    public static void render(ChatComponent chat, GuiGraphics g, int tickCount, boolean focused) {
        ChatRenderer.render(chat, g, tickCount, focused);
    }

    /** Fade-in of a line's message, 1 = fully visible. */
    public static float alphaOf(GuiMessageTag tag) {
        return ChatRenderer.alphaOf(tag);
    }

    public static int clipped(GuiGraphics g, IntSupplier draw) {
        return ChatRenderer.clipped(g, draw);
    }

    public static void drawInlineEmoji(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, float alpha) {
        ChatDecor.drawInlineEmoji(g, font, text, x, y, alpha);
    }
}
