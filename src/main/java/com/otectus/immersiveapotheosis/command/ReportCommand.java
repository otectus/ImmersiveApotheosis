package com.otectus.immersiveapotheosis.command;

import com.mojang.brigadier.CommandDispatcher;
import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.bridge.BridgeBootstrap;
import com.otectus.immersiveapotheosis.bridge.tanning.LeatherSalvage;
import com.otectus.immersiveapotheosis.category.ApothicCuriosExclusion;
import com.otectus.immersiveapotheosis.category.JewelryCategories;
import com.otectus.immersiveapotheosis.category.JewelryGemBonuses;
import com.otectus.immersiveapotheosis.minigame.Station;
import com.otectus.immersiveapotheosis.station.StationRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/** {@code /immersiveapotheosis report}: which bridges are active and what they changed. Operators only. */
@Mod.EventBusSubscriber(modid = ImmersiveApotheosis.MOD_ID)
public final class ReportCommand {
    private static final String KEY = "command.immersive_apotheosis.report.";

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("immersiveapotheosis").requires(s -> s.hasPermission(2))
                .then(Commands.literal("report").executes(c -> report(c.getSource()))));
    }

    private static int report(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(KEY + "header", version("apotheosis"), version(ImmersiveApotheosis.MOD_ID)), false);
        for (Map.Entry<String, BridgeBootstrap.Status> e : BridgeBootstrap.status().entrySet()) {
            String status = e.getValue().name().toLowerCase(java.util.Locale.ROOT);
            source.sendSuccess(() -> Component.translatable(KEY + "bridge", e.getKey(), version(e.getKey()),
                    Component.translatable(KEY + "status." + status)), false);
        }
        source.sendSuccess(() -> Component.translatable(KEY + "jewelry", JewelryCategories.categories().size(),
                JewelryGemBonuses.status(), ApothicCuriosExclusion.status().name().toLowerCase(java.util.Locale.ROOT)), false);
        StringBuilder stations = new StringBuilder();
        for (Station station : Station.values()) {
            if (!stations.isEmpty()) stations.append(", ");
            stations.append(station.id).append(StationRules.performed(station) ? " performed" : " instant");
        }
        source.sendSuccess(() -> Component.translatable(KEY + "stations", stations.toString()), false);
        if (BridgeBootstrap.active("immersive_tanning")) {
            source.sendSuccess(() -> Component.translatable(KEY + "salvage", LeatherSalvage.rewritten().size(),
                    LeatherSalvage.rewritten().toString()), false);
        }
        return 1;
    }

    private static String version(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getVersion().toString()).orElse("-");
    }

    private ReportCommand() {}
}
