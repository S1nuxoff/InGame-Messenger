package com.sinuxoff.ingamemessenger.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import com.sinuxoff.ingamemessenger.mixin.ChatComponentAccessor;

/** Client-side code that differs between Minecraft versions, apart from drawing (see {@link Gfx}). */
public final class Compat {
    private Compat() {
    }

    /** Chat screen with this initial text. */
    public static ChatScreen chatScreen(String text) {
        //? if >=1.21.9 {
        /*return new ChatScreen(text, false);
        *///?} else {
        return new ChatScreen(text);
        //?}
    }

    /** The name only shows in debugging. */
    public static DynamicTexture texture(String name, NativeImage img) {
        //? if >=1.21.5 {
        /*return new DynamicTexture(() -> "ingamemessenger:" + name, img);
        *///?} else {
        return new DynamicTexture(img);
        //?}
    }

    /** Copy of a chat line with different text (same time, signature, tag). */
    public static GuiMessage withText(GuiMessage m, Component text) {
        //? if >=26.1 {
        /*return new GuiMessage(m.addedTime(), text, m.signature(), m.source(), m.tag());
        *///?} else {
        return new GuiMessage(m.addedTime(), text, m.signature(), m.tag());
        //?}
    }

    /** Copy with a different tag. */
    public static GuiMessage withText(GuiMessage m, Component text, GuiMessageTag tag) {
        //? if >=26.1 {
        /*return new GuiMessage(m.addedTime(), text, m.signature(), m.source(), tag);
        *///?} else {
        return new GuiMessage(m.addedTime(), text, m.signature(), tag);
        //?}
    }

    /** Advancements visible to the player. */
    public static java.util.List<net.minecraft.advancements.DisplayInfo> advancements(net.minecraft.client.multiplayer.ClientPacketListener conn) {
        java.util.List<net.minecraft.advancements.DisplayInfo> out = new java.util.ArrayList<>();
        //? if >=26.3 {
        /*var tree = conn.getAdvancements().tree();
        *///?} else {
        var tree = conn.getAdvancements().getTree();
        //?}
        for (var node : tree.nodes()) {
            node.holder().value().display().ifPresent(out::add);
        }
        return out;
    }

    public static Component title(net.minecraft.advancements.DisplayInfo display) {
        //? if >=26.3 {
        /*return display.title();
        *///?} else {
        return display.getTitle();
        //?}
    }

    public static net.minecraft.world.item.ItemStack icon(net.minecraft.advancements.DisplayInfo display) {
        //? if >=26.3 {
        /*return display.icon().create();
        *///?} else if >=26.1 {
        /*return display.getIcon().create();
        *///?} else {
        return display.getIcon();
        //?}
    }

    /** Presses Enter in the screen (in the chat: sends the input). */
    public static void pressEnter(net.minecraft.client.gui.screens.Screen screen) {
        //? if >=1.21.9 {
        /*screen.keyPressed(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
        *///?} else {
        screen.keyPressed(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0);
        //?}
    }

    /** True if the key mapping is bound to this key (GLFW key, scancode). */
    public static boolean isKey(net.minecraft.client.KeyMapping k, int key, int scan) {
        var b = ClientEvents.boundKey(k);
        //? if >=26.3 {
        /*return isKeyboard(b) && b.getValue() == key;
        *///?} else {
        return b.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM ? b.getValue() == key
                : b.getType() == com.mojang.blaze3d.platform.InputConstants.Type.SCANCODE && b.getValue() == scan;
        //?}
    }

    /** True if the key mapping is bound to this mouse button (see button). */
    public static boolean isMouse(net.minecraft.client.KeyMapping k, int button) {
        var b = ClientEvents.boundKey(k);
        return b.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE && button(b.getValue()) == button;
    }

    /**
     * Mouse button in our numbering: 0 left, 1 right, 2 middle, 3+ side buttons (GLFW's, used until 26.3; 26.3's SDL
     * numbers them 1 left, 2 middle, 3 right, 4+ side).
     */
    public static int button(int minecraftButton) {
        //? if >=26.3 {
        /*return switch (minecraftButton) {
            case 1 -> 0;
            case 3 -> 1;
            case 2 -> 2;
            default -> minecraftButton - 1;
        };
        *///?} else {
        return minecraftButton;
        //?}
    }

