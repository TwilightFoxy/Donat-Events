package com.twilightfoxy.donatevents.client.animation;

import com.twilightfoxy.donatevents.DonatEvents;
import com.twilightfoxy.donatevents.client.config.ClientConfigStore;
import com.twilightfoxy.donatevents.client.donation.Donation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public final class DonationTotemAnimation {
    private static final Identifier DONATION_TEXTURE = Identifier.fromNamespaceAndPath(DonatEvents.MOD_ID, "textures/gui/donation_gift.png");
    private static final int DURATION_TICKS = 90;

    private final ClientConfigStore configStore;
    private Donation donation;
    private int ticksRemaining;

    public DonationTotemAnimation(ClientConfigStore configStore) {
        this.configStore = configStore;
    }

    public void play(Donation donation) {
        if (!configStore.get().animationEnabled) {
            return;
        }
        this.donation = donation;
        this.ticksRemaining = DURATION_TICKS;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 1.0F, 0.85F));
    }

    public void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
            if (ticksRemaining == 0) {
                donation = null;
            }
        }
    }

    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (donation == null || ticksRemaining <= 0) {
            return;
        }

        float age = (DURATION_TICKS - ticksRemaining + deltaTracker.getGameTimeDeltaPartialTick(false)) / DURATION_TICKS;
        float alpha = age < 0.15F ? age / 0.15F : Mth.clamp(ticksRemaining / 20.0F, 0.0F, 1.0F);
        float pulse = 1.0F + Mth.sin(age * Mth.PI * 6.0F) * 0.05F;
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int size = Math.round((72.0F + age * 22.0F) * pulse);
        int x = centerX - size / 2;
        int y = centerY - size / 2 - 26;

        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.colorFromFloat(alpha * 0.16F, 1.0F, 0.54F, 0.18F));
        graphics.blit(DONATION_TEXTURE, x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);

        String text = donation.username() + " - " + donation.amountText();
        int textWidth = Minecraft.getInstance().font.width(text);
        graphics.text(Minecraft.getInstance().font, text, centerX - textWidth / 2, y + size + 10, ARGB.white(alpha), true);
    }
}
