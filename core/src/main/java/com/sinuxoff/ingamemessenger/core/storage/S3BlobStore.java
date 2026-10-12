package com.sinuxoff.ingamemessenger.core.storage;

import com.sinuxoff.ingamemessenger.core.web.Http;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * S3-compatible object storage, signed with AWS Signature V4 (no SDK). Path-style URLs ({endpoint}/{bucket}/{key})
 * work with most providers; virtual-hosted style ({bucket}.{endpoint host}) is used when pathStyle is off.
 */
public final class S3BlobStore implements BlobStore {
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StorageConfig cfg;
    private final URI endpoint;

    public S3BlobStore(StorageConfig cfg) {
        this.cfg = cfg;
        String e = cfg.endpoint().trim();
        if (!e.startsWith("http://") && !e.startsWith("https://")) {
            e = "https://" + e;
        }
        this.endpoint = URI.create(e.endsWith("/") ? e.substring(0, e.length() - 1) : e);
    }

    @Override
    public void put(String key, byte[] data, String contentType) throws IOException {
        HttpResponse<byte[]> r = send("PUT", key, data, contentType);
        if (r.statusCode() / 100 != 2) {
            throw new IOException("S3 PUT " + key + ": HTTP " + r.statusCode() + " " + new String(r.body(), StandardCharsets.UTF_8));
        }
    }

    @Override
    public byte[] get(String key) throws IOException {
        HttpResponse<byte[]> r = send("GET", key, null, null);
        if (r.statusCode() == 404) {
            return null;
        }
        if (r.statusCode() / 100 != 2) {
            throw new IOException("S3 GET " + key + ": HTTP " + r.statusCode());
        }
        return r.body();
    }

    @Override
    public void delete(String key) throws IOException {
        HttpResponse<byte[]> r = send("DELETE", key, null, null);
        if (r.statusCode() / 100 != 2 && r.statusCode() != 404) {
            throw new IOException("S3 DELETE " + key + ": HTTP " + r.statusCode());
        }
    }

    private HttpResponse<byte[]> send(String method, String key, byte[] body, String contentType) throws IOException {
        String objectKey = cfg.prefix().isEmpty() ? key : cfg.prefix().replaceAll("/+$", "") + "/" + key;
        String host = cfg.pathStyle() ? endpoint.getAuthority() : cfg.bucket() + "." + endpoint.getAuthority();
        String path = (cfg.pathStyle() ? "/" + encode(cfg.bucket()) : "") + "/" + encodePath(objectKey);
        byte[] payload = body == null ? new byte[0] : body;
        String payloadHash = hex(sha256(payload));
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = AMZ_DATE.format(now), date = DATE.format(now);
        String region = cfg.region().isBlank() ? "auto" : cfg.region().trim();

        String canonical = method + "\n" + path + "\n\n"
                + "host:" + host + "\n" + "x-amz-content-sha256:" + payloadHash + "\n" + "x-amz-date:" + amzDate + "\n\n"
                + "host;x-amz-content-sha256;x-amz-date\n" + payloadHash;
        String scope = date + "/" + region + "/s3/aws4_request";
        String toSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + hex(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
        byte[] k = hmac(("AWS4" + cfg.secretKey()).getBytes(StandardCharsets.UTF_8), date);
        k = hmac(k, region);
        k = hmac(k, "s3");
        k = hmac(k, "aws4_request");
        String signature = hex(hmac(k, toSign));
        String auth = "AWS4-HMAC-SHA256 Credential=" + cfg.accessKey() + "/" + scope
                + ", SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=" + signature;

        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(endpoint.getScheme() + "://" + host + path))
                .timeout(Duration.ofSeconds(60))
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", amzDate)
                .header("Authorization", auth);
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            return Http.CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    private static String encodePath(String key) {
        StringBuilder b = new StringBuilder();
        for (String part : key.split("/", -1)) {
            b.append(b.isEmpty() ? "" : "/").append(encode(part));
        }
        return b.toString();
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }
}
