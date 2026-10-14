package com.sinuxoff.ingamemessenger.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import com.sinuxoff.ingamemessenger.InGameMessenger;

/**
 * Client events, shared by all loaders. Methods returning boolean return true when the input was handled and Minecraft
 * should ignore it. Tapping the chat key opens the chat as usual; holding it opens the sticker picker.
 */
public final class ClientEvents {
    /** Optional key for the sticker picker, unbound by default (holding the chat key does the same). */
    public static final KeyMapping OPEN = new KeyMapping("key.ingamemessenger.open", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(),
            //? if >=1.21.9 {
            KeyMapping.Category.MULTIPLAYER);
            //?} else {
            /*"key.categories.multiplayer");
            *///?}

    /** Hold time that opens the sticker picker. */
    private static final long HOLD_MS = 280;
    /** When the chat key went down, while deciding between tap and hold; -1 = not pressed. */
    private static long chatDownAt = -1;
    /** The picker just closed but the key may still be held: ignore it until released. */
    static boolean waitRelease;

    private ClientEvents() {
    }

    /** Registers the packet handlers. */
    public static void init() {
        Session.init();
        InGameMessenger.onWelcome = w -> Minecraft.getInstance().execute(() -> Session.onWelcome(w));
        // on the main thread, after Features: the stickers it fetches need the messenger on
        InGameMessenger.onStickerIndex = p -> Minecraft.getInstance().execute(() -> Library.onIndex(p.json()));
        InGameMessenger.onStickerData = Library::onData; // a loader thread is waiting for it
        InGameMessenger.onHistory = p -> Minecraft.getInstance().execute(() -> ChatPages.onHistory(p));
        InGameMessenger.onShow = s -> Minecraft.getInstance().execute(() -> ChatMessages.add(s));
        InGameMessenger.onTypingState = t -> Minecraft.getInstance().execute(() -> Typing.onState(t, ChatChannels.takeRoute()));
        InGameMessenger.onFeatures = f -> Minecraft.getInstance().execute(() -> {
            Session.onFeatures();
            Typing.onFeatures(f);
            VoiceClient.onFeatures(f);
            Images.onFeatures(f);
        });
        InGameMessenger.onVoiceShow = v -> Minecraft.getInstance().execute(() -> ChatVoiceNotes.addVoice(v));
        InGameMessenger.onEdited = v -> Minecraft.getInstance().execute(() -> ChatReplies.onEdited(v));
        InGameMessenger.onDeleted = v -> Minecraft.getInstance().execute(() -> ChatReplies.onDeleted(v));
        InGameMessenger.onVoiceData = v -> Minecraft.getInstance().execute(() -> VoiceClient.onData(v));
        InGameMessenger.onImageShow = v -> Minecraft.getInstance().execute(() -> {
            var me = Minecraft.getInstance().player;
            if (me != null && me.getUUID().equals(v.sender())) {
                Images.sent(); // our pictures arrived: hide the progress bar
            }
            ChatAlbums.addImage(v);
        });
        InGameMessenger.onImageData = Images::onData; // a loader thread is waiting for it
        InGameMessenger.onTag = t -> Minecraft.getInstance().execute(() -> ChatModel.onTag(t));
        InGameMessenger.onSearch = r -> Minecraft.getInstance().execute(() -> ChatInput.onSearch(r));
        InGameMessenger.onPollShow = v -> Minecraft.getInstance().execute(() -> ChatPolls.addPoll(v));
        InGameMessenger.onReadState = v -> Minecraft.getInstance().execute(() -> ChatReads.onReadState(v));
        InGameMessenger.onDmShow = v -> Minecraft.getInstance().execute(() -> ChatChannels.onDmShow(v));
        InGameMessenger.onRoute = v -> Minecraft.getInstance().execute(() -> ChatChannels.onRoute(v));
        InGameMessenger.onDmReadState = v -> Minecraft.getInstance().execute(() -> ChatChannels.onDmReadState(v));
        InGameMessenger.onDmChats = v -> Minecraft.getInstance().execute(() -> ChatChannels.onDmChats(v));
        InGameMessenger.onReadAll = v -> Minecraft.getInstance().execute(() -> { // sent once, on join
            ChatReads.onReadAll(v);
            ChatPages.startHistory();
        });
        InGameMessenger.onPollState = v -> Minecraft.getInstance().execute(() -> ChatPolls.onPollState(v));
        InGameMessenger.onMuteState = v -> Minecraft.getInstance().execute(() -> Muted.on(v));
        InGameMessenger.onNames = v -> Minecraft.getInstance().execute(() -> ChatDecor.onNames(v.data()));
        InGameMessenger.onProto = v -> Minecraft.getInstance().execute(() -> {
            if (v instanceof com.sinuxoff.ingamemessenger.core.protocol.Proto.Snapshot) {
                Session.onSnapshot();
            }
            Messenger.handle(v);
        });
        InGameMessenger.onReactions = r -> Minecraft.getInstance().execute(() -> ChatModel.onReactions(r));
    }

