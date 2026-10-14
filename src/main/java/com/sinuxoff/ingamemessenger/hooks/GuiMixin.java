package com.sinuxoff.ingamemessenger.hooks;

//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?}
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.sinuxoff.ingamemessenger.client.ClientEvents;

/** HUD hooks for loaders without HUD events (Fabric, Forge): blur before the HUD, our overlays after it, hotbar hidden while the chat is open. */
//? if >=26.2 {
@Mixin(net.minecraft.client.gui.Hud.class)
//?} else {
/*@Mixin(net.minecraft.client.gui.Gui.class)
*///?}
public abstract class GuiMixin {
    //? if >=26.1 {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void ingamemessenger$start(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        ClientEvents.hudStart(g, delta.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ingamemessenger$end(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        ClientEvents.hudEnd(g);
    }

    /** The hotbar with what sits on it: health, armour, food, air, experience... (plain Minecraft draws them in one go). */
    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$hotbar(CallbackInfo ci) {
        if (ClientEvents.hideHotbar()) {
            ci.cancel();
        }
    }
    //?} else if >=1.21 {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void ingamemessenger$start(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        ClientEvents.hudStart(g, delta.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ingamemessenger$end(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        ClientEvents.hudEnd(g);
    }

    @Inject(method = {"renderHotbarAndDecorations", "renderExperienceLevel"}, at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$hotbar(CallbackInfo ci) {
        if (ClientEvents.hideHotbar()) {
            ci.cancel();
        }
    }
    *///?} else if >=1.20 {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void ingamemessenger$start(GuiGraphicsExtractor g, float partialTick, CallbackInfo ci) {
        ClientEvents.hudStart(g, partialTick);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ingamemessenger$end(GuiGraphicsExtractor g, float partialTick, CallbackInfo ci) {
        ClientEvents.hudEnd(g);
    }

    @Inject(method = {"renderHotbar", "renderPlayerHealth", "renderVehicleHealth", "renderExperienceBar", "renderJumpMeter",
            "renderSelectedItemName"}, at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$hotbar(CallbackInfo ci) {
        if (ClientEvents.hideHotbar()) {
            ci.cancel();
        }
    }
    *///?} else {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void ingamemessenger$start(com.mojang.blaze3d.vertex.PoseStack pose, float partialTick, CallbackInfo ci) {
        ClientEvents.hudStart(new GuiGraphicsExtractor(pose), partialTick);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ingamemessenger$end(com.mojang.blaze3d.vertex.PoseStack pose, float partialTick, CallbackInfo ci) {
        ClientEvents.hudEnd(new GuiGraphicsExtractor(pose));
    }

    @Inject(method = {"renderHotbar", "renderPlayerHealth", "renderVehicleHealth", "renderExperienceBar", "renderJumpMeter",
            "renderSelectedItemName"}, at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$hotbar(CallbackInfo ci) {
        if (ClientEvents.hideHotbar()) {
            ci.cancel();
        }
    }
    *///?}
}
