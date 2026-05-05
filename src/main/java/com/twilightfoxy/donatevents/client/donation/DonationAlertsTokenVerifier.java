package com.twilightfoxy.donatevents.client.donation;

import com.google.gson.JsonParser;
import com.twilightfoxy.donatevents.client.config.ClientDonationConfig;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DonationAlertsTokenVerifier {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Donat Events DonationAlerts Token Check");
        thread.setDaemon(true);
        return thread;
    });

    private DonationAlertsTokenVerifier() {
    }

    public static CompletableFuture<Result> verify(ClientDonationConfig config) {
        String token = config.donationAlertsToken.strip();
        String apiUrl = config.donationAlertsApiUrl;
        if (token.isBlank()) {
            return CompletableFuture.completedFuture(Result.emptyToken());
        }

        return CompletableFuture.supplyAsync(() -> verifyNow(apiUrl, token), EXECUTOR);
    }

    private static Result verifyNow(String apiUrl, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(buildUri(apiUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                JsonParser.parseString(response.body()).getAsJsonObject();
                return Result.ok();
            }
            if (statusCode == 401) {
                return Result.fail("HTTP 401: token is invalid or not an OAuth Bearer token");
            }
            if (statusCode == 403) {
                return Result.fail("HTTP 403: token is valid, but probably lacks oauth-donation-index scope");
            }
            if (statusCode == 429) {
                return Result.fail("HTTP 429: DonationAlerts rate limit");
            }
            return Result.fail("HTTP " + statusCode + ": DonationAlerts rejected the request");
        } catch (IOException | RuntimeException exception) {
            return Result.fail("Network/API error: " + exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.fail("Token check interrupted");
        }
    }

    private static URI buildUri(String rawUrl) {
        String separator = rawUrl.contains("?") ? "&" : "?";
        return URI.create(rawUrl + separator + "limit=" + URLEncoder.encode("1", StandardCharsets.UTF_8));
    }

    public record Result(boolean success, String message) {
        public static Result ok() {
            return new Result(true, "Token OK: donations API is readable");
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }

        public static Result emptyToken() {
            return fail("Token is empty");
        }
    }
}
