package com.twilightfoxy.donatevents.client.screen;

import com.twilightfoxy.donatevents.client.config.ClientConfigStore;
import com.twilightfoxy.donatevents.client.config.ClientDonationConfig;
import com.twilightfoxy.donatevents.client.donation.DonationAlertsConnectionStatus;
import com.twilightfoxy.donatevents.client.donation.DonationAlertsOAuthService;
import com.twilightfoxy.donatevents.client.donation.DonationAlertsTokenVerifier;
import com.twilightfoxy.donatevents.client.donation.DonationEventHandler;
import com.twilightfoxy.donatevents.client.overlay.TopDonatorOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

public final class DonatEventsConfigScreen extends Screen {
    private static final int LEFT = 24;
    private static final int TOP = 38;
    private static final int CLIENT_ID_LABEL_Y = 101;
    private static final int CLIENT_ID_BOX_Y = 116;
    private static final int CLIENT_SECRET_LABEL_Y = 149;
    private static final int CLIENT_SECRET_BOX_Y = 164;
    private static final int TOKEN_LABEL_Y = 197;
    private static final int TOKEN_BOX_Y = 212;
    private static final int TOKEN_ACTION_Y = 236;
    private static final int TOKEN_STATUS_Y = 260;
    private static final int NUMERIC_LABEL_Y = 283;
    private static final int NUMERIC_BOX_Y = 298;
    private static final int TOP_TEXT_LABEL_Y = 331;
    private static final int TOP_TEXT_BOX_Y = 346;
    private static final int EMPTY_TEXT_LABEL_Y = 379;
    private static final int EMPTY_TEXT_BOX_Y = 394;

    private final @Nullable Screen parent;
    private final ClientConfigStore configStore;
    private final DonationEventHandler donationEventHandler;
    private final DonationAlertsConnectionStatus donationAlertsStatus;

    private EditBox clientIdBox;
    private EditBox clientSecretBox;
    private EditBox tokenBox;
    private EditBox intervalBox;
    private EditBox scaleBox;
    private Button topModeButton;
    private EditBox topDonatorTextBox;
    private EditBox noDonationsTextBox;
    private Component tokenCheckStatus = Component.empty();
    private int tokenCheckStatusColor = ARGB.white(180);
    private boolean tokenCheckInProgress;
    private boolean oauthLoginInProgress;
    private boolean draggingOverlay;
    private int dragOffsetX;
    private int dragOffsetY;

    public DonatEventsConfigScreen(
        @Nullable Screen parent,
        ClientConfigStore configStore,
        DonationEventHandler donationEventHandler,
        DonationAlertsConnectionStatus donationAlertsStatus
    ) {
        super(Component.translatable("screen.donat_events.config"));
        this.parent = parent;
        this.configStore = configStore;
        this.donationEventHandler = donationEventHandler;
        this.donationAlertsStatus = donationAlertsStatus;
    }

