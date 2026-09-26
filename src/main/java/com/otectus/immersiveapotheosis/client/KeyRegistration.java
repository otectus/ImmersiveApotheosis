package com.otectus.immersiveapotheosis.client;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the performance key (mod bus, client only). */
@Mod.EventBusSubscriber(modid = ImmersiveApotheosis.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KeyRegistration {
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ClientPerformance.PERFORM);
        event.register(ClientPerformance.CONTEXT);
    }

    private KeyRegistration() {}
}
