package com.sinuxoff.ingamemessenger.client;

//? if >=1.21.11 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import com.sinuxoff.ingamemessenger.mixin.ChatComponentAccessor;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/^*
 * 1.21.11+ draws the chat and finds clicks through a ChatGraphicsAccess; this wraps it and does what the older
 * versions' mixins do: no dark rows, no scroll bar or tag bars, messenger text right of the face, fading in and moving
 * with the chat (click detection too), inline emoji drawn on top.
 ^/
public final class ChatLines implements ChatComponent.ChatGraphicsAccess {
    private final ChatComponent.ChatGraphicsAccess inner;
    /^* Where it's drawn (null: Minecraft only looks for what's clicked). ^/
    private final GuiGraphics g;
    /^* Each line on the screen by its text, to know whose it is. ^/
    private final Map<FormattedCharSequence, GuiMessage.Line> lines = new IdentityHashMap<>();

    public ChatLines(ChatComponent chat, ChatComponent.ChatGraphicsAccess inner, GuiGraphics g) {
        this.inner = inner;
        this.g = g;
        for (GuiMessage.Line line : ((ChatComponentAccessor) chat).ingamemessenger$lines()) {
            lines.put(line.content(), line);
        }
        if (g == null) { // click detection: apply the same offset drawing uses
            float dx = ChatHooks.shiftX(chat), dy = ChatHooks.shiftY();
            inner.updatePose(p -> p.translate(dx, dy));
        }
    }

    @Override
    public void updatePose(Consumer<Matrix3x2f> updater) {
        inner.updatePose(updater);
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int color) {
        // the bubbles replace Minecraft's dark rows
    }

    @Override
    public boolean handleMessage(int textTop, float opacity, FormattedCharSequence message) {
        GuiMessage.Line line = lines.get(message);
        GuiMessageTag tag = line != null ? line.tag() : null;
        float a = opacity * (tag != null ? ChatHooks.alphaOf(tag) : 1f);
        if (a <= 0.012f) {
            return false;
        }
        int dx = tag != null && ChatHooks.shifted(tag) ? ChatHooks.textX(tag) : 0;
        float slide = ChatHooks.slide();
        if (g != null && ChatMotion.clipTop >= 0) { // clip to the chat area like the bubbles
            Gfx.scissor(g, 0, ChatMotion.clipTop, g.guiWidth(), ChatMotion.clipBottom);
            try {
                return draw(textTop, a, message, dx, slide);
            } finally {
                Gfx.unscissor(g);
            }
        }
        return draw(textTop, a, message, dx, slide);
    }

    private boolean draw(int textTop, float a, FormattedCharSequence message, int dx, float slide) {
        inner.updatePose(p -> p.translate(dx, slide));
        boolean hovered = inner.handleMessage(textTop, a, message);
        if (g != null) {
            ChatHooks.drawInlineEmoji(g, Minecraft.getInstance().font, message, 0, textTop, a);
        }
        inner.updatePose(p -> p.translate(-dx, -slide));
        return hovered;
    }

    @Override
    public void handleTag(int x0, int y0, int x1, int y1, float opacity, GuiMessageTag tag) {
        // no tag bars next to bubbles
    }

    @Override
    public void handleTagIcon(int left, int bottom, boolean forceVisible, GuiMessageTag tag, GuiMessageTag.Icon icon) {
        inner.handleTagIcon(left, bottom, forceVisible, tag, icon);
    }
}
*///?}
