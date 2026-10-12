package com.sinuxoff.ingamemessenger.core.protocol;

/** A packet between client and server. All of them are defined in {@link Packets}. */
public interface Packet {
    PacketType<?> type();

    void write(Wire.Out out);
}
