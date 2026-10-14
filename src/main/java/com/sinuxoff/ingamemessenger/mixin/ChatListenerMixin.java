package com.sinuxoff.ingamemessenger.mixin;

import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.sinuxoff.ingamemessenger.client.ChatHooks;

/**
 * A player's chat line ("chat" type) comes as our message from the server, so the game's own copy isn't shown. It
 * still counts as shown, so the game acknowledges it to the server as usual. /msg, /me, /say have other types.
 */
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
    //? if >=1.19.3 {
    @Inject(method = "showMessageToPlayer", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$ownLine(ChatType.Bound type, net.minecraft.network.chat.PlayerChatMessage message, Component decorated,
                                         com.mojang.authlib.GameProfile sender, boolean onlySecure, java.time.Instant received,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (ChatHooks.ownChatLine(type)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "handleDisguisedChatMessage", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$ownDisguised(Component message, ChatType.Bound type, CallbackInfo ci) {
        if (ChatHooks.ownChatLine(type)) {
            ci.cancel();
        }
    }
    //?} else {
    /*// 1.19.2: the sender is a PlayerInfo, and lines without a known sender take their own way
    @Inject(method = "showMessageToPlayer", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$ownLine(ChatType.Bound type, net.minecraft.network.chat.PlayerChatMessage message, Component decorated,
                                         net.minecraft.client.multiplayer.PlayerInfo sender, boolean onlySecure, java.time.Instant received,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (ChatHooks.ownChatLine(type)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "processNonPlayerChatMessage", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$ownUnknown(ChatType.Bound type, net.minecraft.network.chat.PlayerChatMessage message, Component decorated,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (ChatHooks.ownChatLine(type)) {
            cir.setReturnValue(true);
        }
    }
    *///?}
}
