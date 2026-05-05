package com.twilightfoxy.donatevents;

import com.twilightfoxy.donatevents.client.DonatEventsClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(DonatEvents.MOD_ID)
public final class DonatEvents {
    public static final String MOD_ID = "donat_events";

    public DonatEvents(IEventBus modEventBus, ModContainer modContainer) {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            DonatEventsClient.init(modEventBus, modContainer);
        }
    }
}
