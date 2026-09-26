package com.otectus.immersiveapotheosis.station;

import com.otectus.immersiveapotheosis.config.ServerConfig;
import com.otectus.immersiveapotheosis.minigame.Station;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemItem;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.world.item.ItemStack;

/** Shared station rules: which stations are performed, and a stack's tier (its Apotheosis rarity, 0 if none). */
public final class StationRules {
    /** Set while a station replays Apotheosis's own action after a performance, so it is not intercepted again. */
    private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);

    public static boolean performed(Station station) {
        return ServerConfig.get(switch (station) {
            case SALVAGING -> ServerConfig.PERFORMED_SALVAGING;
            case SOCKETING -> ServerConfig.PERFORMED_SOCKETING;
            case GEM_CUTTING -> ServerConfig.PERFORMED_GEM_CUTTING;
            case REFORGING -> ServerConfig.PERFORMED_REFORGING;
        });
    }

    public static boolean replaying() {
        return REPLAYING.get();
    }

    /** Runs Apotheosis's own action without interception. */
    public static void replay(Runnable action) {
        REPLAYING.set(true);
        try {
            action.run();
        } finally {
            REPLAYING.set(false);
        }
    }

    /** The stack's rarity ordinal: an affix item's rarity, a gem's rarity, or 0. */
    public static int tier(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.getItem() instanceof GemItem) {
            DynamicHolder<?> rarity = GemInstance.unsocketed(stack).rarity();
            return rarity.isBound() ? ordinal(rarity) : 0;
        }
        DynamicHolder<?> rarity = AffixHelper.getRarity(stack);
        return rarity != null && rarity.isBound() ? ordinal(rarity) : 0;
    }

    private static int ordinal(DynamicHolder<?> holder) {
        return holder.get() instanceof dev.shadowsoffire.apotheosis.adventure.loot.LootRarity r ? Math.max(0, Math.min(5, r.ordinal())) : 0;
    }

    static boolean sameItems(ItemStack[] expected, java.util.function.IntFunction<ItemStack> current) {
        for (int i = 0; i < expected.length; i++) {
            if (!ItemStack.matches(expected[i], current.apply(i))) return false;
        }
        return true;
    }

    private StationRules() {}
}
