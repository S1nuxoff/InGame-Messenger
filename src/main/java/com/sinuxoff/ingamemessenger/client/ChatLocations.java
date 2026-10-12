package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.sinuxoff.ingamemessenger.Platform;
import com.sinuxoff.ingamemessenger.core.text.ChatText;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Shared coordinates. Sent in JourneyMap's waypoint format ("[name:"S1nuxoff", x:1, y:64, z:-3, dim:minecraft:overworld]"),
 * so players without this mod still get a JourneyMap link or the numbers. Shown as a card; clicking it creates a
 * JourneyMap waypoint or copies the coordinates.
 */
final class ChatLocations {
    private ChatLocations() {
    }

    static final Map<GuiMessageTag, ChatText.Location> locOf = new IdentityHashMap<>();
    static ChatText.Location pendingLoc;
    static final int LOC_W = 128;

    /** The player's position as text to send, or null outside a world. */
    static String myLocation() {
        var p = Minecraft.getInstance().player;
        if (p == null) {
            return null;
        }
        return new ChatText.Location(p.getGameProfile().getName(), p.getBlockX(), p.getBlockY(), p.getBlockZ(),
                p.level().dimension().location().toString()).wire();
    }

    static String locWord() {
        return Component.translatable("ingamemessenger.loc.word").getString();
    }

    private static String dimName(String dim) {
        return switch (dim) {
            case "minecraft:overworld" -> Component.translatable("ingamemessenger.loc.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("ingamemessenger.loc.nether").getString();
            case "minecraft:the_end" -> Component.translatable("ingamemessenger.loc.end").getString();
            default -> {
                String path = dim.substring(dim.indexOf(':') + 1).replace('_', ' ');
                yield path.isEmpty() ? dim : Character.toUpperCase(path.charAt(0)) + path.substring(1);
            }
        };
    }

    static boolean journeyMap() {
        return Platform.get().isModLoaded("journeymap");
    }

    /** Pin icon, coordinates, dimension and distance (or the click action on hover). */
    static void drawLoc(GuiGraphics g, ChatText.Location l, int x, int y, float a) {
        var mc = Minecraft.getInstance();
        int s = 17, by = y + 1;
        boolean over = ChatUi.over(x, by, LOC_W, s);
        Panel.round(g, x, by, s, s, s / 2, (int) (a * 255) << 24 | (over ? 0x4CD98A : 0x3DBE7A));
        Panel.drawIcon(g, "pin", x + 3.5f, by + 3, s - 7, (int) (a * 255) << 24 | 0xFFFFFF);
        int tx = x + s + 5;
        Gfx.text(g, mc.font, l.x() + ", " + l.y() + ", " + l.z(), tx, by, (int) (a * 255) << 24 | (over ? 0xFFFFFF : 0xF2F3F7), false);
        String second;
        if (over) {
            second = Component.translatable(journeyMap() ? "ingamemessenger.loc.hint_jm" : "ingamemessenger.loc.hint_copy").getString();
        } else {
            second = dimName(l.dim());
            var p = mc.player;
            if (p != null && p.level().dimension().location().toString().equals(l.dim())) {
                double d = Math.sqrt(p.distanceToSqr(l.x() + 0.5, l.y(), l.z() + 0.5));
                second += " \u00B7 " + Component.translatable("ingamemessenger.loc.dist", d < 1000 ? String.valueOf((int) d)
                        : String.format(java.util.Locale.ROOT, "%.1fk", d / 1000)).getString();
            }
        }
        ChatDates.smallText(g, second, tx, by + 11, (int) (a * 255) << 24 | (over ? 0x9BE7A6 : 0x8E95A6));
        ChatUi.loc(l, x, by, LOC_W, s);
    }

    /** Opens JourneyMap's waypoint editor prefilled, or copies the coordinates. */
    static void openLoc(net.minecraft.client.gui.screens.Screen screen, ChatText.Location l) {
        var mc = Minecraft.getInstance();
        if (journeyMap()) {
            Compat.clickCommand(screen, "/jmc wpedit " + l.wire());
        } else {
            mc.keyboardHandler.setClipboard(l.coords());
            mc.gui.setOverlayMessage(Component.translatable("ingamemessenger.loc.copied"), false);
        }
    }
}
