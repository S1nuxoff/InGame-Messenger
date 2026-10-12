package com.sinuxoff.ingamemessenger.core.storage;

/**
 * [storage] in the server config. type is "local" (the world folder) or "s3" (any S3-compatible service: AWS S3,
 * Cloudflare R2, Backblaze B2, MinIO, Wasabi...).
 */
public record StorageConfig(String type, String endpoint, String region, String bucket, String accessKey, String secretKey,
                            boolean pathStyle, String prefix) {
    public static StorageConfig local() {
        return new StorageConfig("local", "", "auto", "", "", "", true, "");
    }

    public boolean s3() {
        return type.trim().equalsIgnoreCase("s3");
    }
}