    //? if >=1.21.9 {
    /*public static net.minecraft.resources.ResourceLocation skinTexture(net.minecraft.world.entity.player.PlayerSkin skin) {
        return skin.body().texturePath();
    }
    *///?} else {
    public static net.minecraft.resources.ResourceLocation skinTexture(net.minecraft.client.resources.PlayerSkin skin) {
        return skin.texture();
    }
    //?}

    /** Action bar message. */
    public static void actionBar(net.minecraft.world.entity.player.Player p, Component text) {
        //? if >=26.1 {
        /*p.sendOverlayMessage(text);
        *///?} else {
        p.displayClientMessage(text, true);
        //?}
    }

    /** Adds a message to the chat the way server chat lines arrive. */
    public static void addToChat(ChatComponent chat, Component text, net.minecraft.network.chat.MessageSignature sig, GuiMessageTag tag) {
        //? if >=26.1 {
        /*chat.addPlayerMessage(text, sig, tag);
        *///?} else {
        chat.addMessage(text, sig, tag);
        //?}
    }

    /** A chat line built by us, to be added later. */
    public static GuiMessage message(int tick, Component text, net.minecraft.network.chat.MessageSignature sig, GuiMessageTag tag) {
        //? if >=26.1 {
        /*return new GuiMessage(tick, text, sig, net.minecraft.client.multiplayer.chat.GuiMessageSource.PLAYER, tag);
        *///?} else {
        return new GuiMessage(tick, text, sig, tag);
        //?}
    }

    /** Style using our font (the blank glyph for inline emoji). */
    public static net.minecraft.network.chat.Style font(net.minecraft.network.chat.Style style, net.minecraft.resources.ResourceLocation font) {
        //? if >=1.21.9 {
        /*return style.withFont(new net.minecraft.network.chat.FontDescription.Resource(font));
        *///?} else {
        return style.withFont(font);
        //?}
    }

    /** Ctrl, or Cmd on a Mac. */
    public static boolean ctrlDown() {
        //? if >=1.21.9 {
        /*return net.minecraft.client.Minecraft.getInstance().hasControlDown();
        *///?} else {
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
        //?}
    }