    // Ticks and frames

    /** Before Minecraft handles keys: decides between tap and hold of the chat key. */
    public static void tickStart() {
        VoiceClient.tick();
        Images.tick();
        ChatMessages.flushHeld();
        ModCompat.tick();
        Minecraft mc = Minecraft.getInstance();
        if (!Session.on()) { // Minecraft's own chat
            chatDownAt = -1;
            while (OPEN.consumeClick()) {
                // nothing to open
            }
            return;
        }
        if (mc.player == null || mc.gui.screen() != null) {
            chatDownAt = -1;
            return;
        }
        KeyMapping chat = mc.options.keyChat;
        boolean holdable = Compat.isKeyboard(boundKey(chat));
        if (waitRelease) {
            boolean stillDown = holdable && Compat.keyDown(boundKey(chat).getValue());
            while (chat.consumeClick()) {
                // key repeats while still held from the picker
            }
            if (stillDown) {
                return;
            }
            waitRelease = false;
        }
        if (holdable && chatDownAt < 0 && chat.consumeClick()) {
            while (chat.consumeClick()) {
                // one press is enough
            }
            chatDownAt = System.currentTimeMillis();
        }
        if (chatDownAt >= 0) {
            boolean down = Compat.keyDown(boundKey(chat).getValue());
            long held = System.currentTimeMillis() - chatDownAt;
            if (!down) {
                chatDownAt = -1;
                mc.gui.setScreen(Compat.chatScreen("")); // tap: the chat
            } else if (held >= HOLD_MS) {
                chatDownAt = -1;
                mc.gui.setScreen(new PickScreen(chat)); // hold: the sticker picker
            }
        }
        while (OPEN.consumeClick()) {
            if (mc.gui.screen() == null) {
                mc.gui.setScreen(new PickScreen(OPEN));
            }
        }
    }

    public static void tickEnd() {
        Session.tick();
        Typing.tick(Minecraft.getInstance());
    }

    /** Start of each frame: texture uploads and cleanup. */
    public static void frameStart() {
        Library.pump();
    }

    public static void loggedOut() {
        Typing.reset();
        VoiceClient.reset();
        ChatChannels.resetChannels();
    }

    // HUD

    /** The hotbar and everything on it is hidden while the chat is open. */
    public static boolean hideHotbar() {
        return Session.on() && Minecraft.getInstance().gui.screen() instanceof ChatScreen && Prefs.hideHotbar();
    }

    /** Clips drawing to above the hotbar area. */
    public static void clipBottom(GuiGraphicsExtractor g) {
        Gfx.scissor(g, 0, 0, g.guiWidth(), g.guiHeight() - 64);
    }

