package com.sinuxoff.ingamemessenger.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.sinuxoff.ingamemessenger.client.ChatHooks;
import com.sinuxoff.ingamemessenger.client.ChatInput;

/**
 * Replaces the black chat input bar with the rounded field. Up to 1.21.5 the field is drawn in place of the bar; from
 * 1.21.6 the bar comes before the chat, so it's skipped and the field is drawn after the chat instead.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    //? if >=26.1 {
    /*@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0))
    private void ingamemessenger$field(GuiGraphics g, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(g, x1, y1, x2, y2, color);
        }
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"))
    private void ingamemessenger$fieldAfterChat(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        if (ChatHooks.on()) {
            ChatInput.drawField(g, (ChatScreen) (Object) this);
        }
    }
    *///?} else if >=1.21.11 {
    /*@WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0))
    private void ingamemessenger$field(GuiGraphics g, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(g, x1, y1, x2, y2, color);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V"))
    private void ingamemessenger$fieldAfterChat(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        if (ChatHooks.on()) {
            ChatInput.drawField(g, (ChatScreen) (Object) this);
        }
    }
    *///?} else if >=1.21.6 {
    /*@WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0))
    private void ingamemessenger$field(GuiGraphics g, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(g, x1, y1, x2, y2, color);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V"))
    private void ingamemessenger$fieldAfterChat(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        if (ChatHooks.on()) {
            ChatInput.drawField(g, (ChatScreen) (Object) this);
        }
    }
    *///?} else {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0))
    private void ingamemessenger$field(GuiGraphics g, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (ChatHooks.on()) {
            ChatInput.drawField(g, (ChatScreen) (Object) this);
        } else {
            op.call(g, x1, y1, x2, y2, color);
        }
    }
    //?}

    /** Emoji in the input are turned into their codes when sending. */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true)
    private String ingamemessenger$emoji(String message) {
        return ChatHooks.on() ? ChatInput.send(message) : message;
    }
}
