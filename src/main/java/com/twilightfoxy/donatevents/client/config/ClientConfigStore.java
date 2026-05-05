package com.twilightfoxy.donatevents.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;

public final class ClientConfigStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private ClientDonationConfig config;

    private ClientConfigStore(Path path, ClientDonationConfig config) {
        this.path = path;
        this.config = config;
    }

    public static ClientConfigStore create(Path configDirectory) {
        Path configPath = configDirectory.resolve("donat_events-client.json");
        ClientConfigStore store = new ClientConfigStore(configPath, new ClientDonationConfig());
        store.load();
        return store;
    }

    public ClientDonationConfig get() {
        return config;
    }

    public void load() {
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    ClientDonationConfig loaded = GSON.fromJson(reader, ClientDonationConfig.class);
                    if (loaded != null) {
                        config = loaded;
                    }
                }
            }
            config.sanitize();
            save();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Failed to load Donat Events client config from {}", path, exception);
            config = new ClientDonationConfig();
            config.sanitize();
        }
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            config.sanitize();
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to save Donat Events client config to {}", path, exception);
        }
    }
}
