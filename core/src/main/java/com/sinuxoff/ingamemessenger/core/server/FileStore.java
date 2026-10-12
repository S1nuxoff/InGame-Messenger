package com.sinuxoff.ingamemessenger.core.server;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Voice messages or pictures in the blob store under random 16-hex-char keys, tracked in the database so old ones can
 * be swept. Keys from clients are validated. Callbacks run on the server thread.
 */
final class FileStore {
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{16}");
    private static final SecureRandom RND = new SecureRandom();

    private final ChatServer server;
    private final String category;
    private final String contentType;

    FileStore(ChatServer server, String category, String contentType) {
        this.server = server;
        this.category = category;
        this.contentType = contentType;
    }

    static boolean isKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    private String path(String key) {
        return category + "/" + key;
    }

    /** Stores the data under a new key; done gets the key, or null if it failed. */
    void put(byte[] data, Consumer<String> done) {
        byte[] k = new byte[8];
        RND.nextBytes(k);
        String key = HexFormat.of().formatHex(k);
        server.storage.io.execute(() -> {
            try {
                server.storage.files.put(path(key), data, contentType);
            } catch (Exception e) {
                server.platform.warn("Can't store a file: " + e);
                server.platform.execute(() -> done.accept(null));
                return;
            }
            server.db("file record", () -> server.storage.chat.addFile(path(key), category, System.currentTimeMillis()));
            server.platform.execute(() -> done.accept(key));
        });
    }

    /** done gets the data, or null if the key is invalid or the file is gone. */
    void get(String key, Consumer<byte[]> done) {
        if (!isKey(key)) {
            done.accept(null);
            return;
        }
        server.storage.io.execute(() -> {
            byte[] data;
            try {
                data = server.storage.files.get(path(key));
            } catch (Exception e) {
                server.platform.warn("Can't read a file: " + e);
                data = null;
            }
            byte[] d = data;
            server.platform.execute(() -> done.accept(d));
        });
    }

    void delete(String key) {
        if (isKey(key)) {
            remove(path(key));
        }
    }

    private void remove(String path) {
        server.storage.io.execute(() -> {
            try {
                server.storage.files.delete(path);
            } catch (Exception e) {
                server.platform.warn("Can't delete a file: " + e);
                return;
            }
            server.db("file record", () -> server.storage.chat.removeFile(path));
        });
    }

    /** Deletes files older than the given days (0 = keep forever). */
    void sweep(int days, long now) {
        if (days <= 0) {
            return;
        }
        server.db("file sweep", () -> {
            var old = server.storage.chat.filesBefore(category, now - days * 86_400_000L);
            old.forEach(this::remove);
            if (!old.isEmpty()) {
                server.platform.info(category + ": deleting " + old.size() + " files older than " + days + " days");
            }
        });
    }
}
