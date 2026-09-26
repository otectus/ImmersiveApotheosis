package com.otectus.immersiveapotheosis.station;

import com.otectus.immersiveapotheosis.config.ServerConfig;
import com.otectus.immersiveapotheosis.minigame.OutcomeBand;
import com.otectus.immersiveapotheosis.minigame.WorkResult;
import com.otectus.immersiveapotheosis.minigame.Station;
import com.otectus.immersiveapotheosis.mixin.ReforgingMenuInvoker;
import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu;
import dev.shadowsoffire.apotheosis.util.ApothMiscUtil;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import dev.shadowsoffire.placebo.util.EnchantmentUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The Reforging Tables, performed. Apotheosis rolls the three choices; the chosen one is taken through Apotheosis's
 * own slot (which charges materials, gem dust and levels and re-rolls the choices).
 *
 * <ul>
 *   <li>Perfect or Stable keeps the roll exactly.</li>
 *   <li>Frayed or Weak keeps the same affixes and rarity, each at a lower roll within its rarity's range (never
 *   higher).</li>
 *   <li>A failure applies nothing, charges as configured (gem dust and levels by default, keeping the materials) and
 *   re-rolls the choices, so a failed roll cannot be retried.</li>
 * </ul>
 */
public final class ReforgingStation {
    static final int INPUT = 0;
    static final int MATERIALS = 1;
    static final int DUST = 2;

    public static Optional<PerformanceSessions.Context> capture(ServerPlayer player, ReforgingMenu menu, int slotId) {
        Slot slot = menu.getSlot(slotId);
        if (!(slot instanceof ReforgingMenu.ReforgingResultSlot) || !slot.hasItem() || !slot.mayPickup(player)) return Optional.empty();
        int choice = slot.getSlotIndex();
        int tier = menu.getRarity() == null ? 0 : Math.max(0, Math.min(5, menu.getRarity().ordinal()));
        return Optional.of(new Context(slotId, choice, menu.getSlot(INPUT).getItem().copy(), slot.getItem().copy(),
                menu.getSlot(MATERIALS).getItem().copy(), menu.getSlot(DUST).getItem().copy(), tier));
    }

    /** Lowers every affix's roll to {@code keep} of what it was, within the same rarity's range. */
    static void weaken(ItemStack stack, double keep) {
        Map<DynamicHolder<? extends Affix>, AffixInstance> affixes = new LinkedHashMap<>();
        AffixHelper.getAffixes(stack).forEach((affix, instance) -> affixes.put(affix, instance.withNewLevel((float) (instance.level() * keep))));
        AffixHelper.setAffixes(stack, affixes);
    }

    record Context(int slotId, int choice, ItemStack input, ItemStack offered, ItemStack materials, ItemStack dust, int tier) implements PerformanceSessions.Context {
        @Override public Station station() { return Station.REFORGING; }
        @Override public int tier() { return tier; }
        @Override public Component title() { return Component.translatable("performance.immersive_apotheosis.reforging"); }
        @Override public java.util.List<ItemStack> previews() { return java.util.List.of(input, offered); }

        @Override
        public boolean stillValid(ServerPlayer player, AbstractContainerMenu menu) {
            return ItemStack.matches(input, menu.getSlot(INPUT).getItem()) && ItemStack.matches(offered, menu.getSlot(slotId).getItem())
                    && ItemStack.matches(materials, menu.getSlot(MATERIALS).getItem())
                    && ItemStack.matches(dust, menu.getSlot(DUST).getItem());
        }

        @Override
        public Component resolve(ServerPlayer player, AbstractContainerMenu menu, WorkResult result) {
            if (result.band().isFailure()) return failure(player, (ReforgingMenu) menu);
            ItemStack taken = menu.getSlot(slotId).safeTake(1, 1, player);
            if (taken.isEmpty()) return Component.translatable("message.immersive_apotheosis.performance.changed");
            double keep = result.band() == OutcomeBand.FRAYED ? ServerConfig.get(ServerConfig.REFORGE_FRAYED_RETENTION)
                    : result.band() == OutcomeBand.WEAK ? ServerConfig.get(ServerConfig.REFORGE_WEAK_RETENTION) : 1.0;
            if (keep < 1.0) weaken(taken, keep);
            ItemHandlerHelper.giveItemToPlayer(player, taken);
            return Component.translatable(keep < 1.0 ? "message.immersive_apotheosis.reforged_weaker" : "message.immersive_apotheosis.reforged");
        }

        @Override
        public Component abandon(ServerPlayer player, AbstractContainerMenu menu) {
            return failure(player, (ReforgingMenu) menu);
        }

        private Component failure(ServerPlayer player, ReforgingMenu menu) {
            ServerConfig.ReforgeFailureCost cost = ServerConfig.get(ServerConfig.REFORGE_FAILURE_COST);
            if (cost != ServerConfig.ReforgeFailureCost.NONE && !player.getAbilities().instabuild) {
                shrink(menu, DUST, menu.getSigilCost(choice));
                if (cost == ServerConfig.ReforgeFailureCost.FULL) shrink(menu, MATERIALS, menu.getMatCost(choice));
                EnchantmentUtils.chargeExperience(player, ApothMiscUtil.getExpCostForSlot(menu.getLevelCost(choice), choice));
            }
            // A new seed, as after any reforge: the failed choice is gone. updateSeed only loads the seed; the choices are
            // rolled again in slotsChanged, which Apotheosis reaches by emptying the input on a successful take.
            player.getPersistentData().putInt(ReforgingMenu.REFORGE_SEED, player.getRandom().nextInt());
            ((ReforgingMenuInvoker) menu).immersiveapotheosis$updateSeed();
            menu.slotsChanged(new SimpleContainer(0));
            return Component.translatable("message.immersive_apotheosis.reforge_failed");
        }

        private static void shrink(AbstractContainerMenu menu, int slot, int amount) {
            ItemStack stack = menu.getSlot(slot).getItem();
            stack.shrink(Math.min(amount, stack.getCount()));
            menu.getSlot(slot).setChanged();
        }
    }

    private ReforgingStation() {}
}
