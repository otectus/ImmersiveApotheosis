package com.otectus.immersiveapotheosis;

import com.mojang.logging.LogUtils;
import com.otectus.immersiveapotheosis.api.ImmersiveApotheosisAPI;
import com.otectus.immersiveapotheosis.bridge.BridgeBootstrap;
import com.otectus.immersiveapotheosis.category.JewelryCategories;
import com.otectus.immersiveapotheosis.category.JewelryGemBonuses;
import com.otectus.immersiveapotheosis.config.ServerConfig;
import com.otectus.immersiveapotheosis.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ImmersiveApotheosis.MOD_ID)
public class ImmersiveApotheosis {
    public static final String MOD_ID = "immersive_apotheosis";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImmersiveApotheosis() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modBus.addListener(this::commonSetup);
        modBus.addListener(ImmersiveApotheosisAPI::processIMC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Before any affix or gem data loads; independent of the sister mods (see JewelryCategories).
            ModNetwork.register();
            JewelryCategories.register();
            JewelryGemBonuses.register();
            BridgeBootstrap.commonSetup();
        });
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
