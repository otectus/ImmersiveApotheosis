package com.otectus.immersiveapotheosis.station;

import com.otectus.immersiveapotheosis.config.ServerConfig;
import com.otectus.immersiveapotheosis.minigame.WorkResult;
import com.otectus.immersiveapotheosis.minigame.Performance;
import com.otectus.immersiveapotheosis.minigame.Station;
import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import com.otectus.immersiveapotheosis.session.Permissions;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingMenu;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.SlotItemHandler;

/**
 * The Salvaging Table, performed. Apotheosis decides what each item salvages into and each output's count range; the
 * score picks the count inside that range (about 65 gives Apotheosis's mean, Perfect its maximum), never outside it.
 * An abandoned run salvages at the minimum. Batches whose best case yields few items salvage instantly.
 */
public final class SalvagingStation {
    /** Apotheosis's layout: twelve input slots, then six output slots. */
    public static final int INPUTS = 12;
    public static final int OUTPUTS_FROM = 12;
    public static final int OUTPUTS_TO = 18;

    /** At the head of {@code SalvagingMenu#clickMenuButton}; true when a performance takes over the salvage. */
    public static boolean onButton(SalvagingMenu menu, Player player) {
        if (!(player instanceof ServerPlayer sp) || StationRules.replaying() || !StationRules.performed(Station.SALVAGING)) return false;
        if (Permissions.bypasses(sp)) return false;
        if (PerformanceSessions.get(sp).isPresent()) return true;
        ItemStack[] inputs = new ItemStack[INPUTS];
        int best = 0;
        int tier = 0;
        for (int i = 0; i < INPUTS; i++) {
            inputs[i] = menu.getSlot(i).getItem().copy();
            SalvagingRecipe recipe = inputs[i].isEmpty() ? null : SalvagingMenu.findMatch(sp.level(), inputs[i]);
            if (recipe == null) continue;
            for (SalvagingRecipe.OutputData output : recipe.getOutputs()) best += SalvagingMenu.getSalvageCounts(output, inputs[i])[1];
            tier = Math.max(tier, StationRules.tier(inputs[i]));
        }
        if (best < Math.max(1, ServerConfig.get(ServerConfig.SALVAGE_MIN_OUTPUTS))) return false;
        PerformanceSessions.start(sp, menu, new Context(inputs, tier));
        return true;
    }

    /** Salvages every input at the counts {@code score} earns. Returns how many items it produced. */
    public static int salvage(AbstractContainerMenu menu, ServerPlayer player, double score) {
        int made = 0;
        for (int i = 0; i < INPUTS; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            SalvagingRecipe recipe = stack.isEmpty() ? null : SalvagingMenu.findMatch(player.level(), stack);
            if (recipe == null) continue;
            for (SalvagingRecipe.OutputData output : recipe.getOutputs()) {
                int[] range = SalvagingMenu.getSalvageCounts(output, stack);
                int count = Performance.yieldCount(range[0], range[1], score);
                if (count <= 0) continue;
                ItemStack out = output.getStack().copy();
                out.setCount(count);
                made += count;
                deliver(menu, player, out);
            }
            slot.set(ItemStack.EMPTY);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1F, 0.9F);
        return made;
    }

    /** Into the table's output slots first, as Apotheosis does; whatever does not fit goes to the player. */
    private static void deliver(AbstractContainerMenu menu, ServerPlayer player, ItemStack stack) {
        for (int i = OUTPUTS_FROM; i < OUTPUTS_TO && !stack.isEmpty(); i++) {
            if (menu.getSlot(i) instanceof SlotItemHandler output) {
                stack = output.getItemHandler().insertItem(output.getSlotIndex(), stack, false);
            }
        }
        if (!stack.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, stack);
    }

    record Context(ItemStack[] inputs, int tier) implements PerformanceSessions.Context {
        @Override public Station station() { return Station.SALVAGING; }
        @Override public int tier() { return tier; }
        @Override public Component title() { return Component.translatable("performance.immersive_apotheosis.salvaging"); }
        @Override public java.util.List<ItemStack> previews() { return java.util.Arrays.stream(inputs).filter(s -> !s.isEmpty()).map(ItemStack::copy).toList(); }

        @Override
        public boolean stillValid(ServerPlayer player, AbstractContainerMenu menu) {
            return StationRules.sameItems(inputs, i -> menu.getSlot(i).getItem());
        }

        @Override
        public Component resolve(ServerPlayer player, AbstractContainerMenu menu, WorkResult result) {
            return Component.translatable("message.immersive_apotheosis.salvaged", salvage(menu, player, result.score()));
        }

        @Override
        public Component abandon(ServerPlayer player, AbstractContainerMenu menu) {
            return Component.translatable("message.immersive_apotheosis.salvaged_abandoned", salvage(menu, player, 0));
        }
    }

    private SalvagingStation() {}
}
