package com.sinuxoff.ingamemessenger.core.server;

/** A file (voice message or picture) uploaded in chunks. The size comes first, then the chunks in order. */
final class Upload {
    final int id;
    final byte[] data;
    final long started;
    private int filled;

    Upload(int id, int size, long started) {
        this.id = id;
        this.data = new byte[size];
        this.started = started;
    }

    /** False if the chunk belongs to another upload or doesn't fit. */
    boolean accept(int upload, int offset, byte[] piece) {
        if (upload != id || offset < 0 || offset + piece.length > data.length) {
            return false;
        }
        System.arraycopy(piece, 0, data, offset, piece.length);
        filled = Math.max(filled, offset + piece.length);
        return true;
    }

    boolean complete() {
        return filled >= data.length;
    }
}
