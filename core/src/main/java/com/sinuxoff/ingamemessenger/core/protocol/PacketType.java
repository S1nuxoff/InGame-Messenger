package com.sinuxoff.ingamemessenger.core.protocol;

import java.util.function.Function;

/** A packet kind: its id (channel "ingamemessenger:id"), direction, class and reader. */
public record PacketType<T extends Packet>(String id, Direction direction, Class<T> type, Function<Wire.In, T> reader) {
    public enum Direction {
        TO_CLIENT, TO_SERVER
    }

    public String channel() {
        return Protocol.NAMESPACE + ":" + id;
    }
}
