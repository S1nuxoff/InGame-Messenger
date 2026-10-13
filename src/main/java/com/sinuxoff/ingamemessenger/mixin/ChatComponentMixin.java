package com.sinuxoff.ingamemessenger.mixin;

import com.sinuxoff.ingamemessenger.client.Compat;
import com.sinuxoff.ingamemessenger.client.Gfx;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphics; // before 1.20: ours, on a PoseStack
*///?}
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.StringSplitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.sinuxoff.ingamemessenger.client.ChatHooks;

import java.util.List;

/**
 * The messenger look of the chat: incoming player messages are rebuilt (name above, face beside), bubbles and faces
 * are drawn before the text, the text is shifted right of the face (drawing, clicks, wrapping), stickers after it.
 * <p>
 * Minecraft has drawn the chat in four ways: a single render method (up to 1.21.5), lines drawn in lambdas
 * (1.21.6-1.21.10), and a "graphics access" object (1.21.11, renamed in 26.x). Each version has its own hooks below.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    // Same in every version

    /** Opening a picture from the chat keeps the chat in its open state underneath. */
    @Inject(method = "isChatFocused", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$focused(CallbackInfoReturnable<Boolean> cir) {
        if (ChatHooks.viewing()) {
            cir.setReturnValue(true);
        }
    }

    /** ...and keeps the scroll position. */
    @Inject(method = "resetChatScroll", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$keepScroll(CallbackInfo ci) {
        if (ChatHooks.takeKeepScroll()) {
            ci.cancel();
        }
    }

    /** Keeps far more lines than vanilla's 100, for the history. */
    //? if >=1.20.2 {
    @org.spongepowered.asm.mixin.injection.ModifyConstant(method = "addMessageToQueue",
            constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 100))
    private int ingamemessenger$keepMessages(int max) {
        return ChatHooks.keptMessages(max);
    }

    @org.spongepowered.asm.mixin.injection.ModifyConstant(method = "addMessageToDisplayQueue",
            constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 100))
    private int ingamemessenger$keepLines(int max) {
        return ChatHooks.keptLines(max);
    }
    //?} else {
    /*@org.spongepowered.asm.mixin.injection.ModifyConstant(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
            constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 100, ordinal = 0))
    private int ingamemessenger$keepLines(int max) {
        return ChatHooks.keptLines(max);
    }

    @org.spongepowered.asm.mixin.injection.ModifyConstant(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
            constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 100, ordinal = 1))
    private int ingamemessenger$keepMessages(int max) {
        return ChatHooks.keptMessages(max);
    }
    *///?}

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

    // Incoming messages

    //? if >=26.1 {
    /*@Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$incoming(Component content, MessageSignature sig, net.minecraft.client.multiplayer.chat.GuiMessageSource source,
                                   GuiMessageTag tag, CallbackInfo ci) {
        ingamemessenger$transform(content, sig, tag, ci);
    }
    *///?} else {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$incoming(Component content, MessageSignature sig, GuiMessageTag tag, CallbackInfo ci) {
        ingamemessenger$transform(content, sig, tag, ci);
    }
    //?}

    private void ingamemessenger$transform(Component content, MessageSignature sig, GuiMessageTag tag, CallbackInfo ci) {
        if (ChatHooks.transform((ChatComponent) (Object) this, content, sig, tag)) {
            ci.cancel();
        }
    }

    // Drawing: shade and bubbles before, the rest after

    //? if >=26.1 {
    /*@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$bubbles(GuiGraphics g, Font font, int ticks, int mx, int my, ChatComponent.DisplayMode mode, boolean cursor,
                                  CallbackInfo ci) {
        ingamemessenger$before(g, ticks, mode.foreground, ci);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("RETURN"))
    private void ingamemessenger$render(GuiGraphics g, Font font, int ticks, int mx, int my, ChatComponent.DisplayMode mode, boolean cursor,
                                 CallbackInfo ci) {
        ingamemessenger$after(g, ticks, mode.foreground);
    }
    *///?} else if >=1.21.11 {
    /*@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$bubbles(GuiGraphics g, Font font, int ticks, int mx, int my, boolean focused, boolean cursor, CallbackInfo ci) {
        ingamemessenger$before(g, ticks, focused, ci);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("RETURN"))
    private void ingamemessenger$render(GuiGraphics g, Font font, int ticks, int mx, int my, boolean focused, boolean cursor, CallbackInfo ci) {
        ingamemessenger$after(g, ticks, focused);
    }
    *///?} else if >=1.20.2 {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$bubbles(GuiGraphics g, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        ingamemessenger$before(g, tickCount, focused, ci);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ingamemessenger$render(GuiGraphics g, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        ingamemessenger$after(g, tickCount, focused);
    }
    //?} else if >=1.20 {
    /*@org.spongepowered.asm.mixin.Shadow
    private boolean isChatFocused() {
        throw new AssertionError();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$bubbles(GuiGraphics g, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        ingamemessenger$before(g, tickCount, isChatFocused(), ci);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ingamemessenger$render(GuiGraphics g, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        ingamemessenger$after(g, tickCount, isChatFocused());
    }
    *///?} else {
    /*@org.spongepowered.asm.mixin.Shadow
    private boolean isChatFocused() {
        throw new AssertionError();
    }

    // before 1.20 the chat is drawn from 48 above the screen's bottom; ours is laid out on the whole screen
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ingamemessenger$bubbles(com.mojang.blaze3d.vertex.PoseStack pose, int tickCount, CallbackInfo ci) {
        int off = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight() - 48;
        pose.pushPose();
        pose.translate(0, -off, 0);
        ingamemessenger$before(new GuiGraphics(pose), tickCount, isChatFocused(), ci);
        if (ci.isCancelled()) {
            pose.popPose();
            return;
        }
        pose.pushPose();
        pose.translate(0, off, 0);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ingamemessenger$render(com.mojang.blaze3d.vertex.PoseStack pose, int tickCount, CallbackInfo ci) {
        pose.popPose();
        ingamemessenger$after(new GuiGraphics(pose), tickCount, isChatFocused());
        pose.popPose();
    }
    *///?}

    private void ingamemessenger$before(GuiGraphics g, int ticks, boolean focused, CallbackInfo ci) {
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
        // the whole chat is moved right when the player wants it there (undone afterwards)
        Gfx.push(g);
        ChatHooks.tickShiftY(chat);
        Gfx.move(g, ChatHooks.shiftX(chat), ChatHooks.shiftY());
        ChatHooks.renderBubbles(chat, g, ticks, focused);
    }

    private void ingamemessenger$after(GuiGraphics g, int ticks, boolean focused) {
        if (!ChatHooks.on()) {
            return;
        }
        ChatHooks.render((ChatComponent) (Object) this, g, ticks, focused);
        Gfx.pop(g);
    }

    // Lines: text position, clicks, wrapping

    //? if >=1.21.11 {
    /*/^* Minecraft draws the chat and finds clicks through a "graphics access"; ChatLines wraps it. ^/
    //? if >=26.1 {
    /^@ModifyArg(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"),
            index = 0)
    ^///?} else {
    /^@ModifyArg(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V"),
            index = 0)
    ^///?}
    private ChatComponent.ChatGraphicsAccess ingamemessenger$drawing(ChatComponent.ChatGraphicsAccess access, @Local(argsOnly = true) GuiGraphics g) {
        if (!ChatHooks.on()) {
            return access;
        }
        return new com.sinuxoff.ingamemessenger.client.ChatLines((ChatComponent) (Object) this, access, g);
    }

    //? if >=26.1 {
    /^@ModifyArg(method = "captureClickableText",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"),
            index = 0)
    ^///?} else {
    /^@ModifyArg(method = "captureClickableText",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V"),
            index = 0)
    ^///?}
    private ChatComponent.ChatGraphicsAccess ingamemessenger$clicks(ChatComponent.ChatGraphicsAccess access) {
        if (!ChatHooks.on()) {
            return access;
        }
        return new com.sinuxoff.ingamemessenger.client.ChatLines((ChatComponent) (Object) this, access, null);
    }

    /^* While scrolling smoothly, one extra line is drawn at the top, partly hidden. ^/
    @WrapOperation(method = "forEachLine", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;getLinesPerPage()I"))
    private int ingamemessenger$moreLines(ChatComponent chat, Operation<Integer> op) {
        return op.call(chat) + ChatHooks.extraLines();
    }

    /^* Messenger messages wrap narrower, leaving room for the face. ^/
    @WrapOperation(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
    private List<FormattedCharSequence> ingamemessenger$wrap(GuiMessage msg, Font font, int width, Operation<List<FormattedCharSequence>> op) {
        if (!ChatHooks.shifted(msg.tag())) {
            return op.call(msg, font, width);
        }
        return ingamemessenger$messengerLines(op.call(msg, font, width - ChatHooks.AV - 40), msg.tag());
    }
    *///?} else {
    /** Mouse position mapped into the shifted chat. */
    @Inject(method = "screenToChatX", at = @At("RETURN"), cancellable = true)
    private void ingamemessenger$mouseX(double x, CallbackInfoReturnable<Double> cir) {
        ChatComponent chat = (ChatComponent) (Object) this;
        cir.setReturnValue(cir.getReturnValue() - ChatHooks.shiftX(chat) / Compat.chatScale(chat));
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "screenToChatY", at = @At("HEAD"), argsOnly = true)
    private double ingamemessenger$mouseY(double y) {
        return y - ChatHooks.shiftY();
    }

    /** While scrolling smoothly, one extra line is drawn at the top, partly hidden. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;getLinesPerPage()I"))
    private int ingamemessenger$moreLines(ChatComponent chat, Operation<Integer> op) {
        return op.call(chat) + ChatHooks.extraLines();
    }

    /** Clicks on links land where the shifted text really is. */
    @WrapOperation(method = "getClickedComponentStyleAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/StringSplitter;componentStyleAtWidth(Lnet/minecraft/util/FormattedCharSequence;I)Lnet/minecraft/network/chat/Style;"))
    private Style ingamemessenger$click(StringSplitter splitter, FormattedCharSequence text, int x, Operation<Style> op,
                                 @Local GuiMessage.Line line) {
        return op.call(splitter, text, ChatHooks.shifted(line.tag()) ? x - ChatHooks.textX(line.tag()) : x);
    }

    /** Messenger messages wrap narrower, leaving room for the face. */
    //? if >=1.20.2 {
    @WrapOperation(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"))
    private List<FormattedCharSequence> ingamemessenger$wrap(FormattedText text, int width, Font font, Operation<List<FormattedCharSequence>> op,
                                                     @Local(argsOnly = true) GuiMessage msg) {
        GuiMessageTag tag = msg.tag();
    //?} else {
    /*@WrapOperation(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"))
    private List<FormattedCharSequence> ingamemessenger$wrap(FormattedText text, int width, Font font, Operation<List<FormattedCharSequence>> op,
                                                     @Local(argsOnly = true) GuiMessageTag tag) {
    *///?}
        if (!ChatHooks.shifted(tag)) {
            return op.call(text, width, font);
        }
        return ingamemessenger$messengerLines(op.call(text, width - ChatHooks.AV - 40, font), tag);
    }

    //? if >=1.21.6 {
    /*/^* Vanilla's dark row and tag bar behind each line are replaced by the bubbles. ^/
    // lambda names: NeoForge's, then Fabric's
    @WrapOperation(method = {"lambda$render$0", "method_71992"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void ingamemessenger$rows(GuiGraphics g, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(g, x1, y1, x2, y2, color);
        }
    }

    /^* No background for the pending-message line, and no square scroll bar (we draw a slim one). ^/
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void ingamemessenger$scrollBar(GuiGraphics g, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(g, x1, y1, x2, y2, color);
        }
    }

    /^* Messenger message text, right of the face. ^/
    @WrapOperation(method = {"lambda$render$1", "method_71991"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"))
    private void ingamemessenger$text(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color, Operation<Void> op,
                               @Local(argsOnly = true) GuiMessage.Line line) {
        ingamemessenger$line(g, font, text, x, y, color, line, (dx, c) -> op.call(g, font, text, dx, y, c));
    }
    *///?} else if >=1.20 {
    /** Vanilla's dark row is replaced by the bubbles. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0))
    private void ingamemessenger$row(GuiGraphics g, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(g, x1, y1, x2, y2, color);
        }
    }

    /** No square scroll bar, we draw a slim one. */
    //? if >=1.20.2 {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIIII)V"))
    private void ingamemessenger$scrollBar(GuiGraphics g, int x1, int y1, int x2, int y2, int z, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(g, x1, y1, x2, y2, z, color);
        }
    }
    //?} else {
    /*@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 3))
    private void ingamemessenger$scrollBar(GuiGraphics g, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(g, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 4))
    private void ingamemessenger$scrollBarEdge(GuiGraphics g, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(g, x1, y1, x2, y2, color);
        }
    }
    *///?}

    /** No tag bar next to bubbles. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 1))
    private void ingamemessenger$tagBar(GuiGraphics g, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(g, x1, y1, x2, y2, color);
        }
    }

    /** Messenger message text, right of the face. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int ingamemessenger$text(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color, Operation<Integer> op,
                              @Local GuiMessage.Line line) {
        ingamemessenger$line(g, font, text, x, y, color, line, (dx, c) -> op.call(g, font, text, dx, y, c));
        return 0;
    }
    //?} else {
    /*// before 1.20 the chat draws on a PoseStack: its fills are GuiComponent's, its text Font.drawShadow
    private static final String FILL = "Lnet/minecraft/client/gui/components/ChatComponent;fill(Lcom/mojang/blaze3d/vertex/PoseStack;IIIII)V";

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = FILL, ordinal = 0))
    private void ingamemessenger$row(com.mojang.blaze3d.vertex.PoseStack pose, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(pose, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = FILL, ordinal = 1))
    private void ingamemessenger$tagBar(com.mojang.blaze3d.vertex.PoseStack pose, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(pose, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = FILL, ordinal = 3))
    private void ingamemessenger$scrollBar(com.mojang.blaze3d.vertex.PoseStack pose, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(pose, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = FILL, ordinal = 4))
    private void ingamemessenger$scrollBarEdge(com.mojang.blaze3d.vertex.PoseStack pose, int x1, int y1, int x2, int y2, int color, Operation<Void> op) {
        if (!ChatHooks.on()) {
            op.call(pose, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/util/FormattedCharSequence;FFI)I"))
    private int ingamemessenger$text(Font font, com.mojang.blaze3d.vertex.PoseStack pose, FormattedCharSequence text, float x, float y, int color,
                              Operation<Integer> op, @Local GuiMessage.Line line) {
        ingamemessenger$line(new GuiGraphics(pose), font, text, (int) x, (int) y, color, line, (dx, c) -> op.call(font, pose, text, (float) dx, y, c));
        return 0;
    }
    *///?}

    /** Draws a line's text: faded in when new, right of the face, moving with the chat. */
    private static void ingamemessenger$line(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color, GuiMessage.Line line,
                                      java.util.function.BiConsumer<Integer, Integer> draw) {
        if (!ChatHooks.on()) {
            draw.accept(x, color);
            return;
        }
        int alpha = Math.round((color >>> 24) * ChatHooks.alphaOf(line.tag()));
        if (alpha <= 3) {
            return;
        }
        int dx = ChatHooks.shifted(line.tag()) ? x + ChatHooks.textX(line.tag()) : x, c = alpha << 24 | color & 0xFFFFFF;
        float slide = ChatHooks.slide(), a = alpha / 255f;
        if (slide == 0) {
            draw.accept(dx, c);
            ChatHooks.drawInlineEmoji(g, font, text, dx, y, a);
            return;
        }
        ChatHooks.clipped(g, () -> {
            Gfx.push(g);
            Gfx.move(g, 0f, slide);
            draw.accept(dx, c);
            ChatHooks.drawInlineEmoji(g, font, text, dx, y, a);
            Gfx.pop(g);
            return 0;
        });
    }
    //?}

    /** Wrapped lines of a messenger message: no leading space on continuation lines, a reaction row if needed. */
    private static List<FormattedCharSequence> ingamemessenger$messengerLines(List<FormattedCharSequence> wrapped, GuiMessageTag tag) {
        List<FormattedCharSequence> lines = new java.util.ArrayList<>(wrapped);
        for (int i = 1; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            lines.set(i, sink -> {
                boolean[] first = {true};
                return line.accept((index, style, cp) -> {
                    if (first[0]) {
                        first[0] = false;
                        if (cp == ' ') {
                            return true;
                        }
                    }
                    return sink.accept(index, style, cp);
                });
            });
        }
        if (ChatHooks.reactionRow(tag)) { // above the spacer line
            lines.add(Math.max(0, lines.size() - 1), FormattedCharSequence.EMPTY);
        }
        return lines;
    }
}
