package com.otectus.immersiveapotheosis.station;

import com.otectus.immersiveapotheosis.config.ServerConfig;
import com.otectus.immersiveapotheosis.minigame.WorkResult;
import com.otectus.immersiveapotheosis.minigame.Station;
import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Optional;

/**
 * Setting a gem at the smithing table, performed. The table decides whether the gem fits (Apotheosis's socketing
 * recipe). A setting of Weak or better sockets exactly as Apotheosis would, through the table's own result slot. A
 * failed setting returns the gem untouched and costs nothing by default (optionally a little durability).
 */
public final class SocketingStation {
    static final int BASE = 1;
    static final int ADDITION = 2;

    public static Optional<PerformanceSessions.Context> capture(ServerPlayer player, SmithingMenu menu, int slotId) {
        if (!menu.getSlot(slotId).hasItem() || !menu.getSlot(slotId).mayPickup(player)) return Optional.empty();
        ItemStack[] inputs = new ItemStack[3];
        for (int i = 0; i < 3; i++) inputs[i] = menu.getSlot(i).getItem().copy();
        return Optional.of(new Context(slotId, inputs, StationRules.tier(inputs[ADDITION])));
    }

    record Context(int slotId, ItemStack[] inputs, int tier) implements PerformanceSessions.Context {
        @Override public Station station() { return Station.SOCKETING; }
        @Override public int tier() { return tier; }
        @Override public Component title() { return Component.translatable("performance.immersive_apotheosis.socketing"); }
        @Override public java.util.List<ItemStack> previews() { return java.util.List.of(inputs[BASE], inputs[ADDITION]); }

        @Override
        public boolean stillValid(ServerPlayer player, AbstractContainerMenu menu) {
            return StationRules.sameItems(inputs, i -> menu.getSlot(i).getItem()) && menu.getSlot(slotId).hasItem();
        }

        @Override
        public Component resolve(ServerPlayer player, AbstractContainerMenu menu, WorkResult result) {
            if (result.band().isFailure()) return failure(player, menu);
            ItemStack socketed = menu.getSlot(slotId).safeTake(1, 1, player);
            if (socketed.isEmpty()) return Component.translatable("message.immersive_apotheosis.performance.changed");
            ItemHandlerHelper.giveItemToPlayer(player, socketed);
            return Component.translatable("message.immersive_apotheosis.socketed");
        }

        @Override
        public Component abandon(ServerPlayer player, AbstractContainerMenu menu) {
            return failure(player, menu);
        }

        private Component failure(ServerPlayer player, AbstractContainerMenu menu) {
            int wear = ServerConfig.get(ServerConfig.SOCKETING_FAILURE_DURABILITY);
            ItemStack base = menu.getSlot(BASE).getItem();
            if (wear > 0 && base.isDamageableItem()) {
                base.hurt(wear, player.getRandom(), player);
                menu.getSlot(BASE).setChanged();
            }
            return Component.translatable("message.immersive_apotheosis.socket_failed");
        }
    }

    private SocketingStation() {}
}
