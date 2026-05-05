package com.twilightfoxy.donatevents.client.overlay;

import com.twilightfoxy.donatevents.client.config.ClientConfigStore;
import com.twilightfoxy.donatevents.client.config.ClientDonationConfig;
import com.twilightfoxy.donatevents.client.donation.DonationEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import java.util.function.Supplier;

public final class TopDonatorOverlay {
    public static final int MIN_DRAG_WIDTH = 96;
    public static final int HEIGHT = 18;
    private static final int MAX_TEXT_WIDTH = 220;

    private final ClientConfigStore configStore;
    private final Supplier<DonationEventHandler.TopDonation> topDonationSupplier;

    public TopDonatorOverlay(ClientConfigStore configStore, Supplier<DonationEventHandler.TopDonation> topDonationSupplier) {
        this.configStore = configStore;
        this.topDonationSupplier = topDonationSupplier;
    }

    public void render(GuiGraphicsExtractor graphics) {
        ClientDonationConfig config = configStore.get();
        if (!config.overlayEnabled) {
            return;
        }
        renderText(
            graphics,
            Minecraft.getInstance().font,
            config.overlayX,
            config.overlayY,
            config.overlayScale,
            topDonationSupplier.get(),
            config.overlayTopDonatorText,
            config.overlayNoDonationsText
        );
    }

    public static void renderText(
        GuiGraphicsExtractor graphics,
        Font font,
        int x,
        int y,
        float scale,
        DonationEventHandler.TopDonation topDonation,
        String topDonatorText,
        String noDonationsText
    ) {
        String text = displayText(font, topDonation, topDonatorText, noDonationsText);
        if (text.isBlank()) {
            return;
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, ARGB.color(255, 255, 221, 94), true);
        graphics.pose().popMatrix();
    }

    public static String displayText(Font font, DonationEventHandler.TopDonation topDonation, String topDonatorText, String noDonationsText) {
        String text;
        if (topDonation.isEmpty()) {
            text = noDonationsText == null ? "" : noDonationsText.strip();
        } else {
            String template = topDonatorText == null || topDonatorText.isBlank() ? "{name} - {amount}" : topDonatorText;
            text = template
                .replace("{name}", topDonation.username())
                .replace("{amount}", topDonation.amountText());
        }

        if (!text.isBlank() && font.width(text) > MAX_TEXT_WIDTH) {
            return font.plainSubstrByWidth(text, MAX_TEXT_WIDTH - font.width("...")) + "...";
        }
        return text;
    }

    public static int displayWidth(Font font, DonationEventHandler.TopDonation topDonation, String topDonatorText, String noDonationsText) {
        String text = displayText(font, topDonation, topDonatorText, noDonationsText);
        if (text.isBlank()) {
            return 0;
        }
        return Math.max(MIN_DRAG_WIDTH, font.width(text));
    }
}
