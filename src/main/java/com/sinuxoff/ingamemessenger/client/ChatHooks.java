package com.sinuxoff.ingamemessenger.client;

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The only public entry into this package for the chat mixins. On a server with the messenger the game's chat keeps
 * nothing: its lines go to the messenger ({@link Messenger#line}), and the messenger's feed ({@link Feed}) is drawn
 * where the chat is.
 */
public final class ChatHooks {
    private ChatHooks() {
    }

    /** False on servers without the mod: every hook then leaves Minecraft's chat as it is. */
    public static boolean on() {
        return Session.on();
    }

    /** True if the line was taken by the messenger (and the game's chat must not keep it). */
    public static boolean take(Component content) {
        Session.sync();
        // the server's "install the mod" line: we have it, even if the server can't tell
        if (content.getContents() instanceof TranslatableContents t && t.getKey().equals("ingamemessenger.plain.install")
                || "ingamemessenger.plain.install".equals(content.getStyle().getInsertion())) {
            return true;
        }
        if (!on()) {
            return false;
        }
        Messenger.line(content);
        return true;
    }

    /** Draws the messenger's feed where the game's chat is. */
    public static void feed(ChatComponent chat, GuiGraphicsExtractor g, int ticks, boolean focused) {
        Feed.render(chat, g, ticks, focused);
    }

    /**
     * Whether the game's own copy of a player's chat line is left out (ours comes from the server): the "chat" type,
     * and Paper's "raw" one, which it uses for chat lines a chat plugin formatted. /msg, /me and /say have their own.
     */
    public static boolean ownChatLine(net.minecraft.network.chat.ChatType.Bound type) {
        if (!on()) {
            return false;
        }
        //? if >=1.20.5 {
        var key = type.chatType().unwrapKey().orElse(null);
        //?} else {
        /*var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        //? if >=1.19.3 {
        var types = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CHAT_TYPE);
        //?} else {
        /^var types = level.registryAccess().registryOrThrow(net.minecraft.core.Registry.CHAT_TYPE_REGISTRY);
        ^///?}
        var key = types.getResourceKey(type.chatType()).orElse(null);
        *///?}
        if (key == null) {
            return false;
        }
        //? if >=1.21.11 {
        String path = key.identifier().getPath();
        //?} else {
        /*String path = key.location().getPath();
        *///?}
        return key.equals(net.minecraft.network.chat.ChatType.CHAT) || path.equals("raw");
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

    public static boolean viewing() {
        return on() && ChatMotion.viewing();
    }

    // Drawing

    public static void drawShade(GuiGraphicsExtractor g, ChatComponent chat) {
        ChatMotion.drawShade(g, chat);
    }

    public static void hiddenForSearch(GuiGraphicsExtractor g, ChatComponent chat) {
        ChatMotion.hiddenForSearch(g, chat);
    }
}
