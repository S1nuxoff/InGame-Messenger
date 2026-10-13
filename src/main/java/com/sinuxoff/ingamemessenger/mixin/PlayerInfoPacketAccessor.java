package com.sinuxoff.ingamemessenger.mixin;

//? if >=1.19.3 {
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Lets the test bots be added to the tab list. */
//? if >=1.19.3 {
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerInfoPacketAccessor {
    @Mutable
    @Accessor("entries")
    void ingamemessenger$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
//?} else {
/*@Mixin(net.minecraft.server.MinecraftServer.class) // the bots have no tab list entries before 1.19.3
public interface PlayerInfoPacketAccessor {
}
*///?}