    /** Before the HUD: our blur behind our screens (instead of Minecraft's). */
    public static void hudStart(GuiGraphicsExtractor g, float partialTick) {
        if (!Session.on()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean ours = mc.gui.screen() instanceof ChatScreen || mc.gui.screen() instanceof SettingsScreen || mc.gui.screen() instanceof PollScreen
                || mc.gui.screen() instanceof ImageViewScreen;
        if (ours && Prefs.blurBehind() && mc.level != null) {
            Gfx.blur(g, partialTick);
        }
    }

    /** After the HUD: typing line next to the closed chat, upload progress, mini player. */
    public static void hudEnd(GuiGraphicsExtractor g0) {
        if (!Session.on()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null && !Compat.hudHidden(mc)) {
            ChatRenderer.drawTyping(g0);
            if (Images.sending) { // upload progress above the hotbar while the chat is closed
                var gg = g0;
                int bw = 180, bx = (gg.guiWidth() - bw) / 2, by = gg.guiHeight() - 72;
                ChatInput.glassBox(gg, bx - 6, by - 3, bw + 12, 15);
                ChatInput.drawProgress(gg, bx, by, bw);
            }
            // mini player: centered above the hotbar and hearts
            var g = g0;
            ChatInput.miniPlayer(g, (g.guiWidth() - ChatInput.MP_W) / 2, g.guiHeight() - 92, -1, -1, false);
        }
    }

    // Chat screen

    private static Screen lastChatScreen;

    /** Opened or resized: room for the smiley button, and the pinned panel. */
    public static void screenInit(Screen screen0) {
        if (screen0 instanceof ChatScreen screen && Session.on()) {
            var input = ((com.sinuxoff.ingamemessenger.mixin.ChatScreenAccessor) screen).ingamemessenger$input();
            ChatInput.place(input, screen.width, screen.height);
            if (screen != lastChatScreen) { // opened, not resized
                lastChatScreen = screen;
                // the chat key opens a chat for talking; the server chat (commands) is where "/" leads
                if (ChatChannels.SERVER.equals(ChatChannels.channel()) && !input.getValue().startsWith("/")) {
                    ChatChannels.leaveServer();
                }
            }
            if (Prefs.pinned() && !Panel.open) {
                Panel.openFresh();
            }
        }
    }

    /** Our layer on top of the chat screen. */
    public static void screenRender(Screen screen0, GuiGraphicsExtractor g, int mx, int my) {
        if (screen0 instanceof ChatScreen screen && Session.on()) {
            Gfx.layerAbove(g); // our windows and lists above the messages
            Panel.command = ((com.sinuxoff.ingamemessenger.mixin.ChatScreenAccessor) screen).ingamemessenger$input().getValue().startsWith("/");
            ChatUi.render(g, mx, my, screen.width, screen.height,
                    Panel.covers(mx, my, screen.width, screen.height));
            Panel.render(g, mx, my, screen.width, screen.height);
            ChatInput.drawLineEmoji(g, screen);
            ChatInput.renderMentions(g, screen, mx, my);
        }
    }

    public static boolean charTyped(Screen screen0, int codePoint) {
        if (!Session.on()) {
            return false;
        }
        if (screen0 instanceof ChatScreen && swallowing()) {
            return true; // key still held from the picker: ignore repeats
        }
        if (screen0 instanceof ChatScreen && VoiceClient.recording()) {
            return true; // no typing while recording
        } else if (screen0 instanceof ChatScreen && ChatInput.searchChar((char) codePoint)) {
            return true; // typing goes into the search
        }
        return false;
    }

    public static boolean keyPressed(Screen screen0, int key) {
        if (!Session.on()) {
            return false;
        }
        if (screen0 instanceof ChatScreen && VoiceClient.recording()) {
            if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) { // Esc cancels the recording, the chat stays
                VoiceClient.stop(false);
            }
            return true;
        }
        if (screen0 instanceof ChatScreen && !ChatInput.searching() && key == com.mojang.blaze3d.platform.InputConstants.KEY_V
                && Compat.ctrlDown()
                && !Compat.shiftDown()) {
            String text = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (text == null || text.isEmpty()) { // maybe an image
                Images.paste();
            }
        }
        if (screen0 instanceof ChatScreen screen && (ChatInput.searchKey(screen, key)
                || key == com.mojang.blaze3d.platform.InputConstants.KEY_F && Compat.ctrlDown() && toggleSearch(screen))) {
            return true;
        }
        if (screen0 instanceof ChatScreen screen && ChatInput.key(screen, key)) {
            return true;
        }
        if (screen0 instanceof ChatScreen screen && ChatInput.enter(screen, key)) {
            return true;
        }
        return false;
    }

