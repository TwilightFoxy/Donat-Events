package com.twilightfoxy.donatevents.client.donation;

import com.twilightfoxy.donatevents.client.animation.DonationTotemAnimation;
import com.twilightfoxy.donatevents.client.chat.DonationChatRenderer;
import com.twilightfoxy.donatevents.client.config.ClientConfigStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DonationEventHandler {
    private final ClientConfigStore configStore;
    private final DonationTotemAnimation donationTotemAnimation;
    private final DonationChatRenderer chatRenderer = new DonationChatRenderer();
    private final Map<String, BigDecimal> dayTotals = new HashMap<>();
    private final Map<String, BigDecimal> allTimeTotals = new HashMap<>();
    private final Set<String> countedTodayDonationIds = new HashSet<>();
    private final Set<String> countedAllTimeDonationIds = new HashSet<>();

    private LocalDate currentDay;
    private TopDonation topTodayDonation = TopDonation.empty();
    private TopDonation topAllTimeDonation = TopDonation.empty();

    public DonationEventHandler(ClientConfigStore configStore, DonationTotemAnimation donationTotemAnimation) {
        this.configStore = configStore;
        this.donationTotemAnimation = donationTotemAnimation;
        this.currentDay = LocalDate.now(zoneId());
    }

    public void acceptHistoricalDonation(Donation donation) {
        updateTopDonator(donation);
    }

    public void acceptNewDonation(Donation donation) {
        updateTopDonator(donation);
        if (configStore.get().chatMessagesEnabled) {
            chatRenderer.showDonation(donation);
        }
        if (configStore.get().animationEnabled) {
            donationTotemAnimation.play(donation);
        }
    }

    public TopDonation topDonation() {
        resetIfDayChanged();
        return "ALL_TIME".equals(configStore.get().overlayTopMode) ? topAllTimeDonation : topTodayDonation;
    }

    public void triggerTestDonation() {
        Donation donation = new Donation("test-" + System.currentTimeMillis(), "Twily", new BigDecimal("100"), "RUB", "Test donation", java.time.Instant.now());
        acceptNewDonation(donation);
    }

    public void resetTodayDonations() {
        currentDay = LocalDate.now(zoneId());
        dayTotals.clear();
        countedTodayDonationIds.clear();
        topTodayDonation = TopDonation.empty();
    }

    private void updateTopDonator(Donation donation) {
        resetIfDayChanged();
        updateAllTimeTop(donation);
        LocalDate donationDay = donation.createdAt().atZone(zoneId()).toLocalDate();
        if (!donationDay.equals(currentDay)) {
            return;
        }
        if (!countedTodayDonationIds.add(donation.id())) {
            return;
        }

        String key = donation.username() + "\u0000" + donation.currency();
        BigDecimal total = dayTotals.merge(key, donation.amount(), BigDecimal::add);
        if (topTodayDonation.isEmpty() || total.compareTo(topTodayDonation.amount()) > 0) {
            topTodayDonation = new TopDonation(donation.username(), total, donation.currency());
        }
    }

    private void updateAllTimeTop(Donation donation) {
        if (!countedAllTimeDonationIds.add(donation.id())) {
            return;
        }

        String key = donation.username() + "\u0000" + donation.currency();
        BigDecimal total = allTimeTotals.merge(key, donation.amount(), BigDecimal::add);
        if (topAllTimeDonation.isEmpty() || total.compareTo(topAllTimeDonation.amount()) > 0) {
            topAllTimeDonation = new TopDonation(donation.username(), total, donation.currency());
        }
    }

    private void resetIfDayChanged() {
        LocalDate today = LocalDate.now(zoneId());
        if (!today.equals(currentDay)) {
            currentDay = today;
            dayTotals.clear();
            countedTodayDonationIds.clear();
            topTodayDonation = TopDonation.empty();
        }
    }

    private ZoneId zoneId() {
        try {
            return ZoneId.of(configStore.get().timezone);
        } catch (RuntimeException exception) {
            return ZoneId.of("Europe/Moscow");
        }
    }

    public record TopDonation(String username, BigDecimal amount, String currency) {
        public static TopDonation empty() {
            return new TopDonation("", BigDecimal.ZERO, "");
        }

        public boolean isEmpty() {
            return username.isBlank();
        }

        public String amountText() {
            String value = amount.stripTrailingZeros().toPlainString();
            return currency.isBlank() ? value : value + " " + currency;
        }
    }
}
