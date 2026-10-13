package com.sinuxoff.ingamemessenger.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** In the server chat the input takes commands without the "/", like a command block's. */
@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsAccessor {
    @Accessor("commandsOnly")
    boolean ingamemessenger$commandsOnly();

    @Mutable
    @Accessor("commandsOnly")
    void ingamemessenger$setCommandsOnly(boolean on);
}
