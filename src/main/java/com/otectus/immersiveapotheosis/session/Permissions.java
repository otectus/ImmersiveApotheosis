package com.otectus.immersiveapotheosis.session;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

@Mod.EventBusSubscriber(modid = ImmersiveApotheosis.MOD_ID)
public final class Permissions {
    /** Players with this node use the stations instantly when {@code allowPermissionBypass} is on. Default: nobody. */
    public static final PermissionNode<Boolean> BYPASS = new PermissionNode<>(ImmersiveApotheosis.MOD_ID, "bypass_performances",
            PermissionTypes.BOOLEAN, (player, uuid, context) -> false);

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(BYPASS);
    }

    /** Whether this player uses Apotheosis's stations instantly. */
    public static boolean bypasses(ServerPlayer player) {
        if (player.getAbilities().instabuild && ServerConfig.get(ServerConfig.CREATIVE_BYPASS)) return true;
        return ServerConfig.get(ServerConfig.ALLOW_PERMISSION_BYPASS) && PermissionAPI.getPermission(player, BYPASS);
    }

    private Permissions() {}
}
