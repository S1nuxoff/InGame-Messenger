package com.sinuxoff.ingamemessenger.mixin;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minecraft's GUI render state (1.21.6+) or buffer source, for custom draw calls. */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
    //? if >=1.21.6 {
    /*@Accessor("guiRenderState")
    net.minecraft.client.gui.render.state.GuiRenderState ingamemessenger$state();
    *///?} else {
    @Accessor("bufferSource")
    net.minecraft.client.renderer.MultiBufferSource.BufferSource ingamemessenger$buffers();
    //?}
}
