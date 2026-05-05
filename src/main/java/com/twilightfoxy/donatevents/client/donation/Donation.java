package com.twilightfoxy.donatevents.client.donation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public record Donation(String id, String username, BigDecimal amount, String currency, String message, Instant createdAt) {
    private static final DateTimeFormatter DONATION_ALERTS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static Optional<Donation> fromJson(JsonObject object) {
        String id = readString(object, "id", "");
        if (id.isBlank()) {
            return Optional.empty();
        }

        String username = readString(object, "username", readString(object, "name", "Anonymous"));
        BigDecimal amount = readAmount(object.get("amount"));
        String currency = readString(object, "currency", "");
        String message = readString(object, "message", "");
        Instant createdAt = readInstant(readString(object, "created_at", ""));

        return Optional.of(new Donation(id, username, amount, currency, message, createdAt));
    }

    public String amountText() {
        String value = amount.stripTrailingZeros().toPlainString();
        return currency.isBlank() ? value : value + " " + currency;
    }

    private static String readString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return element.getAsString();
    }

    private static BigDecimal readAmount(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(element.getAsString());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private static Instant readInstant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(rawValue).toInstant();
        } catch (RuntimeException exception) {
            try {
                return LocalDateTime.parse(rawValue, DONATION_ALERTS_DATE_TIME).atZone(ZoneId.systemDefault()).toInstant();
            } catch (RuntimeException ignored) {
                return Instant.now();
            }
        }
    }
}
