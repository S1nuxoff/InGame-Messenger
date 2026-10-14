package com.sinuxoff.ingamemessenger.hooks;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.sinuxoff.ingamemessenger.client.ClientEvents;

/** Typed characters before the screen gets them (for loaders without an event for it). */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    //? if >=1.21.9 {
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$char(long window, net.minecraft.client.input.CharacterEvent event, CallbackInfo ci) {
        var screen = Minecraft.getInstance().gui.screen();
        if (screen != null && ClientEvents.charTyped(screen, event.codepoint())) {
            ci.cancel();
        }
    }
    //?} else {
    /*@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$char(long window, int codePoint, int modifiers, CallbackInfo ci) {
        var screen = Minecraft.getInstance().gui.screen();
        if (screen != null && ClientEvents.charTyped(screen, codePoint)) {
            ci.cancel();
        }
    }
    *///?}
}
