package com.sinuxoff.ingamemessenger.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.sinuxoff.ingamemessenger.InGameMessenger;

/** System broadcasts (joins, deaths, advancements) get message ids and go into the history. */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"))
    private void ingamemessenger$system(Component message, boolean overlay, CallbackInfo ci) {
        if (!overlay) {
            InGameMessenger.systemMessage(message);
        }
    }
}