    @Override
    protected void init() {
        ClientDonationConfig config = configStore.get();
        int fieldWidth = Math.min(260, this.width - 48);
        int placementLeft = Math.max(LEFT, Math.min(this.width - 240, LEFT + fieldWidth + 24));

        addRenderableWidget(Button.builder(toggleText("DonationAlerts", config.donationAlertsEnabled), button -> {
            config.donationAlertsEnabled = !config.donationAlertsEnabled;
            button.setMessage(toggleText("DonationAlerts", config.donationAlertsEnabled));
            configStore.save();
        }).bounds(LEFT, TOP, 160, 20).build());

        addRenderableWidget(Button.builder(toggleText("Overlay", config.overlayEnabled), button -> {
            config.overlayEnabled = !config.overlayEnabled;
            button.setMessage(toggleText("Overlay", config.overlayEnabled));
            configStore.save();
        }).bounds(LEFT + 168, TOP, 120, 20).build());

        addRenderableWidget(Button.builder(toggleText("Chat", config.chatMessagesEnabled), button -> {
            config.chatMessagesEnabled = !config.chatMessagesEnabled;
            button.setMessage(toggleText("Chat", config.chatMessagesEnabled));
            configStore.save();
        }).bounds(LEFT, TOP + 28, 120, 20).build());

        addRenderableWidget(Button.builder(toggleText("Animation", config.animationEnabled), button -> {
            config.animationEnabled = !config.animationEnabled;
            button.setMessage(toggleText("Animation", config.animationEnabled));
            configStore.save();
        }).bounds(LEFT + 128, TOP + 28, 160, 20).build());

        clientIdBox = new EditBox(this.font, LEFT, CLIENT_ID_BOX_Y, 118, 20, Component.translatable("screen.donat_events.client_id"));
        clientIdBox.setMaxLength(32);
        clientIdBox.setValue(config.donationAlertsClientId);
        clientIdBox.setFilter(text -> text.isEmpty() || text.matches("[0-9]+"));
        clientIdBox.setResponder(value -> {
            config.donationAlertsClientId = value;
            configStore.save();
        });
        addRenderableWidget(clientIdBox);

        clientSecretBox = new EditBox(this.font, LEFT, CLIENT_SECRET_BOX_Y, fieldWidth, 20, Component.translatable("screen.donat_events.client_secret"));
        clientSecretBox.setMaxLength(256);
        clientSecretBox.setValue(config.donationAlertsClientSecret);
        clientSecretBox.addFormatter(DonatEventsConfigScreen::maskTokenText);
        clientSecretBox.setResponder(value -> {
            config.donationAlertsClientSecret = value;
            configStore.save();
        });
        addRenderableWidget(clientSecretBox);

        tokenBox = new EditBox(this.font, LEFT, TOKEN_BOX_Y, fieldWidth, 20, Component.translatable("screen.donat_events.token"));
        tokenBox.setMaxLength(512);
        tokenBox.setValue(config.donationAlertsToken);
        tokenBox.addFormatter(DonatEventsConfigScreen::maskTokenText);
        tokenBox.setResponder(value -> {
            config.donationAlertsToken = value;
            configStore.save();
        });
        addRenderableWidget(tokenBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.donat_events.oauth_login"), button -> loginDonationAlerts())
            .bounds(LEFT, TOKEN_ACTION_Y, 150, 20)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("screen.donat_events.check_token"), button -> checkToken())
            .bounds(LEFT + 158, TOKEN_ACTION_Y, 118, 20)
            .build());

        intervalBox = numericBox(LEFT, NUMERIC_BOX_Y, 72, Integer.toString(config.pollingIntervalSeconds), value -> {
            config.pollingIntervalSeconds = parseInt(value, config.pollingIntervalSeconds);
            configStore.save();
        });
        addRenderableWidget(intervalBox);

        scaleBox = numericBox(LEFT + 98, NUMERIC_BOX_Y, 72, Float.toString(config.overlayScale), value -> {
            config.overlayScale = parseFloat(value, config.overlayScale);
            configStore.save();
        });
        addRenderableWidget(scaleBox);

        topModeButton = addRenderableWidget(Button.builder(topModeText(config), button -> {
            config.overlayTopMode = "ALL_TIME".equals(config.overlayTopMode) ? "TODAY" : "ALL_TIME";
            button.setMessage(topModeText(config));
            configStore.save();
        }).bounds(LEFT + 196, NUMERIC_BOX_Y, 128, 20).build());

