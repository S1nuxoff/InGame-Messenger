package com.sinuxoff.ingamemessenger.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.1 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sinuxoff.ingamemessenger.client.ChatInput;
import com.sinuxoff.ingamemessenger.client.Smooth;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

/** The chat input in the smooth font: its text, and the caret and scrolling measured in it. */
@Mixin(EditBox.class)
public abstract class EditBoxSmoothMixin {
    //? if >=26.1 {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int ingamemessenger$width(Font font, String text, Operation<Integer> op) {
        return ChatInput.smoothInput((EditBox) (Object) this) ? Smooth.width(font, text) : op.call(font, text);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;plainSubstrByWidth(Ljava/lang/String;I)Ljava/lang/String;"))
    private String ingamemessenger$cut(Font font, String text, int w, Operation<String> op) {
        return ChatInput.smoothInput((EditBox) (Object) this) ? Smooth.cut(font, text, w) : op.call(font, text, w);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;plainSubstrByWidth(Ljava/lang/String;IZ)Ljava/lang/String;"))
    private String ingamemessenger$cutEnd(Font font, String text, int w, boolean fromEnd, Operation<String> op) {
        return ChatInput.smoothInput((EditBox) (Object) this) ? Smooth.cut(font, text, w, fromEnd) : op.call(font, text, w, fromEnd);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/util/FormattedCharSequence;)I"))
    private int ingamemessenger$widthSeq(Font font, FormattedCharSequence text, Operation<Integer> op) {
        return op.call(font, ChatInput.smoothInput((EditBox) (Object) this) ? Smooth.seq(text) : text);
    }

    @Inject(method = "applyFormat", at = @At("RETURN"), cancellable = true)
    private void ingamemessenger$format(String text, int offset, CallbackInfoReturnable<FormattedCharSequence> cir) {
        if (ChatInput.smoothInput((EditBox) (Object) this)) {
            cir.setReturnValue(Smooth.seq(cir.getReturnValue()));
        }
    }
    //?}
}
