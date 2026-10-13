package com.sinuxoff.ingamemessenger.mixin;

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphics; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.sinuxoff.ingamemessenger.client.ChatInput;

import java.util.List;

/** Command suggestions above the raised chat input, usage and errors in a rounded bubble. */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow
    @Final
    private Screen screen;
    @Shadow
    @Final
    private List<FormattedCharSequence> commandUsage;
    @Shadow
    private int commandUsagePosition;
    @Shadow
    private int commandUsageWidth;
    @Shadow
    @Final
    boolean anchorToBottom;

    @ModifyConstant(method = "showSuggestions", constant = @Constant(intValue = 12))
    private int ingamemessenger$listAbove(int bottomGap) {
        return screen instanceof ChatScreen && com.sinuxoff.ingamemessenger.client.ChatHooks.on() ? screen.height - ChatInput.listBottom(screen.height) : bottomGap;
    }

    /** Usage or error text: one rounded bubble above the input, as wide as the text. */
    //? if >=26.1 {
    /*@Inject(method = "extractUsage", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "renderUsage", at = @At("HEAD"), cancellable = true)
    //?}
    //? if >=1.20 {
    private void ingamemessenger$usage(GuiGraphics g, CallbackInfo ci) {
    //?} else {
    /*private void ingamemessenger$usage(com.mojang.blaze3d.vertex.PoseStack pose, CallbackInfo ci) {
        GuiGraphics g = new GuiGraphics(pose);
    *///?}
        if (anchorToBottom && screen instanceof ChatScreen && com.sinuxoff.ingamemessenger.client.ChatHooks.on()) {
            ci.cancel();
            ChatInput.renderUsage(g, screen, commandUsage, commandUsagePosition, commandUsageWidth);
        }
    }
}
