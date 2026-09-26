package com.otectus.immersiveapotheosis.bridge.tanning;

import com.otectus.immersiveapotheosis.bridge.CraftGrades;
import com.otectus.immersiveapotheosis.config.ServerConfig;
import com.otectus.immersivetanning.api.ImmersiveTanningAPI;
import com.otectus.immersivetanning.leather.LeatherworkData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

import java.util.Optional;

/** Immersive Tanning: socket floors for bench leatherwork, and salvaging that yields Binding Leather. */
public final class TanningBridge {

    public static void init() {
        CraftGrades.register("immersive_tanning", ServerConfig.SOCKETS_FROM_TANNING, TanningBridge::grade);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> LeatherSalvage.rewrite(e.getServer().getRecipeManager()));
        // Before the recipe packet (player == null is the whole-server sync), so clients and JEI see the rewrite.
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> {
            if (e.getPlayer() == null) LeatherSalvage.rewrite(e.getPlayerList().getServer().getRecipeManager());
        });
    }

    /**
     * Fine or Masterwork, for leatherwork a player stitched at the bench. Villager leatherwork also carries a crafter's
     * name (MCA villagers have names), so only the bench's record of the player counts.
     */
    static CraftGrades.Grade grade(ItemStack stack) {
        Optional<LeatherworkData> data = ImmersiveTanningAPI.leatherwork(stack);
        if (data.isEmpty() || !ImmersiveTanningAPI.playerMade(stack)) return CraftGrades.Grade.NONE;
        return switch (data.get().quality()) {
            case MASTERWORK -> CraftGrades.Grade.MASTERWORK;
            case FINE -> CraftGrades.Grade.FINE;
            default -> CraftGrades.Grade.NONE;
        };
    }

    private TanningBridge() {}
}
