package com.sinuxoff.ingamemessenger.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;

/**
 * The server as a sender: its lines come with its icon (the one in the server list) and its name, like a bot's
 * messages. Without an icon, ours.
 */
final class ServerIcon {
    private ServerIcon() {
    }

    private static Object loadedFor;
    private static Identifier tex;

    /** The server's name: its space's (set by the server), else as the player calls it in their server list. */
    static String name() {
        Messenger.Space s = Messenger.spaces.get(1L);
        if (s != null && !s.name().isBlank()) {
            return s.name();
        }
        var sd = Minecraft.getInstance().getCurrentServer();
        return sd != null && sd.name != null && !sd.name.isBlank() ? sd.name : "Server";
    }

    private static String loadedIcon;

    /** Its space's icon (sent by the server), else the server list's. */
    private static void load() {
        Object c = Minecraft.getInstance().getConnection();
        Messenger.Space s = Messenger.spaces.get(1L);
        String given = s == null ? null : s.icon();
        if (c == loadedFor && java.util.Objects.equals(given, loadedIcon)) {
            return;
        }
        loadedFor = c;
        loadedIcon = given;
        tex = null;
        var sd = Minecraft.getInstance().getCurrentServer();
        //? if >=1.19.4 {
        byte[] listed = sd == null ? null : sd.getIconBytes();
        //?} else {
        /*byte[] listed = sd == null || sd.getIconB64() == null ? null : java.util.Base64.getDecoder().decode(sd.getIconB64());
        *///?}
        byte[] png = given != null ? java.util.Base64.getDecoder().decode(given) : listed;
        if (png == null) {
            return;
        }
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            Identifier id = com.sinuxoff.ingamemessenger.InGameMessenger.id("server_icon");
            Minecraft.getInstance().getTextureManager().register(id, Compat.texture("server_icon", img));
            tex = id;
        } catch (Exception e) {
            tex = null;
        }
    }

    static void draw(GuiGraphicsExtractor g, int x, int y, int size, float a) {
        load();
        if (tex != null) {
            Library.roundedTex(g, tex, x, y, size, size, size * 0.3f, 0, 0, 1, 1, a);
        } else {
            Panel.round(g, x, y, size, size, Math.max(2, size / 3), (int) (a * 255) << 24 | 0x2A3040);
            Panel.drawIcon(g, "server", x + size * 0.18f, y + size * 0.18f, size * 0.64f, (int) (a * 255) << 24 | 0xFFFFFF);
        }
    }
}
