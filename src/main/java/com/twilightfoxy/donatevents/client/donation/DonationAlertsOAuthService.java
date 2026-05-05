package com.twilightfoxy.donatevents.client.donation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.twilightfoxy.donatevents.client.config.ClientDonationConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.util.Util;
import org.slf4j.Logger;

public final class DonationAlertsOAuthService {
    public static final String REQUIRED_SCOPE = "oauth-donation-index";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String AUTHORIZE_URL = "https://www.donationalerts.com/oauth/authorize";
    private static final String TOKEN_URL = "https://www.donationalerts.com/oauth/token";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "Donat Events DonationAlerts OAuth");
        thread.setDaemon(true);
        return thread;
    });

    private DonationAlertsOAuthService() {
    }

    public static CompletableFuture<Result> login(ClientDonationConfig config) {
        config.sanitize();
        if (config.donationAlertsClientId.isBlank()) {
            return CompletableFuture.completedFuture(Result.fail("Client ID is empty"));
        }
        if (config.donationAlertsClientSecret.isBlank()) {
            return CompletableFuture.completedFuture(Result.fail("Client secret is empty"));
        }

        return CompletableFuture.supplyAsync(() -> loginBlocking(config), EXECUTOR);
    }

    private static Result loginBlocking(ClientDonationConfig config) {
        HttpServer server = null;
        try {
            URI redirectUri = URI.create(config.donationAlertsRedirectUri);
            String state = randomState();
            CallbackWaiter waiter = new CallbackWaiter();

            server = HttpServer.create(new InetSocketAddress(resolvePort(redirectUri)), 0);
            HttpServer finalServer = server;
            server.createContext(resolvePath(redirectUri), exchange -> handleCallback(exchange, waiter, state, finalServer));
            server.setExecutor(EXECUTOR);
            server.start();

            Util.getPlatform().openUri(buildAuthorizeUri(config, state));

            Callback callback = waiter.future().get(180, java.util.concurrent.TimeUnit.SECONDS);
            if (callback.error() != null && !callback.error().isBlank()) {
                return Result.fail("DonationAlerts denied access: " + callback.error());
            }
            if (callback.code() == null || callback.code().isBlank()) {
                return Result.fail("DonationAlerts did not return an authorization code");
            }

            return exchangeCode(config, callback.code());
        } catch (java.util.concurrent.TimeoutException exception) {
            return Result.fail("Login timed out");
        } catch (Exception exception) {
            LOGGER.warn("DonationAlerts OAuth login failed", exception);
            return Result.fail("OAuth error: " + exception.getClass().getSimpleName());
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private static void handleCallback(HttpExchange exchange, CallbackWaiter waiter, String expectedState, HttpServer server) throws IOException {
        try {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            String returnedState = params.getOrDefault("state", "");
            if (!returnedState.isBlank() && !returnedState.equals(expectedState)) {
                waiter.complete(new Callback(null, "state_mismatch"));
                writeHtml(exchange, "Donat Events", "Authorization state mismatch. You can close this tab.");
                return;
            }

            Callback callback = new Callback(params.get("code"), params.get("error"));
            waiter.complete(callback);
            if (callback.error() == null || callback.error().isBlank()) {
                writeHtml(exchange, "Donat Events", "DonationAlerts login complete. You can return to Minecraft.");
            } else {
                writeHtml(exchange, "Donat Events", "DonationAlerts login failed: " + escape(callback.error()));
            }
        } finally {
            EXECUTOR.execute(() -> server.stop(0));
        }
    }

    private static Result exchangeCode(ClientDonationConfig config, String code) throws IOException, InterruptedException {
        String body = form(
            "grant_type", "authorization_code",
            "client_id", config.donationAlertsClientId.strip(),
            "client_secret", config.donationAlertsClientSecret.strip(),
            "redirect_uri", config.donationAlertsRedirectUri.strip(),
            "code", code
        );

        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Result.fail("Token exchange failed: HTTP " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String accessToken = getString(json, "access_token");
        if (accessToken.isBlank()) {
            return Result.fail("DonationAlerts did not return access_token");
        }

        config.donationAlertsToken = accessToken;
        config.donationAlertsRefreshToken = getString(json, "refresh_token");
        long expiresIn = json.has("expires_in") && !json.get("expires_in").isJsonNull() ? json.get("expires_in").getAsLong() : 0L;
        config.donationAlertsTokenExpiresAt = expiresIn > 0 ? System.currentTimeMillis() + expiresIn * 1000L : 0L;
        return Result.ok("Login complete. Access token saved.");
    }

    public static Result refreshBlocking(ClientDonationConfig config) {
        if (config.donationAlertsRefreshToken.isBlank()) {
            return Result.fail("Refresh token missing");
        }
        if (config.donationAlertsClientId.isBlank() || config.donationAlertsClientSecret.isBlank()) {
            return Result.fail("Client ID/secret missing");
        }

        try {
            String body = form(
                "grant_type", "refresh_token",
                "client_id", config.donationAlertsClientId.strip(),
                "client_secret", config.donationAlertsClientSecret.strip(),
                "refresh_token", config.donationAlertsRefreshToken.strip()
            );

            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Result.fail("Token refresh failed: HTTP " + response.statusCode());
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String accessToken = getString(json, "access_token");
            if (accessToken.isBlank()) {
                return Result.fail("Token refresh did not return access_token");
            }

            config.donationAlertsToken = accessToken;
            String refreshToken = getString(json, "refresh_token");
            if (!refreshToken.isBlank()) {
                config.donationAlertsRefreshToken = refreshToken;
            }
            long expiresIn = json.has("expires_in") && !json.get("expires_in").isJsonNull() ? json.get("expires_in").getAsLong() : 0L;
            config.donationAlertsTokenExpiresAt = expiresIn > 0 ? System.currentTimeMillis() + expiresIn * 1000L : 0L;
            return Result.ok("Access token refreshed");
        } catch (IOException | RuntimeException exception) {
            return Result.fail("Token refresh error: " + exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.fail("Token refresh interrupted");
        }
    }

    private static URI buildAuthorizeUri(ClientDonationConfig config, String state) {
        return URI.create(AUTHORIZE_URL
            + "?client_id=" + url(config.donationAlertsClientId.strip())
            + "&redirect_uri=" + url(config.donationAlertsRedirectUri.strip())
            + "&response_type=code"
            + "&scope=" + url(REQUIRED_SCOPE)
            + "&state=" + url(state));
    }

    private static int resolvePort(URI uri) {
        return uri.getPort() > 0 ? uri.getPort() : 80;
    }

    private static String resolvePath(URI uri) {
        String path = uri.getPath();
        return path == null || path.isBlank() ? "/" : path;
    }

    private static String form(String... pairs) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < pairs.length; index += 2) {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(url(pairs[index])).append('=').append(url(pairs[index + 1]));
        }
        return builder.toString();
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String randomState() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static void writeHtml(HttpExchange exchange, String title, String message) throws IOException {
        byte[] bytes = ("<!doctype html><html><head><meta charset=\"utf-8\"><title>" + escape(title)
            + "</title></head><body style=\"font-family:sans-serif;background:#181818;color:#eee;padding:32px\"><h1>"
            + escape(title) + "</h1><p>" + escape(message) + "</p></body></html>").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private record Callback(String code, String error) {
    }

    private static final class CallbackWaiter {
        private final CompletableFuture<Callback> future = new CompletableFuture<>();

        CompletableFuture<Callback> future() {
            return future;
        }

        void complete(Callback callback) {
            future.complete(callback);
        }
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
