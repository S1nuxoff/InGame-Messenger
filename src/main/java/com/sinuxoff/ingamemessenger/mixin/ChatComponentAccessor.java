package com.sinuxoff.ingamemessenger.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> ingamemessenger$lines();

    @Accessor("chatScrollbarPos")
    int ingamemessenger$scroll();

    @Accessor("chatScrollbarPos")
    void ingamemessenger$setScroll(int pos);

    @Accessor("allMessages")
    List<GuiMessage> ingamemessenger$allMessages();

    @Invoker("getScale")
    double ingamemessenger$scale();

    @Invoker("getWidth")
    int ingamemessenger$width();
}
