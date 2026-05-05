package com.twilightfoxy.donatevents.client.config;

public final class ClientDonationConfig {
    public boolean donationAlertsEnabled = false;
    public String donationAlertsToken = "";
    public String donationAlertsRefreshToken = "";
    public long donationAlertsTokenExpiresAt = 0L;
    public String donationAlertsClientId = "";
    public String donationAlertsClientSecret = "";
    public String donationAlertsRedirectUri = "http://localhost:17845/donationalerts/callback";
    public String donationAlertsApiUrl = "https://www.donationalerts.com/api/v1/alerts/donations";
    public int pollingIntervalSeconds = 15;
    public int donationAlertsBackfillPages = 20;
    public String lastSeenDonationId = "";

    public boolean overlayEnabled = true;
    public int overlayX = 12;
    public int overlayY = 12;
    public float overlayScale = 1.0F;
    public String overlayTopMode = "TODAY";
    public String overlayTopDonatorText = "Top donator: {name} - {amount}";
    public String overlayNoDonationsText = "No donations today";

    public boolean chatMessagesEnabled = true;
    public boolean animationEnabled = true;
    public String timezone = "Europe/Moscow";

    public void sanitize() {
        if (donationAlertsToken == null) {
            donationAlertsToken = "";
        }
        if (donationAlertsRefreshToken == null) {
            donationAlertsRefreshToken = "";
        }
        if (donationAlertsClientId == null) {
            donationAlertsClientId = "";
        }
        if (donationAlertsClientSecret == null) {
            donationAlertsClientSecret = "";
        }
        if (donationAlertsRedirectUri == null || donationAlertsRedirectUri.isBlank()) {
            donationAlertsRedirectUri = "http://localhost:17845/donationalerts/callback";
        }
        if (donationAlertsApiUrl == null || donationAlertsApiUrl.isBlank()) {
            donationAlertsApiUrl = "https://www.donationalerts.com/api/v1/alerts/donations";
        }
        pollingIntervalSeconds = Math.max(5, Math.min(300, pollingIntervalSeconds));
        donationAlertsBackfillPages = Math.max(1, Math.min(50, donationAlertsBackfillPages));
        overlayX = Math.max(0, overlayX);
        overlayY = Math.max(0, overlayY);
        overlayScale = Math.max(0.5F, Math.min(3.0F, overlayScale));
        if (!"ALL_TIME".equals(overlayTopMode)) {
            overlayTopMode = "TODAY";
        }
        if (overlayTopDonatorText == null || overlayTopDonatorText.isBlank()) {
            overlayTopDonatorText = "Top donator: {name} - {amount}";
        }
        if (overlayNoDonationsText == null) {
            overlayNoDonationsText = "";
        }
        if (timezone == null || timezone.isBlank()) {
            timezone = "Europe/Moscow";
        }
        if (lastSeenDonationId == null) {
            lastSeenDonationId = "";
        }
    }
}