        addPlacementButton(placementLeft, CLIENT_ID_BOX_Y, Component.translatable("screen.donat_events.position_top_left"), this::placeTopLeft);
        addPlacementButton(placementLeft + 112, CLIENT_ID_BOX_Y, Component.translatable("screen.donat_events.position_top_center"), this::placeTopCenter);
        addPlacementButton(placementLeft, CLIENT_ID_BOX_Y + 26, Component.translatable("screen.donat_events.position_top_right"), this::placeTopRight);
        addPlacementButton(placementLeft + 112, CLIENT_ID_BOX_Y + 26, Component.translatable("screen.donat_events.position_bottom_left"), this::placeBottomLeft);
        addPlacementButton(placementLeft, CLIENT_ID_BOX_Y + 52, Component.translatable("screen.donat_events.position_bottom_right"), this::placeBottomRight);
        addPlacementButton(placementLeft + 112, CLIENT_ID_BOX_Y + 52, Component.translatable("screen.donat_events.position_above_xp"), this::placeAboveExperience);

        topDonatorTextBox = new EditBox(this.font, LEFT, TOP_TEXT_BOX_Y, fieldWidth, 20, Component.translatable("screen.donat_events.top_donator_text"));
        topDonatorTextBox.setMaxLength(128);
        topDonatorTextBox.setValue(config.overlayTopDonatorText);
        topDonatorTextBox.setResponder(value -> {
            config.overlayTopDonatorText = value;
            configStore.save();
        });
        addRenderableWidget(topDonatorTextBox);

