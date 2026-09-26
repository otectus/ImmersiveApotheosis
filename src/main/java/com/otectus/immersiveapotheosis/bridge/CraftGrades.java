package com.otectus.immersiveapotheosis.bridge;

import com.otectus.immersiveapotheosis.config.ServerConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * The craft grade of player-made work, as each sister mod labels it. Bridges register one source per mod; the
 * socket floor asks them in turn. A source answers {@link Grade#NONE} for anything it did not make, and must answer
 * quickly, because it runs whenever Apotheosis counts an item's sockets (tooltips included).
 */
public final class CraftGrades {
    public enum Grade { NONE, FINE, MASTERWORK }

    private record Source(String modId, ForgeConfigSpec.BooleanValue enabled, Function<ItemStack, Grade> grade) {}

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    public static void register(String modId, ForgeConfigSpec.BooleanValue enabled, Function<ItemStack, Grade> grade) {
        SOURCES.add(new Source(modId, enabled, grade));
    }

    /** The best grade any enabled source gives the stack. */
    public static Grade of(ItemStack stack) {
        Grade best = Grade.NONE;
        for (Source s : SOURCES) {
            if (!ServerConfig.get(s.enabled())) continue;
            Grade g = s.grade().apply(stack);
            if (g.ordinal() > best.ordinal()) best = g;
        }
        return best;
    }

    private CraftGrades() {}
}
