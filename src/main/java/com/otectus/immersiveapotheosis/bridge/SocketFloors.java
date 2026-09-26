package com.otectus.immersiveapotheosis.bridge;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.config.ServerConfig;
import dev.shadowsoffire.apotheosis.adventure.event.GetItemSocketsEvent;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Master craftsmanship leaves room for gems. Player-made Fine and Masterwork work always has at least the configured
 * number of sockets.
 *
 * <p>This is a floor, never a bonus. Reforging writes {@code max(rolled, getSockets())} back to the item, so an
 * additive bonus would compound on every reforge; a floor is idempotent. Nothing is written to the item: the floor
 * lives only in this event, so changing the config changes every item at once.
 */
@Mod.EventBusSubscriber(modid = ImmersiveApotheosis.MOD_ID)
public final class SocketFloors {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGetSockets(GetItemSocketsEvent event) {
        if (!ServerConfig.get(ServerConfig.SOCKET_FLOORS)) return;
        ItemStack stack = event.getStack();
        if (!stack.hasTag()) return; // every maker's grade lives in NBT
        int floor = floor(CraftGrades.of(stack));
        if (floor <= event.getSockets()) return;
        if (LootCategory.forItem(stack).isNone()) return;
        event.setSockets(floor);
    }

    public static int floor(CraftGrades.Grade grade) {
        return switch (grade) {
            case MASTERWORK -> ServerConfig.get(ServerConfig.MASTERWORK_SOCKET_FLOOR);
            case FINE -> ServerConfig.get(ServerConfig.FINE_SOCKET_FLOOR);
            case NONE -> 0;
        };
    }

    private SocketFloors() {}
}
