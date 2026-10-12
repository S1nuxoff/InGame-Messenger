package com.sinuxoff.ingamemessenger.core.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sinuxoff.ingamemessenger.core.web.Http;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gemini client using the player's own API key. Picks the best available model; free keys have small per-model
 * quotas, so on 429 it falls back to the next model until the first one's quota resets. On 404 the model list is
 * refreshed.
 */
public final class Gemini {
    private static final String API = "https://generativelanguage.googleapis.com/v1beta/";
    private static final String FALLBACK_MODEL = "models/gemini-flash-latest";
    private static final Pattern RETRY_DELAY = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)");
    private static final Pattern VERSION = Pattern.compile("gemini-(\\d+(?:\\.\\d+)?)");
    private static final Pattern NOT_FOR_CHAT = Pattern.compile(".*(image|tts|audio|live|embed|thinking|exp|vision|8b).*");
    /** Longer answers are cut. */
    public static final int MAX_ANSWER = 2000;

    private final Consumer<String> log;
    /** Model -> time until which it's out of quota. */
    private final Map<String, Long> resting = new ConcurrentHashMap<>();
    private volatile HttpResponse<String> lastLimited;
    private volatile List<String> models;

    public Gemini(Consumer<String> log) {
        this.log = log;
    }

    /** Sends a generateContent body to the best model that still has quota. */
    public CompletableFuture<HttpResponse<String>> ask(String key, String body) {
        return ask(key, body, true);
    }

    private CompletableFuture<HttpResponse<String>> ask(String key, String body, boolean retry) {
        return pickModels(key).thenCompose(list -> {
            String m = null;
            long now = System.currentTimeMillis();
            for (String c : list) {
                if (resting.getOrDefault(c, 0L) <= now) {
                    m = c;
                    break;
                }
            }
            if (m == null) { // every model is out of quota: return the last 429
                return CompletableFuture.completedFuture(lastLimited);
            }
            String used = m;
            HttpRequest req = HttpRequest.newBuilder(URI.create(API + used + ":generateContent?key=" + encoded(key)))
                    .timeout(Duration.ofSeconds(60)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            return Http.CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenCompose(resp -> {
                if (resp.statusCode() == 429) {
                    long wait = retryDelay(resp.body());
                    resting.put(used, System.currentTimeMillis() + wait);
                    lastLimited = resp;
                    log.accept("Gemini: " + used + " is out of its quota for " + wait / 1000 + " s, trying the next model");
                    return ask(key, body, retry);
                }
                if (resp.statusCode() == 404 && retry) {
                    models = null;
                    return ask(key, body, false);
                }
                if (resp.statusCode() == 200) {
                    log.accept("Gemini: answered by " + used);
                }
                return CompletableFuture.completedFuture(resp);
            });
        });
    }

    /** Fetched once per key, best first. */
    private CompletableFuture<List<String>> pickModels(String key) {
        List<String> have = models;
        if (have != null && !have.isEmpty()) {
            return CompletableFuture.completedFuture(have);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "models?pageSize=200&key=" + encoded(key)))
                .timeout(Duration.ofSeconds(20)).build();
        return Http.CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(resp -> {
            List<String> out;
            try {
                out = rankModels(resp.body());
            } catch (RuntimeException e) {
                log.accept("Gemini's models not read: " + e);
                out = new ArrayList<>();
            }
            if (out.isEmpty()) {
                out.add(FALLBACK_MODEL);
            }
            log.accept("Gemini: models " + out);
            models = out;
            return out;
        });
    }

    /**
     * Flash chat models only (no image/tts/embedding/experimental), ranked: newer version first, stable before preview,
     * "lite" models last (bigger free quota but weaker).
     */
    static List<String> rankModels(String listJson) {
        Map<String, Double> scored = new HashMap<>();
        for (var el : JsonParser.parseString(listJson).getAsJsonObject().getAsJsonArray("models")) {
            JsonObject o = el.getAsJsonObject();
            String name = o.get("name").getAsString();
            String n = name.toLowerCase();
            boolean generates = o.has("supportedGenerationMethods")
                    && o.getAsJsonArray("supportedGenerationMethods").toString().contains("generateContent");
            if (!generates || !n.contains("gemini") || !n.contains("flash") || NOT_FOR_CHAT.matcher(n).matches()) {
                continue;
            }
            Matcher v = VERSION.matcher(n);
            double ver = v.find() ? Double.parseDouble(v.group(1)) : 0;
            double score = ver * 10 + (n.contains("preview") ? 0 : 5) + (n.endsWith("-latest") ? 1 : 0) - (n.contains("lite") ? 100 : 0);
            scored.put(name, score);
        }
        List<String> out = new ArrayList<>(scored.keySet());
        // ties: newer name first, for a stable order
        out.sort((a, b) -> scored.get(a).equals(scored.get(b)) ? b.compareTo(a) : Double.compare(scored.get(b), scored.get(a)));
        return out;
    }

    /** retryDelay from the 429 body in ms; at least an hour for daily quotas, a minute by default. */
    static long retryDelay(String body) {
        Matcher m = RETRY_DELAY.matcher(body);
        long s = m.find() ? Long.parseLong(m.group(1)) : 60;
        if (body.contains("PerDay")) {
            s = Math.max(s, 3600);
        }
        return s * 1000;
    }

    public static JsonObject turn(String role, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        JsonArray parts = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty("text", text);
        parts.add(p);
        o.add("parts", parts);
        return o;
    }

    /** A user turn with a WAV recording and a text note. */
    public static JsonObject voiceTurn(byte[] wav, String note) {
        JsonObject o = new JsonObject();
        o.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject audio = new JsonObject(), inline = new JsonObject();
        inline.addProperty("mime_type", "audio/wav");
        inline.addProperty("data", java.util.Base64.getEncoder().encodeToString(wav));
        audio.add("inline_data", inline);
        parts.add(audio);
        JsonObject n = new JsonObject();
        n.addProperty("text", note);
        parts.add(n);
        o.add("parts", parts);
        return o;
    }

    public static boolean isVoice(JsonObject turn) {
        JsonArray ps = turn.getAsJsonArray("parts");
        return ps != null && !ps.isEmpty() && ps.get(0).getAsJsonObject().has("inline_data");
    }

    public static String request(String system, List<JsonObject> talk) {
        JsonObject body = new JsonObject();
        body.add("systemInstruction", turn("system", system));
        body.getAsJsonObject("systemInstruction").remove("role");
        JsonArray contents = new JsonArray();
        talk.forEach(contents::add);
        body.add("contents", contents);
        return body.toString();
    }

    /** Answer text (headings made bold, cut at {@link #MAX_ANSWER}), or null. */
    public static String textOf(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            JsonArray parts = o.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("content").getAsJsonArray("parts");
            StringBuilder b = new StringBuilder();
            parts.forEach(p -> {
                if (p.getAsJsonObject().has("text")) {
                    b.append(p.getAsJsonObject().get("text").getAsString());
                }
            });
            String t = b.toString().replaceAll("(?m)^#{1,6}\\s*(.*)$", "**$1**").replaceAll("\n{3,}", "\n\n").strip();
            if (t.length() > MAX_ANSWER) {
                t = t.substring(0, MAX_ANSWER) + "…";
            }
            return t.isEmpty() ? null : t;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String encoded(String key) {
        return URLEncoder.encode(key.trim(), StandardCharsets.UTF_8);
    }
}
