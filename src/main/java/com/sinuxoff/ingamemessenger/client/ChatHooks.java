package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphics; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.contents.TranslatableContents;
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

    /** False on servers without the mod: every hook then leaves Minecraft's chat as it is. */
    public static boolean on() {
        return Session.on();
    }

    public static int keptMessages(int vanilla) {
        return on() ? MAX_KEPT : vanilla;
    }

    public static int keptLines(int vanilla) {
        return on() ? MAX_LINES : vanilla;
    }

    // Incoming

    /** True if the message was rebuilt (and the original must be dropped). */
    public static boolean transform(ChatComponent chat, Component content, MessageSignature sig, GuiMessageTag tag) {
        Session.sync();
        // the server's "install the mod" line: we have it, even if the server can't tell
        if (content.getContents() instanceof TranslatableContents t && t.getKey().equals("ingamemessenger.plain.install")
                || "ingamemessenger.plain.install".equals(content.getStyle().getInsertion())) {
            return true;
        }
        return on() && ChatMessages.transform(chat, content, sig, tag);
    }

    // Layout

    /** True if the line's text is shifted right of the face. */
    public static boolean shifted(GuiMessageTag tag) {
        return on() && ChatModel.shifted(tag);
    }

    public static int textX(GuiMessageTag tag) {
        return ChatRenderer.textX(tag);
    }

    /** True if the message has a reaction row (one more line when wrapping). */
    public static boolean reactionRow(GuiMessageTag tag) {
        return on() && ChatModel.reactionRow(tag);
    }

    // Size and position

    public static double scaleMul() {
        return on() ? ChatMotion.scaleMul() : 1;
    }

    public static int clampWidth(int w) {
        return on() ? ChatMotion.clampWidth(w) : w;
    }

    public static int chatHeightPx(ChatComponent chat) {
        return ChatMotion.chatHeightPx(chat);
    }

    public static float shiftX(ChatComponent chat) {
        return on() ? ChatMotion.shiftX(chat) : 0;
    }

    public static float shiftY() {
        return on() ? ChatMotion.shiftY() : 0;
    }

    public static void tickShiftY(ChatComponent chat) {
        ChatMotion.tickShiftY(chat);
    }

    public static int extraLines() {
        return on() ? ChatMotion.extraLines() : 0;
    }

    /** Slide offset of the newest message this frame, in pixels. */
    public static float slide() {
        return on() ? ChatMotion.slide : 0;
    }

    public static boolean viewing() {
        return on() && ChatMotion.viewing();
    }

    public static boolean takeKeepScroll() {
        return on() && ChatMotion.takeKeepScroll();
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
        return on() ? ChatRenderer.alphaOf(tag) : 1;
    }

    public static int clipped(GuiGraphics g, IntSupplier draw) {
        return ChatRenderer.clipped(g, draw);
    }

    public static void drawInlineEmoji(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, float alpha) {
        if (on()) {
            ChatDecor.drawInlineEmoji(g, font, text, x, y, alpha);
        }
    }
}