        noDonationsTextBox = new EditBox(this.font, LEFT, EMPTY_TEXT_BOX_Y, fieldWidth, 20, Component.translatable("screen.donat_events.no_donations_text"));
        noDonationsTextBox.setMaxLength(96);
        noDonationsTextBox.setValue(config.overlayNoDonationsText);
        noDonationsTextBox.setResponder(value -> {
            config.overlayNoDonationsText = value;
            configStore.save();
        });
        addRenderableWidget(noDonationsTextBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.donat_events.test_donation"), button -> donationEventHandler.triggerTestDonation())
            .bounds(LEFT, this.height - 54, 150, 20)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("screen.donat_events.reset_today"), button -> donationEventHandler.resetTodayDonations())
            .bounds(LEFT + 158, this.height - 54, 150, 20)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
            .bounds(this.width - 174, this.height - 32, 150, 20)
            .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractMenuBackground(graphics);
        graphics.text(this.font, this.title, LEFT, 16, ARGB.color(255, 255, 221, 94), true);
        DonationAlertsConnectionStatus.Snapshot snapshot = donationAlertsStatus.snapshot();
        graphics.text(
            this.font,
            clipped(Component.literal(snapshot.message()), Math.min(360, this.width - LEFT - 8)),
            LEFT,
            80,
            snapshot.ok() ? ARGB.color(255, 98, 255, 148) : ARGB.color(255, 255, 221, 94),
            true
        );
        graphics.text(this.font, Component.translatable("screen.donat_events.client_id"), LEFT, CLIENT_ID_LABEL_Y, ARGB.white(220), true);
        graphics.text(this.font, Component.translatable("screen.donat_events.client_secret"), LEFT, CLIENT_SECRET_LABEL_Y, ARGB.white(220), true);
        graphics.text(this.font, Component.translatable("screen.donat_events.token"), LEFT, TOKEN_LABEL_Y, ARGB.white(220), true);
        graphics.text(this.font, clipped(tokenCheckStatus, Math.min(340, this.width - LEFT - 8)), LEFT, TOKEN_STATUS_Y, tokenCheckStatusColor, true);
        graphics.text(this.font, Component.translatable("screen.donat_events.interval"), LEFT, NUMERIC_LABEL_Y, ARGB.white(220), true);
        graphics.text(this.font, Component.translatable("screen.donat_events.scale"), LEFT + 98, NUMERIC_LABEL_Y, ARGB.white(220), true);
        graphics.text(this.font, Component.translatable("screen.donat_events.top_mode"), LEFT + 196, NUMERIC_LABEL_Y, ARGB.white(220), true);
        graphics.text(this.font, Component.translatable("screen.donat_events.position"), Math.max(LEFT, Math.min(this.width - 240, 308)), CLIENT_ID_LABEL_Y, ARGB.white(220), true);
        graphics.text(this.font, Component.translatable("screen.donat_events.top_donator_text"), LEFT, TOP_TEXT_LABEL_Y, ARGB.white(220), true);
        graphics.text(this.font, Component.translatable("screen.donat_events.no_donations_text"), LEFT, EMPTY_TEXT_LABEL_Y, ARGB.white(220), true);
        graphics.text(this.font, Component.literal("{name}, {amount}"), LEFT, EMPTY_TEXT_BOX_Y + 25, ARGB.color(180, 220, 224, 235), true);
        renderOverlayPreview(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (isOverOverlay(event.x(), event.y())) {
            ClientDonationConfig config = configStore.get();
            draggingOverlay = true;
            dragOffsetX = (int)event.x() - config.overlayX;
            dragOffsetY = (int)event.y() - config.overlayY;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingOverlay) {
            ClientDonationConfig config = configStore.get();
            int previewWidth = overlayWidth(config);
            int previewHeight = Math.round(TopDonatorOverlay.HEIGHT * config.overlayScale);
            config.overlayX = clamp((int)event.x() - dragOffsetX, 0, Math.max(0, this.width - previewWidth));
            config.overlayY = clamp((int)event.y() - dragOffsetY, 0, Math.max(0, this.height - previewHeight));
            configStore.save();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingOverlay) {
            draggingOverlay = false;
            configStore.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        configStore.save();
        this.minecraft.setScreen(parent);
    }

    private void renderOverlayPreview(GuiGraphicsExtractor graphics) {
        ClientDonationConfig config = configStore.get();
        if (!config.overlayEnabled) {
            return;
        }
        TopDonatorOverlay.renderText(
            graphics,
            this.font,
            config.overlayX,
            config.overlayY,
            config.overlayScale,
            donationEventHandler.topDonation(),
            config.overlayTopDonatorText,
            config.overlayNoDonationsText
        );
    }

    private boolean isOverOverlay(double mouseX, double mouseY) {
        ClientDonationConfig config = configStore.get();
        if (!config.overlayEnabled) {
            return false;
        }
        int width = overlayWidth(config);
        if (width <= 0) {
            return false;
        }
        int height = Math.round(TopDonatorOverlay.HEIGHT * config.overlayScale);
        return mouseX >= config.overlayX && mouseX <= config.overlayX + width && mouseY >= config.overlayY && mouseY <= config.overlayY + height;
    }

    private int overlayWidth(ClientDonationConfig config) {
        return Math.round(
            TopDonatorOverlay.displayWidth(this.font, donationEventHandler.topDonation(), config.overlayTopDonatorText, config.overlayNoDonationsText)
                * config.overlayScale
        );
    }

    private int overlayWidthForPlacement(ClientDonationConfig config) {
        return Math.max(Math.round(TopDonatorOverlay.MIN_DRAG_WIDTH * config.overlayScale), overlayWidth(config));
    }

    private int overlayHeight(ClientDonationConfig config) {
        return Math.round(TopDonatorOverlay.HEIGHT * config.overlayScale);
    }

    private void addPlacementButton(int x, int y, Component message, Runnable action) {
        addRenderableWidget(Button.builder(message, button -> {
            action.run();
            configStore.save();
        }).bounds(x, y, 104, 20).build());
    }

    private void placeTopLeft() {
        placeOverlay(8, 8);
    }

    private void placeTopCenter() {
        ClientDonationConfig config = configStore.get();
        placeOverlay((this.width - overlayWidthForPlacement(config)) / 2, 8);
    }

    private void placeTopRight() {
        ClientDonationConfig config = configStore.get();
        placeOverlay(this.width - overlayWidthForPlacement(config) - 8, 8);
    }

    private void placeBottomLeft() {
        ClientDonationConfig config = configStore.get();
        placeOverlay(8, this.height - overlayHeight(config) - 8);
    }

    private void placeBottomRight() {
        ClientDonationConfig config = configStore.get();
        placeOverlay(this.width - overlayWidthForPlacement(config) - 8, this.height - overlayHeight(config) - 8);
    }

    private void placeAboveExperience() {
        ClientDonationConfig config = configStore.get();
        placeOverlay((this.width - overlayWidthForPlacement(config)) / 2, this.height - overlayHeight(config) - 58);
    }

    private void placeOverlay(int x, int y) {
        ClientDonationConfig config = configStore.get();
        config.overlayX = clamp(x, 0, Math.max(0, this.width - overlayWidthForPlacement(config)));
        config.overlayY = clamp(y, 0, Math.max(0, this.height - overlayHeight(config)));
    }

    private void checkToken() {
        if (tokenCheckInProgress) {
            return;
        }

        tokenCheckInProgress = true;
        tokenCheckStatus = Component.translatable("screen.donat_events.checking_token");
        tokenCheckStatusColor = ARGB.color(255, 255, 221, 94);
        configStore.save();

        DonationAlertsTokenVerifier.verify(configStore.get()).whenComplete((result, throwable) -> this.minecraft.execute(() -> {
            if (this.minecraft.screen != this) {
                return;
            }

            tokenCheckInProgress = false;
            if (throwable != null) {
                tokenCheckStatus = Component.literal("Token check failed");
                tokenCheckStatusColor = ARGB.color(255, 255, 96, 96);
                return;
            }

            tokenCheckStatus = Component.literal(result.message());
            tokenCheckStatusColor = result.success() ? ARGB.color(255, 98, 255, 148) : ARGB.color(255, 255, 96, 96);
        }));
    }

    private void loginDonationAlerts() {
        if (oauthLoginInProgress) {
            return;
        }

        oauthLoginInProgress = true;
        tokenCheckStatus = Component.translatable("screen.donat_events.oauth_waiting");
        tokenCheckStatusColor = ARGB.color(255, 255, 221, 94);
        configStore.save();

        DonationAlertsOAuthService.login(configStore.get()).whenComplete((result, throwable) -> this.minecraft.execute(() -> {
            if (this.minecraft.screen != this) {
                return;
            }

            oauthLoginInProgress = false;
            if (throwable != null) {
                tokenCheckStatus = Component.literal("OAuth login failed");
                tokenCheckStatusColor = ARGB.color(255, 255, 96, 96);
                return;
            }

            tokenCheckStatus = Component.literal(result.message());
            tokenCheckStatusColor = result.success() ? ARGB.color(255, 98, 255, 148) : ARGB.color(255, 255, 96, 96);
            if (result.success()) {
                configStore.save();
                tokenBox.setValue(configStore.get().donationAlertsToken);
            }
        }));
    }

    private EditBox numericBox(int x, int y, int width, String value, java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.empty());
        box.setMaxLength(8);
        box.setValue(value);
        box.setFilter(text -> text.isEmpty() || text.matches("[0-9.]+"));
        box.setResponder(responder);
        return box;
    }

    private static Component toggleText(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "ON" : "OFF"));
    }

    private static Component topModeText(ClientDonationConfig config) {
        return "ALL_TIME".equals(config.overlayTopMode)
            ? Component.translatable("screen.donat_events.top_mode_all_time")
            : Component.translatable("screen.donat_events.top_mode_today");
    }

    private Component clipped(Component component, int maxWidth) {
        if (this.font.width(component) <= maxWidth) {
            return component;
        }

        String text = component.getString();
        String clipped = this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("..."))) + "...";
        return Component.literal(clipped);
    }

    private static FormattedCharSequence maskTokenText(String text, int offset) {
        if (text.isEmpty()) {
            return FormattedCharSequence.EMPTY;
        }

        StringBuilder masked = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            masked.append(offset + index < 3 ? text.charAt(index) : '*');
        }
        return FormattedCharSequence.forward(masked.toString(), Style.EMPTY);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
