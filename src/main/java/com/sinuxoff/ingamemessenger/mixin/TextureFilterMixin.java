package com.sinuxoff.ingamemessenger.mixin;

import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;

/** 1.21.4-1.21.5 turn off texture smoothing on every GUI draw; this keeps it on for the mod's own textures. */
@Mixin(AbstractTexture.class)
public abstract class TextureFilterMixin {
    //? if >=1.21.4 && <1.21.6 {
    /*@org.spongepowered.asm.mixin.injection.Inject(method = "setFilter(Lnet/minecraft/util/TriState;Z)V",
            at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void ingamemessenger$keepSmooth(net.minecraft.util.TriState blur, boolean mipmap,
                                          org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        AbstractTexture self = (AbstractTexture) (Object) this;
        if (com.sinuxoff.ingamemessenger.client.Gfx.isSmooth(self)) {
            self.setFilter(true, mipmap);
            ci.cancel();
        }
    }
    *///?}
}
