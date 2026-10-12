package com.sinuxoff.ingamemessenger.hooks;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.sinuxoff.ingamemessenger.client.ClientEvents;

/** HUD hooks for loaders without HUD events (Fabric, Forge): blur before the HUD, our overlays after it, hotbar hidden while the chat is open. */
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
//?}
public abstract class GuiMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractRenderState", at = @At("HEAD"))
    private void ingamemessenger$start(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        ClientEvents.hudStart(g, delta.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ingamemessenger$end(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        ClientEvents.hudEnd(g);
    }

    /^* The hotbar with what sits on it: health, armour, food, air, experience... (plain Minecraft draws them in one go). ^/
    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$hotbar(CallbackInfo ci) {
        if (ClientEvents.hideHotbar()) {
            ci.cancel();
        }
    }
    *///?} else {
    @Inject(method = "render", at = @At("HEAD"))
    private void ingamemessenger$start(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        ClientEvents.hudStart(g, delta.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ingamemessenger$end(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        ClientEvents.hudEnd(g);
    }

    @Inject(method = {"renderHotbarAndDecorations", "renderExperienceLevel"}, at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$hotbar(CallbackInfo ci) {
        if (ClientEvents.hideHotbar()) {
            ci.cancel();
        }
    }
    //?}
}
