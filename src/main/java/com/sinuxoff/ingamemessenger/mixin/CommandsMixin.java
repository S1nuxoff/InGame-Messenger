package com.sinuxoff.ingamemessenger.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.sinuxoff.ingamemessenger.InGameMessenger;

/** /msg, /tell, /me and the like from a muted player stop here, whoever registered them (the game, Essentials...). */
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Inject(method = "performPrefixedCommand", at = @At("HEAD"), cancellable = true)
    //? if >=1.20.3 {
    private void ingamemessenger$muted(CommandSourceStack source, String command, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (source.getPlayer() != null && !InGameMessenger.mayCommand(source.getPlayer(), command)) {
            ci.cancel();
        }
    }
    //?} else {
    /*private void ingamemessenger$muted(CommandSourceStack source, String command,
                                        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> ci) {
        if (source.getPlayer() != null && !InGameMessenger.mayCommand(source.getPlayer(), command)) {
            ci.setReturnValue(0);
        }
    }
    *///?}
}
