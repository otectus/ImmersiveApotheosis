package com.otectus.immersiveapotheosis.station;

import com.otectus.immersiveapotheosis.config.ServerConfig;
import com.otectus.immersiveapotheosis.minigame.OutcomeBand;
import com.otectus.immersiveapotheosis.minigame.WorkResult;
import com.otectus.immersiveapotheosis.minigame.Station;
import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import com.otectus.immersiveapotheosis.session.Permissions;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.cutting.GemCuttingMenu;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Gem Cutting Table, performed. Apotheosis decides whether an upgrade is possible and what it costs. Stable or
 * better performs Apotheosis's own upgrade, and Perfect gives some of the gem dust back. Anything less keeps both
 * gems and consumes the configured share of the dust and materials, so the gems themselves are never lost.
 */
public final class GemCuttingStation {
    static final ResourceLocation GEM_DUST = new ResourceLocation("apotheosis", "gem_dust");
    static final int GEM = 0;
    static final int DUST = 1;
    static final int SECOND_GEM = 2;
    static final int MATERIAL = 3;

    /** At the head of {@code GemCuttingMenu#clickMenuButton}; true when a performance takes over the cut. */
    public static boolean onButton(GemCuttingMenu menu, Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || StationRules.replaying() || !StationRules.performed(Station.GEM_CUTTING)) return false;
        if (Permissions.bypasses(sp)) return false;
        if (PerformanceSessions.get(sp).isPresent()) return true;
        ItemStack[] inputs = new ItemStack[4];
        for (int i = 0; i < 4; i++) inputs[i] = menu.getSlot(i).getItem().copy();
        boolean matches = GemCuttingMenu.RECIPES.stream().anyMatch(r -> r.matches(inputs[GEM], inputs[DUST], inputs[SECOND_GEM], inputs[MATERIAL]));
        if (!matches) return false; // Apotheosis refuses it too
        PerformanceSessions.start(sp, menu, new Context(inputs, id, StationRules.tier(inputs[GEM])));
        return true;
    }

    record Context(ItemStack[] inputs, int button, int tier) implements PerformanceSessions.Context {
        @Override public Station station() { return Station.GEM_CUTTING; }
        @Override public int tier() { return tier; }
        @Override public Component title() { return Component.translatable("performance.immersive_apotheosis.gem_cutting"); }
        @Override public java.util.List<ItemStack> previews() { return java.util.List.of(inputs[GEM], inputs[SECOND_GEM], inputs[DUST], inputs[MATERIAL]); }

        @Override
        public boolean stillValid(ServerPlayer player, AbstractContainerMenu menu) {
            return StationRules.sameItems(inputs, i -> menu.getSlot(i).getItem());
        }

        @Override
        public Component resolve(ServerPlayer player, AbstractContainerMenu menu, WorkResult result) {
            if (!result.band().grantsFull()) return failure(player, menu);
            int dust = GemCuttingMenu.getDustCost(rarity(inputs[GEM]));
            StationRules.replay(() -> menu.clickMenuButton(player, button));
            if (result.band() == OutcomeBand.PERFECT) {
                int refund = (int) Math.floor(dust * ServerConfig.get(ServerConfig.CUTTING_PERFECT_DUST_REFUND));
                Item gemDust = ForgeRegistries.ITEMS.getValue(GEM_DUST);
                if (refund > 0 && gemDust != null) ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(gemDust, refund));
                return Component.translatable("message.immersive_apotheosis.cut_perfect", refund);
            }
            return Component.translatable("message.immersive_apotheosis.cut");
        }

        @Override
        public Component abandon(ServerPlayer player, AbstractContainerMenu menu) {
            return failure(player, menu);
        }

        /** Both gems stay; the dust and materials are consumed as configured (as Apotheosis would charge them). */
        private Component failure(ServerPlayer player, AbstractContainerMenu menu) {
            ServerConfig.CuttingFailureCost cost = ServerConfig.get(ServerConfig.CUTTING_FAILURE_COST);
            LootRarity gemRarity = rarity(inputs[GEM]);
            int dust = 0;
            int materials = 0;
            if (cost == ServerConfig.CuttingFailureCost.BOTH || cost == ServerConfig.CuttingFailureCost.DUST) {
                dust = take(menu, DUST, GemCuttingMenu.getDustCost(gemRarity));
            }
            if (cost == ServerConfig.CuttingFailureCost.BOTH || cost == ServerConfig.CuttingFailureCost.MATERIALS) {
                materials = take(menu, MATERIAL, materialCost(menu.getSlot(MATERIAL).getItem(), gemRarity));
            }
            return Component.translatable("message.immersive_apotheosis.cut_failed", dust, materials);
        }

        private static int take(AbstractContainerMenu menu, int slot, int amount) {
            ItemStack stack = menu.getSlot(slot).getItem();
            int taken = Math.min(amount, stack.getCount());
            if (taken > 0) {
                stack.shrink(taken);
                menu.getSlot(slot).setChanged();
            }
            return taken;
        }

        /** Apotheosis's own material price: 3 of the gem's rarity, 1 of the next, 9 of the previous. */
        private static int materialCost(ItemStack material, LootRarity gemRarity) {
            DynamicHolder<LootRarity> holder = RarityRegistry.getMaterialRarity(material.getItem());
            if (holder == null || !holder.isBound()) return GemCuttingMenu.STD_MAT_COST;
            int diff = holder.get().ordinal() - gemRarity.ordinal();
            if (diff > 0) return GemCuttingMenu.NEXT_MAT_COST;
            if (diff < 0) return GemCuttingMenu.PREV_MAT_COST;
            return GemCuttingMenu.STD_MAT_COST;
        }

        private static LootRarity rarity(ItemStack gem) {
            DynamicHolder<LootRarity> holder = GemInstance.unsocketed(gem).rarity();
            return holder.isBound() ? holder.get() : RarityRegistry.getMinRarity().get();
        }
    }

    private GemCuttingStation() {}
}
