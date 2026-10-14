package com.sinuxoff.ingamemessenger.neoforge;

import com.sinuxoff.ingamemessenger.client.Gfx;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.client.ClientEvents;

import java.util.ArrayDeque;
import java.util.Set;

/** NeoForge client events forwarded to {@link ClientEvents}. */
@Mod(value = InGameMessenger.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    private static final Set<Identifier> HOTBAR_LAYERS = Set.of(VanillaGuiLayers.HOTBAR, VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH,
            //? if >=1.21.6 {
            VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, VanillaGuiLayers.CONTEXTUAL_INFO_BAR,
            //?} else {
            /*VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.JUMP_METER,
            *///?}
            VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.SELECTED_ITEM_NAME);
    /** Layers left uncut: the chat itself and full-screen overlays (vignette, pumpkin, portal...), which would show a hard edge. */
    private static final Set<Identifier> UNCUT_LAYERS = Set.of(VanillaGuiLayers.CHAT, VanillaGuiLayers.CAMERA_OVERLAYS,
            VanillaGuiLayers.SLEEP_OVERLAY);
    /** Layers currently clipped, so each layer's end restores only its own clip. */
    private static final ArrayDeque<Boolean> clipped = new ArrayDeque<>();
    private static boolean afterClip;

    public NeoForgeClient(IEventBus modBus) {
        ClientEvents.init();
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(ClientEvents.OPEN));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> ClientEvents.tickStart());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientEvents.tickEnd());
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> ClientEvents.frameStart());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientEvents.loggedOut());

        // hide the hotbar while the chat is open,
        NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Pre e) -> {
            if (ClientEvents.hideHotbar() && HOTBAR_LAYERS.contains(e.getName())) {
                e.setCanceled(true);
            }
        });
        // and clip every other layer (other mods' HUDs too) above the hotbar area
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderGuiLayerEvent.Pre e) -> {
            boolean clip = ClientEvents.hideHotbar() && !UNCUT_LAYERS.contains(e.getName());
            if (clip) {
                ClientEvents.clipBottom(e.getGuiGraphics());
            }
            clipped.push(clip);
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Post e) -> {
            if (!clipped.isEmpty() && clipped.pop()) {
                Gfx.unscissor(e.getGuiGraphics());
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (RenderGuiEvent.Post e) -> {
            afterClip = ClientEvents.hideHotbar();
            if (afterClip) {
                ClientEvents.clipBottom(e.getGuiGraphics());
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderGuiEvent.Post e) -> {
            if (afterClip) {
                afterClip = false;
                Gfx.unscissor(e.getGuiGraphics());
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Pre e) -> {
            clipped.clear();
            //? if >=1.21 {
            ClientEvents.hudStart(e.getGuiGraphics(), e.getPartialTick().getGameTimeDeltaPartialTick(false));
            //?} else {
            /*ClientEvents.hudStart(e.getGuiGraphics(), e.getPartialTick());
            *///?}
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> ClientEvents.hudEnd(e.getGuiGraphics()));

        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> ClientEvents.screenInit(e.getScreen()));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post e) ->
                ClientEvents.screenRender(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY()));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.CharacterTyped.Pre e) -> {
            if (ClientEvents.charTyped(e.getScreen(), e.getCodePoint())) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.KeyPressed.Pre e) -> {
            //? if >=26.3 {
            if (ClientEvents.keyPressed(e.getScreen(), e.getKey())) {
            //?} else {
            /*if (ClientEvents.keyPressed(e.getScreen(), e.getKeyCode())) {
            *///?}
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonPressed.Pre e) -> {
            if (ClientEvents.mouseClicked(e.getScreen(), e.getMouseX(), e.getMouseY(), com.sinuxoff.ingamemessenger.client.Compat.button(e.getButton()))) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonReleased.Pre e) -> {
            if (ClientEvents.mouseReleased(e.getScreen(), e.getMouseX(), e.getMouseY(), com.sinuxoff.ingamemessenger.client.Compat.button(e.getButton()))) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseScrolled.Pre e) -> {
            if (ClientEvents.mouseScrolled(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getScrollDeltaY())) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Closing e) -> ClientEvents.screenClosing(e.getScreen()));
    }
}
