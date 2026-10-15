package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;

import java.util.UUID;

/** Players' faces in the chat, and the typing row under the newest message. */
final class Faces {
    private Faces() {
    }

    /** Text starts after the face. */
    static final int AV = 29;
    static final int FACE = 16, TYPING_ROW = 12;

    static void drawFace(GuiGraphicsExtractor g, UUID id, String name, int x, int y, float alpha) {
        drawFace(g, id, name, x, y, FACE, alpha);
    }

    static void drawFace(GuiGraphicsExtractor g, UUID id, String name, int x, int y, int size, float alpha) {
        var conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : id != null ? conn.getPlayerInfo(id) : name != null ? conn.getPlayerInfo(name) : null;
        UUID skinId = id != null ? id : UUID.nameUUIDFromBytes(String.valueOf(name).getBytes());
        //? if >=1.20.2 {
        var face = Compat.skinTexture(info != null ? info.getSkin() : DefaultPlayerSkin.get(skinId));
        //?} else {
        /*var face = info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(skinId);
        *///?}
        if (testSkin(name) != null) {
            face = testSkin(name);
        }
        // face and hat layer, with rounded corners
        float r = size * 0.3f, k = 1 / 64f;
        Gfx.pixelated(face);
        Library.roundedTex(g, face, x, y, size, size, r, 8 * k, 8 * k, 16 * k, 16 * k, alpha);
        Library.roundedTex(g, face, x, y, size, size, r, 40 * k, 8 * k, 48 * k, 16 * k, alpha);
    }

    /** The autotest's skins by name, for the showcase's players who aren't online. */
    private static final java.util.Map<String, Object> testSkins = new java.util.HashMap<>();

    @SuppressWarnings("unchecked")
    private static <T> T testSkin(String name) {
        if (name == null || !Boolean.getBoolean("ingamemessenger.autotest")) {
            return null;
        }
        Object t = testSkins.computeIfAbsent(name, n -> {
            java.nio.file.Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("ingamemessenger-autotest-skins").resolve(n + ".png");
            try {
                var img = com.mojang.blaze3d.platform.NativeImage.read(java.nio.file.Files.readAllBytes(p));
                var tex = Compat.texture("skin", img);
                var id = com.sinuxoff.ingamemessenger.InGameMessenger.id("dyn/skin/" + n.toLowerCase().replaceAll("[^a-z0-9_]", "_"));
                Minecraft.getInstance().getTextureManager().register(id, tex);
                return id;
            } catch (Exception e) {
                return Boolean.FALSE;
            }
        });
        return t == Boolean.FALSE ? null : (T) t;
    }

    /** Typing row under the newest message, next to the faces. */
    static void drawTyping(GuiGraphicsExtractor g) {
        ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
        float cs = (float) Compat.chatScale(chat);
        int y = g.guiHeight() - ChatInput.BOTTOM - ChatInput.H - 6 - 9;
        String who = Typing.first();
        if (who != null && Prefs.seeTyping()) { // face in the face column, like a message
            var conn = Minecraft.getInstance().getConnection();
            UUID id = conn != null && conn.getPlayerInfo(who) != null ? conn.getPlayerInfo(who).getProfile().id() : null;
            int fs = Math.round(11 * cs);
            drawFace(g, id, who, Math.round(ChatMotion.shiftX(chat) + 4 * cs + (FACE * cs - fs) / 2f), y - 2, fs, 1f);
        }
        Typing.render(g, (int) (ChatMotion.shiftX(chat) + (4 + AV) * cs), y);
    }
}
