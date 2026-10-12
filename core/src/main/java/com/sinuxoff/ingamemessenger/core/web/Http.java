package com.sinuxoff.ingamemessenger.core.web;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Shared HTTP client with a simple retry helper. */
public final class Http {
    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() {
    }

    /** Retries up to tries times, 1.5 s apart, on connection errors and 5xx responses. */
    public static <T> CompletableFuture<HttpResponse<T>> withRetries(Supplier<CompletableFuture<HttpResponse<T>>> send, int tries) {
        return send.get().handle((resp, err) -> {
            boolean bad = err != null || resp.statusCode() >= 500;
            if (!bad || tries <= 1) {
                return err != null ? CompletableFuture.<HttpResponse<T>>failedFuture(err) : CompletableFuture.completedFuture(resp);
            }
            return CompletableFuture.supplyAsync(() -> null, CompletableFuture.delayedExecutor(1500, TimeUnit.MILLISECONDS))
                    .thenCompose(x -> withRetries(send, tries - 1));
        }).thenCompose(f -> f);
    }
}
