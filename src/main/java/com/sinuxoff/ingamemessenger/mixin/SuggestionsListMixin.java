package com.sinuxoff.ingamemessenger.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphics; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.sinuxoff.ingamemessenger.client.ChatInput;

/** Command suggestion list on a glass panel: no black rows, the selected item on an accent bar. */
@Mixin(targets = "net.minecraft.client.gui.components.CommandSuggestions$SuggestionsList")
public abstract class SuggestionsListMixin {
    @Shadow
    @Final
    private Rect2i rect;

    private static boolean ingamemessenger$chat() {
        return Minecraft.getInstance().screen instanceof ChatScreen && com.sinuxoff.ingamemessenger.client.ChatHooks.on();
    }

    //? if >=26.1 {
    /*@Inject(method = "extractRenderState", at = @At("HEAD"))
    *///?} else {
    @Inject(method = "render", at = @At("HEAD"))
    //?}
    //? if >=1.20 {
    private void ingamemessenger$panel(GuiGraphics g, int mx, int my, CallbackInfo ci) {
    //?} else {
    /*private void ingamemessenger$panel(com.mojang.blaze3d.vertex.PoseStack pose, int mx, int my, CallbackInfo ci) {
        GuiGraphics g = new GuiGraphics(pose);
    *///?}
        if (ingamemessenger$chat()) {
            ChatInput.glassBox(g, rect.getX() - 4, rect.getY() - 4, rect.getWidth() + 8, rect.getHeight() + 7, 0xF4);
        }
    }

    //? if >=26.1 {
    /*@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    *///?} else if >=1.20 {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    //?} else {
    /*@WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiComponent;fill(Lcom/mojang/blaze3d/vertex/PoseStack;IIIII)V"))
    *///?}
    //? if >=1.20 {
    private void ingamemessenger$rows(GuiGraphics g, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ingamemessenger$chat() || color != 0xD0000000) {
            op.call(g, x1, y1, x2, y2, color);
        }
    }
    //?} else {
    /*private void ingamemessenger$rows(com.mojang.blaze3d.vertex.PoseStack pose, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ingamemessenger$chat() || color != 0xD0000000) {
            op.call(pose, x1, y1, x2, y2, color);
        }
    }
    *///?}

    //? if >=26.1 {
    /*@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void ingamemessenger$row(GuiGraphics g, Font font, String text, int x, int y, int color, Operation<Void> op) {
        op.call(g, font, text, x, y, ingamemessenger$rowColor(g, y, color));
    }
    *///?} else if >=1.21.6 {
    /*@WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void ingamemessenger$row(GuiGraphics g, Font font, String text, int x, int y, int color, Operation<Void> op) {
        op.call(g, font, text, x, y, ingamemessenger$rowColor(g, y, color));
    }
    *///?} else if >=1.20 {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int ingamemessenger$row(GuiGraphics g, Font font, String text, int x, int y, int color, Operation<Integer> op) {
        return op.call(g, font, text, x, y, ingamemessenger$rowColor(g, y, color));
    }
    //?} else {
    /*@WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I"))
    private int ingamemessenger$row(Font font, com.mojang.blaze3d.vertex.PoseStack pose, String text, float x, float y, int color, Operation<Integer> op) {
        return op.call(font, pose, text, x, y, ingamemessenger$rowColor(new GuiGraphics(pose), (int) y, color));
    }
    *///?}

    /** Selected item on an accent bar in white, the others light grey. */
    private int ingamemessenger$rowColor(GuiGraphics g, int y, int color) {
        if (!ingamemessenger$chat()) {
            return color;
        }
        if (color == -256) {
            ChatInput.highlight(g, rect.getX() - 2, y - 2, rect.getWidth() + 4, 12);
            return 0xFFFFFFFF;
        }
        return 0xFFB8BECC;
    }
}
