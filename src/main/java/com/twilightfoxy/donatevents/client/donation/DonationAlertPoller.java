package com.twilightfoxy.donatevents.client.donation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.twilightfoxy.donatevents.client.config.ClientConfigStore;
import com.twilightfoxy.donatevents.client.config.ClientDonationConfig;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

public final class DonationAlertPoller {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PAGE_LIMIT = 25;
    private static final long REFRESH_GRACE_MILLIS = Duration.ofMinutes(5).toMillis();

    private final ClientConfigStore configStore;
    private final DonationEventHandler eventHandler;
    private final DonationAlertsConnectionStatus status;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Donat Events DonationAlerts Poller");
        thread.setDaemon(true);
        return thread;
    });

    private long ticksUntilNextPoll;
    private boolean requestInFlight;
    private boolean backfillDoneThisSession;

    public DonationAlertPoller(ClientConfigStore configStore, DonationEventHandler eventHandler, DonationAlertsConnectionStatus status) {
        this.configStore = configStore;
        this.eventHandler = eventHandler;
        this.status = status;
    }

    public void tick() {
        ClientDonationConfig config = configStore.get();
        if (!config.donationAlertsEnabled) {
            status.setDisabled();
            return;
        }
        if (config.donationAlertsToken.isBlank()) {
            status.setMissingToken();
            return;
        }
        if (requestInFlight) {
            return;
        }
        if (ticksUntilNextPoll > 0) {
            ticksUntilNextPoll--;
            return;
        }

        ticksUntilNextPoll = config.pollingIntervalSeconds * 20L;
        requestInFlight = true;
        status.setWorking(backfillDoneThisSession ? "polling..." : "loading history...");
        CompletableFuture
            .supplyAsync(() -> poll(config), executor)
            .whenComplete((result, throwable) -> Minecraft.getInstance().execute(() -> {
                requestInFlight = false;
                if (throwable != null) {
                    LOGGER.warn("DonationAlerts polling failed", throwable);
                    status.setError("polling failed");
                    return;
                }

                if (!result.success()) {
                    status.setError(result.message());
                    return;
                }

                if (!result.history().isEmpty()) {
                    result.history().forEach(eventHandler::acceptHistoricalDonation);
                }
                acceptDonations(result.current());
                status.setOk(result.message());
            }));
    }

    private PollResult poll(ClientDonationConfig config) {
        TokenState tokenState = ensureValidToken(config);
        if (!tokenState.success()) {
            return PollResult.fail(tokenState.message());
        }

        List<Donation> history = List.of();
        int loadedPages = 0;
        if (!backfillDoneThisSession) {
            BackfillResult backfill = fetchBackfill(config);
            if (!backfill.success()) {
                return PollResult.fail(backfill.message());
            }
            history = backfill.donations();
            loadedPages = backfill.loadedPages();
            backfillDoneThisSession = true;
        }

        FetchResult current = fetchDonations(config, 1, PAGE_LIMIT);
        if (!current.success() && current.statusCode() == 401 && canRefresh(config)) {
            DonationAlertsOAuthService.Result refresh = DonationAlertsOAuthService.refreshBlocking(config);
            if (refresh.success()) {
                configStore.save();
                current = fetchDonations(config, 1, PAGE_LIMIT);
            }
        }
        if (!current.success()) {
            return PollResult.fail(current.message());
        }

        String message = loadedPages > 0
            ? "ok, history pages: " + loadedPages
            : "ok, latest donations: " + current.donations().size();
        return PollResult.ok(current.donations(), history, message);
    }

    private TokenState ensureValidToken(ClientDonationConfig config) {
        if (config.donationAlertsTokenExpiresAt <= 0L) {
            return TokenState.ok();
        }
        if (System.currentTimeMillis() + REFRESH_GRACE_MILLIS < config.donationAlertsTokenExpiresAt) {
            return TokenState.ok();
        }
        if (!canRefresh(config)) {
            return TokenState.fail("token expires soon, refresh data missing");
        }

        DonationAlertsOAuthService.Result refresh = DonationAlertsOAuthService.refreshBlocking(config);
        if (refresh.success()) {
            configStore.save();
            return TokenState.ok();
        }
        return TokenState.fail(refresh.message());
    }

    private BackfillResult fetchBackfill(ClientDonationConfig config) {
        List<Donation> donations = new ArrayList<>();
        int loadedPages = 0;
        for (int page = 1; page <= config.donationAlertsBackfillPages; page++) {
            FetchResult result = fetchDonations(config, page, PAGE_LIMIT);
            if (!result.success()) {
                return BackfillResult.fail(result.message());
            }
            if (result.donations().isEmpty()) {
                break;
            }

            donations.addAll(result.donations());
            loadedPages = page;
            if (result.donations().size() < PAGE_LIMIT) {
                break;
            }
        }
        return BackfillResult.ok(donations, loadedPages);
    }

    private FetchResult fetchDonations(ClientDonationConfig config, int page, int limit) {
        try {
            URI uri = buildUri(config.donationAlertsApiUrl, page, limit);
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.donationAlertsToken)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("DonationAlerts returned HTTP {}", response.statusCode());
                return FetchResult.fail(response.statusCode(), "HTTP " + response.statusCode());
            }

            return FetchResult.ok(parseDonations(response.body()));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Failed to fetch DonationAlerts donations", exception);
            return FetchResult.fail(0, "network/API error");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return FetchResult.fail(0, "polling interrupted");
        }
    }

    private void acceptDonations(List<Donation> donations) {
        if (donations.isEmpty()) {
            return;
        }

        ClientDonationConfig config = configStore.get();
        String lastSeenId = config.lastSeenDonationId;
        String newestId = donations.getFirst().id();

        if (lastSeenId.isBlank()) {
            donations.forEach(eventHandler::acceptHistoricalDonation);
            config.lastSeenDonationId = newestId;
            configStore.save();
            return;
        }

        List<Donation> newDonations = new ArrayList<>();
        for (Donation donation : donations) {
            if (donation.id().equals(lastSeenId)) {
                break;
            }
            newDonations.add(donation);
        }

        if (!newDonations.isEmpty()) {
            Collections.reverse(newDonations);
            newDonations.forEach(eventHandler::acceptNewDonation);
            config.lastSeenDonationId = newestId;
            configStore.save();
        }

        donations.forEach(eventHandler::acceptHistoricalDonation);
    }

    private static boolean canRefresh(ClientDonationConfig config) {
        return !config.donationAlertsRefreshToken.isBlank()
            && !config.donationAlertsClientId.isBlank()
            && !config.donationAlertsClientSecret.isBlank();
    }

    private static URI buildUri(String rawUrl, int page, int limit) {
        String separator = rawUrl.contains("?") ? "&" : "?";
        return URI.create(rawUrl
            + separator
            + "limit=" + URLEncoder.encode(Integer.toString(limit), StandardCharsets.UTF_8)
            + "&page=" + URLEncoder.encode(Integer.toString(page), StandardCharsets.UTF_8));
    }

    private static List<Donation> parseDonations(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray data = root.getAsJsonArray("data");
        if (data == null) {
            return List.of();
        }

        List<Donation> donations = new ArrayList<>();
        for (JsonElement element : data) {
            if (element.isJsonObject()) {
                Donation.fromJson(element.getAsJsonObject()).ifPresent(donations::add);
            }
        }
        return donations;
    }

    private record TokenState(boolean success, String message) {
        static TokenState ok() {
            return new TokenState(true, "");
        }

        static TokenState fail(String message) {
            return new TokenState(false, message);
        }
    }

    private record FetchResult(boolean success, int statusCode, List<Donation> donations, String message) {
        static FetchResult ok(List<Donation> donations) {
            return new FetchResult(true, 200, donations, "");
        }

        static FetchResult fail(int statusCode, String message) {
            return new FetchResult(false, statusCode, List.of(), message);
        }
    }

    private record BackfillResult(boolean success, List<Donation> donations, int loadedPages, String message) {
        static BackfillResult ok(List<Donation> donations, int loadedPages) {
            return new BackfillResult(true, donations, loadedPages, "");
        }

        static BackfillResult fail(String message) {
            return new BackfillResult(false, List.of(), 0, message);
        }
    }

    private record PollResult(boolean success, List<Donation> current, List<Donation> history, String message) {
        static PollResult ok(List<Donation> current, List<Donation> history, String message) {
            return new PollResult(true, current, history, message);
        }

        static PollResult fail(String message) {
            return new PollResult(false, List.of(), List.of(), message);
        }
    }
}