    public static void openUri(String uri) {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(uri));
        *///?} else if >=1.21.11 {
        /*net.minecraft.util.Util.getPlatform().openUri(uri);
        *///?} else {
        net.minecraft.Util.getPlatform().openUri(uri);
        //?}
    }

    /** HUD hidden with F1. */
    public static boolean hudHidden(net.minecraft.client.Minecraft mc) {
        //? if >=26.2 {
        /*return mc.gui.hud.isHidden();
        *///?} else {
        return mc.options.hideGui;
        //?}
    }

    public static boolean shiftDown() {
        //? if >=1.21.9 {
        /*return net.minecraft.client.Minecraft.getInstance().hasShiftDown();
        *///?} else {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
        //?}
    }

    /** Runs a command as if a chat link with it was clicked (works for client commands too). */
    public static void clickCommand(net.minecraft.client.gui.screens.Screen screen, String command) {
        //? if >=1.21.11 {
        /*var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p != null) {
            p.connection.sendUnattendedCommand(command.startsWith("/") ? command.substring(1) : command, screen);
        }
        *///?} else {
        screen.handleComponentClicked(net.minecraft.network.chat.Style.EMPTY.withClickEvent(runCommand(command)));
        //?}
    }

    /** What an edit box displays for its text (the value stays as typed). */
    public static void showAs(net.minecraft.client.gui.components.EditBox box,
                              java.util.function.BiFunction<String, Integer, net.minecraft.util.FormattedCharSequence> format) {
        //? if >=1.21.9 {
        /*box.addFormatter(format::apply);
        *///?} else {
        box.setFormatter(format::apply);
        //?}
    }

    /** Main inventory and hotbar (no armor). */
    public static java.util.List<net.minecraft.world.item.ItemStack> items(net.minecraft.world.entity.player.Player p) {
        //? if >=1.21.5 {
        /*return p.getInventory().getNonEquipmentItems();
        *///?} else {
        return p.getInventory().items;
        //?}
    }

    /** Time of day in ticks (24000 per day). */
    public static long dayTime(net.minecraft.world.level.Level level) {
        //? if >=26.1 {
        /*return level.getOverworldClockTime();
        *///?} else {
        return level.getDayTime();
        //?}
    }

    /** Click event that runs this command. */
    public static net.minecraft.network.chat.ClickEvent runCommand(String command) {
        //? if >=1.21.5 {
        /*return new net.minecraft.network.chat.ClickEvent.RunCommand(command);
        *///?} else {
        return new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, command);
        //?}
    }

    /** Keyboard key, not a mouse button. */
    public static boolean isKeyboard(com.mojang.blaze3d.platform.InputConstants.Key k) {
        //? if >=26.3 {
        /*return k.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD;
        *///?} else {
        return k.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM;
        //?}
    }

    public static boolean keyDown(int key) {
        //? if >=26.3 {
        /*return com.mojang.blaze3d.platform.InputConstants.isKeyDown(key);
        *///?} else if >=1.21.9 {
        /*return com.mojang.blaze3d.platform.InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), key);
        *///?} else {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), key);
        //?}
    }

    /** System file picker for images. Runs off the render thread where possible; done is called on the render thread. */
    public static void pickPicture(String title, String kinds, java.nio.file.Path dir, java.util.function.Consumer<java.nio.file.Path> done) {
        //? if >=26.3 {
        /*// SDL window (26.x): asked on the render thread, answers later
        long filters = org.lwjgl.system.MemoryUtil.nmemAlloc(org.lwjgl.sdl.SDL_DialogFileFilter.SIZEOF);
        java.nio.ByteBuffer name = org.lwjgl.system.MemoryUtil.memUTF8(kinds), pattern = org.lwjgl.system.MemoryUtil.memUTF8("png;jpg;jpeg;bmp;gif");
        org.lwjgl.sdl.SDL_DialogFileFilter.create(filters).name(name).pattern(pattern);
        org.lwjgl.sdl.SDL_DialogFileCallback[] cb = new org.lwjgl.sdl.SDL_DialogFileCallback[1];
        cb[0] = org.lwjgl.sdl.SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
            try {
                long first = filelist != 0 ? org.lwjgl.system.MemoryUtil.memGetAddress(filelist) : 0;
                if (first != 0) {
                    java.nio.file.Path p = java.nio.file.Path.of(org.lwjgl.system.MemoryUtil.memUTF8(first));
                    net.minecraft.client.Minecraft.getInstance().execute(() -> done.accept(p));
                }
            } finally {
                org.lwjgl.system.MemoryUtil.nmemFree(filters);
                org.lwjgl.system.MemoryUtil.memFree(name);
                org.lwjgl.system.MemoryUtil.memFree(pattern);
                net.minecraft.client.Minecraft.getInstance().execute(() -> cb[0].free());
            }
        });
        org.lwjgl.sdl.SDLDialog.SDL_ShowOpenFileDialog(cb[0], 0L, 0L,
                org.lwjgl.sdl.SDL_DialogFileFilter.create(filters, 1), dir.toAbsolutePath().toString(), false);
        *///?} else {
        Thread t = new Thread(() -> {
            String path;
            try (org.lwjgl.system.MemoryStack st = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.PointerBuffer f = st.mallocPointer(5);
                for (String p : new String[]{"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"}) {
                    f.put(st.UTF8(p));
                }
                f.flip();
                path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(title,
                        dir.toAbsolutePath() + java.io.File.separator, f, kinds, false);
            } catch (Throwable e) {
                com.sinuxoff.ingamemessenger.InGameMessenger.LOG.warn("File window failed", e);
                return;
            }
            if (path != null && !path.isBlank()) {
                net.minecraft.client.Minecraft.getInstance().execute(() -> done.accept(java.nio.file.Path.of(path)));
            }
        }, "InGameMessenger file picker");
        t.setDaemon(true);
        t.start();
        //?}
    }

    public static double chatScale(ChatComponent chat) {
        return ((ChatComponentAccessor) chat).ingamemessenger$scale();
    }

    /** Chat width in screen pixels. */
    public static int chatWidth(ChatComponent chat) {
        return ((ChatComponentAccessor) chat).ingamemessenger$width();
    }
}
