package com.twilightfoxy.donatevents.client.chat;

import com.twilightfoxy.donatevents.client.donation.Donation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class DonationChatRenderer {
    public void showDonation(Donation donation) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) {
            return;
        }

        MutableComponent message = Component.literal("[DonationAlerts] ")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.literal(donation.username()).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" donated ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(donation.amountText()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

        if (!donation.message().isBlank()) {
            message.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(donation.message()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        minecraft.gui.getChat().addClientSystemMessage(message);
    }
}
