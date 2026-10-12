package com.sinuxoff.ingamemessenger.core.storage;

import java.io.IOException;

/** Where voice messages and pictures are kept: a local folder or an S3-compatible bucket. Calls block. */
public interface BlobStore {
    void put(String key, byte[] data, String contentType) throws IOException;

    /** Null if there's no such file. */
    byte[] get(String key) throws IOException;

    void delete(String key) throws IOException;
}
