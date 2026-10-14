package com.sinuxoff.ingamemessenger.mixin;

import com.sinuxoff.ingamemessenger.client.ChatHooks;
import com.sinuxoff.ingamemessenger.client.Gfx;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.Font;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphicsExtractor; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On a server with the messenger, the game's chat keeps nothing: its lines go to the messenger (the server's channel)
 * and the messenger's feed is drawn where the chat is, at the chat's size and scale.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    /** Opening a picture from the chat keeps the chat in its open state underneath. */
    @Inject(method = "isChatFocused", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$focused(CallbackInfoReturnable<Boolean> cir) {
        if (ChatHooks.viewing()) {
            cir.setReturnValue(true);
        }
    }

    /** Our chat scale on top of Minecraft's. */
    @Inject(method = "getScale()D", at = @At("RETURN"), cancellable = true)
    private void ingamemessenger$scale(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(cir.getReturnValue() * ChatHooks.scaleMul());
    }

    /** No wider than the space next to the chat input. */
    @Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true)
    private void ingamemessenger$width(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ChatHooks.clampWidth(cir.getReturnValue()));
    }

    /** Our chat height setting, capped at the screen height. */
    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$height(CallbackInfoReturnable<Integer> cir) {
        if (ChatHooks.on()) {
            cir.setReturnValue(ChatHooks.chatHeightPx((ChatComponent) (Object) this));
        }
    }

    // Incoming lines go to the messenger

    //? if >=26.1 {
    @Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$incoming(Component content, MessageSignature sig, net.minecraft.client.multiplayer.chat.GuiMessageSource source,
                                          GuiMessageTag tag, CallbackInfo ci) {
        ingamemessenger$take(content, ci);
    }
    //?} else {
    /*@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$incoming(Component content, MessageSignature sig, GuiMessageTag tag, CallbackInfo ci) {
        ingamemessenger$take(content, ci);
    }
    *///?}

    private void ingamemessenger$take(Component content, CallbackInfo ci) {
        if (ChatHooks.take(content)) {
            ci.cancel();
        }
    }

    // Drawing: the feed in place of the game's lines

    //? if >=26.1 {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$draw(GuiGraphicsExtractor g, Font font, int ticks, int mx, int my, ChatComponent.DisplayMode mode, boolean cursor,
                                      CallbackInfo ci) {
        ingamemessenger$feed(g, ticks, mode.foreground, ci);
    }
    //?} else if >=1.21.11 {
    /*@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$draw(GuiGraphicsExtractor g, Font font, int ticks, int mx, int my, boolean focused, boolean cursor, CallbackInfo ci) {
        ingamemessenger$feed(g, ticks, focused, ci);
    }
    *///?} else if >=1.20.5 {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$draw(GuiGraphicsExtractor g, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        ingamemessenger$feed(g, tickCount, focused, ci);
    }
    *///?} else if >=1.20 {
    /*@org.spongepowered.asm.mixin.Shadow
    private boolean isChatFocused() {
        throw new AssertionError();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$draw(GuiGraphicsExtractor g, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        ingamemessenger$feed(g, tickCount, isChatFocused(), ci);
    }
    *///?} else {
    /*@org.spongepowered.asm.mixin.Shadow
    private boolean isChatFocused() {
        throw new AssertionError();
    }

    // before 1.20 the chat is drawn from 48 above the screen's bottom; ours is laid out on the whole screen
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$draw(com.mojang.blaze3d.vertex.PoseStack pose, int tickCount, CallbackInfo ci) {
        int off = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight() - 48;
        pose.pushPose();
        pose.translate(0, -off, 0);
        ingamemessenger$feed(new GuiGraphicsExtractor(pose), tickCount, isChatFocused(), ci);
        pose.popPose();
    }
    *///?}

    private void ingamemessenger$feed(GuiGraphicsExtractor g, int ticks, boolean focused, CallbackInfo ci) {
        if (!ChatHooks.on()) {
            return;
        }
        ChatComponent chat = (ChatComponent) (Object) this;
        if (focused && com.sinuxoff.ingamemessenger.client.ChatInput.searching()) { // search results are shown instead
            ChatHooks.hiddenForSearch(g, chat);
            ci.cancel();
            return;
        }
        ChatHooks.drawShade(g, chat);
        Gfx.push(g);
        ChatHooks.tickShiftY(chat);
        Gfx.move(g, ChatHooks.shiftX(chat), ChatHooks.shiftY());
        ChatHooks.feed(chat, g, ticks, focused);
        Gfx.pop(g);
        ci.cancel();
    }
}
