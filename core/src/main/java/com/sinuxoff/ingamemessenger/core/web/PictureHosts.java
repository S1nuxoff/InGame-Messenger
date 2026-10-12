package com.sinuxoff.ingamemessenger.core.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * External picture hosts. ImgBB uploads go from the server (needs a key); Catbox uploads go from the client, since
 * Catbox blocks data center IPs.
 */
public final class PictureHosts {
    public static final Pattern CATBOX_URL = Pattern.compile("https://files\\.catbox\\.moe/[a-z0-9]{4,12}\\.(jpg|jpeg|png)");
    /** Clients that can't reach ImgBB get the picture through the server. */
    public static final String IMGBB_PREFIX = "https://i.ibb.co/";

    private PictureHosts() {
    }

    /** keepDays 0 = forever (ImgBB allows at most 180 days). Completes with null on failure. */
    public static CompletableFuture<String> toImgbb(byte[] picture, String key, int keepDays) {
        StringBuilder form = new StringBuilder("image=")
                .append(URLEncoder.encode(Base64.getEncoder().encodeToString(picture), StandardCharsets.UTF_8));
        if (keepDays > 0) {
            form.append("&expiration=").append(Math.min(15_552_000L, Math.max(60L, keepDays * 86_400L)));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.imgbb.com/1/upload?key=" + URLEncoder.encode(key.trim(), StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString())).build();
        return Http.withRetries(() -> Http.CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()), 4).handle((resp, err) -> {
            if (err != null || resp.statusCode() != 200) {
                return null;
            }
            try {
                JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
                String url = o.getAsJsonObject("data").get("url").getAsString();
                return url.startsWith("https://") && url.length() <= 400 && !url.contains(" ") ? url : null;
            } catch (RuntimeException e) {
                return null;
            }
        });
    }

    /** Blocking, 3 tries. */
    public static String toCatbox(byte[] jpeg) throws Exception {
        String b = "----InGameMessenger" + Long.toHexString(System.nanoTime());
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"fileToUpload\"; filename=\"picture.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(jpeg);
        body.write(("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://catbox.moe/user/api.php")).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + b)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        Exception last = null;
        for (int i = 0; i < 3; i++) {
            try {
                HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                String url = resp.body().trim();
                if (resp.statusCode() == 200 && CATBOX_URL.matcher(url).matches()) {
                    return url;
                }
                last = new IllegalStateException("Catbox: HTTP " + resp.statusCode() + " " + url);
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(1000);
        }
        throw last;
    }

    /** Blocking. Fails if the file is bigger than maxBytes. */
    public static byte[] download(String url, int maxBytes) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build();
        HttpResponse<java.io.InputStream> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (java.io.InputStream in = resp.body()) {
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + resp.statusCode());
            }
            byte[] b = in.readNBytes(maxBytes + 1);
            if (b.length > maxBytes) {
                throw new IllegalStateException("too big");
            }
            return b;
        }
    }
}
