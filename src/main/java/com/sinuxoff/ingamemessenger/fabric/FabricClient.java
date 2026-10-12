package com.sinuxoff.ingamemessenger.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import com.sinuxoff.ingamemessenger.InGameMessenger;
import com.sinuxoff.ingamemessenger.Net;
import com.sinuxoff.ingamemessenger.client.ClientEvents;
import com.sinuxoff.ingamemessenger.core.protocol.PacketType;
import com.sinuxoff.ingamemessenger.core.protocol.Packets;

/** Fabric client entry point. HUD, typed characters and frame start come through our mixins (no Fabric events for those). */
public final class FabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricMod.PLATFORM.toServer = p -> ClientPlayNetworking.send(new Net.Payload(p));
        for (PacketType<?> t : Packets.ALL) {
            if (t.direction() == PacketType.Direction.TO_CLIENT) {
                ClientPlayNetworking.registerGlobalReceiver(Net.type(t), (p, ctx) -> InGameMessenger.toClient(p.packet()));
            }
        }
        ClientEvents.init();
        //? if >=26.1 {
        /*net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(ClientEvents.OPEN);
        *///?} else {
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(ClientEvents.OPEN);
        //?}
        ClientTickEvents.START_CLIENT_TICK.register(mc -> ClientEvents.tickStart());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientEvents.tickEnd());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ClientEvents.loggedOut());
        // Fabric clears screen listeners on every init, so they're added again each time
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            ClientEvents.screenInit(screen);
            //? if >=26.1 {
            /*ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> ClientEvents.screenRender(s, g, mx, my));
            *///?} else {
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> ClientEvents.screenRender(s, g, mx, my));
            //?}
            //? if >=1.21.9 {
            /*ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> !ClientEvents.keyPressed(s, e.key()));
            ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !ClientEvents.mouseClicked(s, e.x(), e.y(), com.sinuxoff.ingamemessenger.client.Compat.button(e.button())));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !ClientEvents.mouseReleased(s, e.x(), e.y(), com.sinuxoff.ingamemessenger.client.Compat.button(e.button())));
            *///?} else {
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, mods) -> !ClientEvents.keyPressed(s, key));
            ScreenMouseEvents.allowMouseClick(screen).register((s, x, y, button) -> !ClientEvents.mouseClicked(s, x, y, button));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, x, y, button) -> !ClientEvents.mouseReleased(s, x, y, button));
            //?}
            ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, dx, dy) -> !ClientEvents.mouseScrolled(s, x, y, dy));
            ScreenEvents.remove(screen).register(ClientEvents::screenClosing);
        });
    }
}
