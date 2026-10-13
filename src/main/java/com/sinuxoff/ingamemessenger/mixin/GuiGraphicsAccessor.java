package com.sinuxoff.ingamemessenger.mixin;

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import com.sinuxoff.ingamemessenger.client.compat.GuiGraphics; // before 1.20: ours, on a PoseStack
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minecraft's GUI render state (1.21.6+) or buffer source, for custom draw calls. */
//? if >=1.20 {
@Mixin(GuiGraphics.class)
//?} else {
/*@Mixin(net.minecraft.client.Minecraft.class) // nothing to reach before 1.20
*///?}
public interface GuiGraphicsAccessor {
    //? if >=1.21.6 {
    /*@Accessor("guiRenderState")
    net.minecraft.client.gui.render.state.GuiRenderState ingamemessenger$state();
    *///?} else if >=1.20 {
    @Accessor("bufferSource")
    net.minecraft.client.renderer.MultiBufferSource.BufferSource ingamemessenger$buffers();
    //?}
}
