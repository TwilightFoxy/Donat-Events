package com.twilightfoxy.donatevents.client.donation;

public final class DonationAlertsConnectionStatus {
    private String message = "DonationAlerts: idle";
    private boolean ok;

    public synchronized void setDisabled() {
        this.ok = false;
        this.message = "DonationAlerts: disabled";
    }

    public synchronized void setMissingToken() {
        this.ok = false;
        this.message = "DonationAlerts: token missing";
    }

    public synchronized void setWorking(String action) {
        this.ok = false;
        this.message = "DonationAlerts: " + action;
    }

    public synchronized void setOk(String action) {
        this.ok = true;
        this.message = "DonationAlerts: " + action;
    }

    public synchronized void setError(String action) {
        this.ok = false;
        this.message = "DonationAlerts: " + action;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(ok, message);
    }

    public record Snapshot(boolean ok, String message) {
    }
}
