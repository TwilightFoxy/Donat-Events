package com.twilightfoxy.donatevents.client;

import com.twilightfoxy.donatevents.DonatEvents;
import com.twilightfoxy.donatevents.client.animation.DonationTotemAnimation;
import com.twilightfoxy.donatevents.client.config.ClientConfigStore;
import com.twilightfoxy.donatevents.client.donation.DonationAlertPoller;
import com.twilightfoxy.donatevents.client.donation.DonationAlertsConnectionStatus;
import com.twilightfoxy.donatevents.client.donation.DonationEventHandler;
import com.twilightfoxy.donatevents.client.overlay.TopDonatorOverlay;
import com.twilightfoxy.donatevents.client.screen.DonatEventsConfigScreen;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

public final class DonatEventsClient {
    private static final Identifier TOP_DONATOR_LAYER = Identifier.fromNamespaceAndPath(DonatEvents.MOD_ID, "top_donator_overlay");
    private static final Identifier DONATION_ANIMATION_LAYER = Identifier.fromNamespaceAndPath(DonatEvents.MOD_ID, "donation_totem_animation");

    private static ClientConfigStore configStore;
    private static DonationEventHandler donationEventHandler;
    private static DonationAlertPoller donationAlertPoller;
    private static DonationAlertsConnectionStatus donationAlertsStatus;
    private static TopDonatorOverlay topDonatorOverlay;
    private static DonationTotemAnimation donationTotemAnimation;

    private DonatEventsClient() {
    }

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        configStore = ClientConfigStore.create(FMLPaths.CONFIGDIR.get());
        donationAlertsStatus = new DonationAlertsConnectionStatus();
        donationTotemAnimation = new DonationTotemAnimation(configStore);
        donationEventHandler = new DonationEventHandler(configStore, donationTotemAnimation);
        topDonatorOverlay = new TopDonatorOverlay(configStore, donationEventHandler::topDonation);
        donationAlertPoller = new DonationAlertPoller(configStore, donationEventHandler, donationAlertsStatus);

        modContainer.registerExtensionPoint(
            IConfigScreenFactory.class,
            (container, parent) -> new DonatEventsConfigScreen(parent, configStore, donationEventHandler, donationAlertsStatus)
        );

        modEventBus.addListener(DonatEventsClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(DonatEventsClient::clientTick);
    }

    public static ClientConfigStore configStore() {
        return configStore;
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT, TOP_DONATOR_LAYER, (graphics, deltaTracker) -> topDonatorOverlay.render(graphics));
        event.registerAboveAll(DONATION_ANIMATION_LAYER, donationTotemAnimation::render);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        donationTotemAnimation.tick();
        donationAlertPoller.tick();
    }
}