    public static boolean mouseClicked(Screen screen0, double x, double y, int button) {
        if (!Session.on()) {
            return false;
        }
        if (screen0 instanceof ChatScreen screen
                && (button == 0 && CommandAnswer.click(x, y)
                || button == 0 && !Panel.covers(x, y, screen.width, screen.height) && Feed.click(screen, x, y)
                || ChatInput.clickTabs(screen, x, y, button)
                || ChatUi.clickDown(x, y, button)
                || button == 0 && (ChatInput.clickMention(screen, x, y)
                || ChatInput.click(screen, x, y))
                || Panel.click(x, y, button, screen.width, screen.height)
                || ChatUi.click(screen, x, y, button,
                Panel.covers(x, y, screen.width, screen.height)))) {
            return true;
        }
        return false;
    }

    public static boolean mouseReleased(Screen screen0, double x, double y, int button) {
        if (!Session.on()) {
            return false;
        }
        if (screen0 instanceof ChatScreen screen && button == 0 && ChatInput.release(screen, x, y)) {
            return true;
        }
        if (screen0 instanceof ChatScreen screen
                && Panel.release(x, y, button, screen.width, screen.height)) {
            return true;
        }
        return false;
    }

    public static boolean mouseScrolled(Screen screen0, double x, double y, double dy) {
        if (!Session.on()) {
            return false;
        }
        if (screen0 instanceof ChatScreen screen
                && (ChatInput.scrollSearch(screen.width, screen.height, x, y, dy)
                || ChatUi.scroll(x, y, dy)
                || Panel.scroll(x, y, dy, screen.width, screen.height))) {
            return true;
        } else if (screen0 instanceof ChatScreen screen
                && !((com.sinuxoff.ingamemessenger.mixin.ChatScreenAccessor) screen).ingamemessenger$input().getValue().startsWith("/")) {
            Feed.scroll(dy * (Compat.shiftDown() ? 1 : 3));
            return true;
        }
        return false;
    }

    public static void screenClosing(Screen screen0) {
        if (screen0 instanceof ChatScreen) {
            Panel.open = false;
            Panel.reset();
            ChatUi.closed();
            ChatInput.setReply(-1);
            ChatInput.stopEdit();
            ChatInput.closeAttach();
            ChatInput.stopSearch(null);
            VoiceClient.stop(false); // discard a recording in progress
        }
    }

    static InputConstants.Key boundKey(KeyMapping k) {
        return ((com.sinuxoff.ingamemessenger.mixin.KeyMappingAccessor) k).ingamemessenger$key();
    }

    private static boolean toggleSearch(ChatScreen screen) {
        ChatInput.toggleSearch(screen);
        return true;
    }

    /** Keys held when the chat opened from the picker; their input is ignored until released. */
    private static final java.util.List<Integer> held = new java.util.ArrayList<>();

    static void swallowHeldKeys() {
        held.clear();
        for (KeyMapping k : new KeyMapping[]{Minecraft.getInstance().options.keyChat, OPEN}) {
            if (Compat.isKeyboard(boundKey(k)) && Compat.keyDown(boundKey(k).getValue())) {
                held.add(boundKey(k).getValue());
            }
        }
    }

    private static boolean swallowing() {
        held.removeIf(k -> !Compat.keyDown(k));
        return !held.isEmpty();
    }
}
