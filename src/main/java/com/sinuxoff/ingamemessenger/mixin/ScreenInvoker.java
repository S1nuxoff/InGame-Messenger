package com.sinuxoff.ingamemessenger.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.6 {
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.gen.Invoker;
//?}

@Mixin(Screen.class)
public interface ScreenInvoker {
    //? if >=1.21.6 {
    @Invoker("defaultHandleGameClickEvent")
    static void ingamemessenger$click(ClickEvent event, Minecraft mc, Screen screen) {
        throw new AssertionError();
    }
    //?}
}
