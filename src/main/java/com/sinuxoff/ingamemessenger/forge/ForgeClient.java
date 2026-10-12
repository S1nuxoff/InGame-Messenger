package com.sinuxoff.ingamemessenger.forge;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.sinuxoff.ingamemessenger.client.ClientEvents;
import com.sinuxoff.ingamemessenger.client.Compat;

import java.util.function.Predicate;

/**
 * Forge client events forwarded to {@link ClientEvents}. The HUD, typed characters and the frame start come through
 * the shared hook mixins instead, since Forge's HUD API changes from version to version.
 */
final class ForgeClient {
    private ForgeClient() {
    }

    static void init(FMLJavaModLoadingContext context) {
        ClientEvents.init();
        //? if >=1.21.9 {
        /*RegisterKeyMappingsEvent.BUS.addListener(e -> e.register(ClientEvents.OPEN));
        TickEvent.ClientTickEvent.Pre.BUS.addListener(e -> ClientEvents.tickStart());
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> ClientEvents.tickEnd());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> ClientEvents.loggedOut());
        ScreenEvent.Init.Post.BUS.addListener(e -> ClientEvents.screenInit(e.getScreen()));
        ScreenEvent.Render.Post.BUS.addListener(e -> ClientEvents.screenRender(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY()));
        // a listener that returns true cancels the event
        ScreenEvent.KeyPressed.Pre.BUS.addListener((Predicate<ScreenEvent.KeyPressed.Pre>) e -> ClientEvents.keyPressed(e.getScreen(), e.getKeyCode()));
        ScreenEvent.MouseButtonPressed.Pre.BUS.addListener((Predicate<ScreenEvent.MouseButtonPressed.Pre>) e ->
                ClientEvents.mouseClicked(e.getScreen(), e.getMouseX(), e.getMouseY(), Compat.button(e.getButton())));
        ScreenEvent.MouseButtonReleased.Pre.BUS.addListener((Predicate<ScreenEvent.MouseButtonReleased.Pre>) e ->
                ClientEvents.mouseReleased(e.getScreen(), e.getMouseX(), e.getMouseY(), Compat.button(e.getButton())));
        ScreenEvent.MouseScrolled.Pre.BUS.addListener((Predicate<ScreenEvent.MouseScrolled.Pre>) e ->
                ClientEvents.mouseScrolled(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getDeltaY()));
        ScreenEvent.Closing.BUS.addListener(e -> ClientEvents.screenClosing(e.getScreen()));
        *///?} else if >=1.21.6 {
        /*RegisterKeyMappingsEvent.getBus(context.getModBusGroup()).addListener(e -> e.register(ClientEvents.OPEN));
        TickEvent.ClientTickEvent.Pre.BUS.addListener(e -> ClientEvents.tickStart());
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> ClientEvents.tickEnd());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> ClientEvents.loggedOut());
        ScreenEvent.Init.Post.BUS.addListener(e -> ClientEvents.screenInit(e.getScreen()));
        ScreenEvent.Render.Post.BUS.addListener(e -> ClientEvents.screenRender(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY()));
        // a listener that returns true cancels the event
        ScreenEvent.KeyPressed.Pre.BUS.addListener((Predicate<ScreenEvent.KeyPressed.Pre>) e -> ClientEvents.keyPressed(e.getScreen(), e.getKeyCode()));
        ScreenEvent.MouseButtonPressed.Pre.BUS.addListener((Predicate<ScreenEvent.MouseButtonPressed.Pre>) e ->
                ClientEvents.mouseClicked(e.getScreen(), e.getMouseX(), e.getMouseY(), Compat.button(e.getButton())));
        ScreenEvent.MouseButtonReleased.Pre.BUS.addListener((Predicate<ScreenEvent.MouseButtonReleased.Pre>) e ->
                ClientEvents.mouseReleased(e.getScreen(), e.getMouseX(), e.getMouseY(), Compat.button(e.getButton())));
        ScreenEvent.MouseScrolled.Pre.BUS.addListener((Predicate<ScreenEvent.MouseScrolled.Pre>) e ->
                ClientEvents.mouseScrolled(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getDeltaY()));
        ScreenEvent.Closing.BUS.addListener(e -> ClientEvents.screenClosing(e.getScreen()));
        *///?} else {
        context.getModEventBus().addListener((RegisterKeyMappingsEvent e) -> e.register(ClientEvents.OPEN));
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addListener((TickEvent.ClientTickEvent.Pre e) -> ClientEvents.tickStart());
        bus.addListener((TickEvent.ClientTickEvent.Post e) -> ClientEvents.tickEnd());
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientEvents.loggedOut());
        bus.addListener((ScreenEvent.Init.Post e) -> ClientEvents.screenInit(e.getScreen()));
        bus.addListener((ScreenEvent.Render.Post e) -> ClientEvents.screenRender(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY()));
        bus.addListener((ScreenEvent.KeyPressed.Pre e) -> {
            if (ClientEvents.keyPressed(e.getScreen(), e.getKeyCode())) {
                e.setCanceled(true);
            }
        });
        bus.addListener((ScreenEvent.MouseButtonPressed.Pre e) -> {
            if (ClientEvents.mouseClicked(e.getScreen(), e.getMouseX(), e.getMouseY(), Compat.button(e.getButton()))) {
                e.setCanceled(true);
            }
        });
        bus.addListener((ScreenEvent.MouseButtonReleased.Pre e) -> {
            if (ClientEvents.mouseReleased(e.getScreen(), e.getMouseX(), e.getMouseY(), Compat.button(e.getButton()))) {
                e.setCanceled(true);
            }
        });
        bus.addListener((ScreenEvent.MouseScrolled.Pre e) -> {
            if (ClientEvents.mouseScrolled(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getDeltaY())) {
                e.setCanceled(true);
            }
        });
        bus.addListener((ScreenEvent.Closing e) -> ClientEvents.screenClosing(e.getScreen()));
        //?}
    }
}
