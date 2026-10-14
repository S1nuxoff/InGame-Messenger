package com.sinuxoff.ingamemessenger.mixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * 1.21.11+ clips chat text with the scissor box taken when drawing starts. This applies the current box per line, so
 * lines are cut at the edge of the scrolling chat area. Empty on older versions.
 */
//? if >=1.21.11 {
@Mixin(targets = {"net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess"})
public abstract class ChatTextClipMixin {
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "handleMessage", index = 3,
            at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V"))
    private net.minecraft.client.gui.ActiveTextCollector.Parameters ingamemessenger$clip(net.minecraft.client.gui.ActiveTextCollector.Parameters p) {
        var box = com.sinuxoff.ingamemessenger.client.Gfx.currentScissor();
        if (box == null) {
            return p;
        }
        var both = p.scissor() != null ? p.scissor().intersection(box) : box;
        return p.withScissor(both != null ? both : new net.minecraft.client.gui.navigation.ScreenRectangle(0, 0, 0, 0));
    }
}
//?} else {
/*@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent")
public abstract class ChatTextClipMixin {
}
*///?}
