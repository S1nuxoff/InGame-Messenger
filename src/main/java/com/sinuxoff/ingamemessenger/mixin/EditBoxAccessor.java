package com.sinuxoff.ingamemessenger.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Scroll offset of a text field, to draw emoji at the right place in the chat input. */
@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("displayPos")
    int ingamemessenger$displayPos();
}
