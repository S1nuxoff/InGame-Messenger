package com.sinuxoff.ingamemessenger.core.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Files in a folder; keys are relative paths like "voice/0123456789abcdef". */
public final class LocalBlobStore implements BlobStore {
    private final Path dir;

    public LocalBlobStore(Path dir) {
        this.dir = dir;
    }

    private Path file(String key) {
        Path f = dir.resolve(key).normalize();
        if (!f.startsWith(dir.normalize())) {
            throw new IllegalArgumentException("bad key: " + key);
        }
        return f;
    }

    @Override
    public void put(String key, byte[] data, String contentType) throws IOException {
        Path f = file(key);
        Files.createDirectories(f.getParent());
        Files.write(f, data);
    }

    @Override
    public byte[] get(String key) throws IOException {
        Path f = file(key);
        return Files.exists(f) ? Files.readAllBytes(f) : null;
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(file(key));
    }
}
