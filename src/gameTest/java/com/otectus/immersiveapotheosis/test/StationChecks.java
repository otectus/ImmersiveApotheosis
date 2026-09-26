package com.otectus.immersiveapotheosis.test;

import com.otectus.immersiveapotheosis.minigame.OutcomeBand;
import com.otectus.immersiveapotheosis.minigame.WorkDefinition;
import com.otectus.immersiveapotheosis.minigame.WorkInput;
import com.otectus.immersiveapotheosis.minigame.WorkAutoplay;
import com.otectus.immersiveapotheosis.network.InputsC2S;
import com.otectus.immersiveapotheosis.network.ResolvedS2C;
import com.otectus.immersiveapotheosis.network.StartS2C;
import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import com.otectus.immersiveapotheosis.station.SalvagingStation;
import com.otectus.immersiveapotheosis.station.StationSlots;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingMenu;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingRecipe;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.cutting.GemCuttingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class StationChecks {
    enum Play { PERFECT, PARTIAL, SILENT }

    static final BlockPos TABLE = new BlockPos(2, 1, 2);

    static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
    }

    static LootRarity rarity(String id) {
        return RarityRegistry.INSTANCE.getValue(new ResourceLocation("apotheosis", id));
    }

    private static void block(GameTestHelper h, String id) {
        h.setBlock(TABLE, ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id)));
    }

    /** Stream real encoded input batches at their corresponding server time. */
    private static void perform(GameTestHelper h, FakePlayer p, Function<WorkDefinition, List<WorkInput>> play, Runnable after) {
        StartS2C start = TestSupport.last(p, StartS2C.class);
        h.assertTrue(start != null, "a performance started");
        List<WorkInput> inputs = play.apply(start.definition());
        h.assertTrue(!inputs.isEmpty(), "the reference player produces a bounded trace");
        for (int from = 0; from < inputs.size(); from += InputsC2S.MAX_FRAMES) {
            int first = from;
            int end = Math.min(inputs.size(), from + InputsC2S.MAX_FRAMES);
            List<WorkInput> batch = List.copyOf(inputs.subList(first, end));
            long ms = (long) (WorkDefinition.COUNT_IN_STEPS + end) * WorkDefinition.STEP_MS;
            h.runAfterDelay(TestSupport.ticks(ms), () -> {
                net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                try {
                    new InputsC2S(start.session(), first, batch).encode(buffer);
                    InputsC2S decoded = InputsC2S.decode(buffer);
                    PerformanceSessions.receive(p, decoded.session(), decoded.firstStep(), decoded.frames());
                } finally {
                    buffer.release();
                }
                if (end == inputs.size()) after.run();
            });
        }
    }

    private static List<WorkInput> play(WorkDefinition definition, Play play) {
        return switch (play) {
            case PERFECT -> WorkAutoplay.perfect(definition);
            case PARTIAL -> WorkAutoplay.partial(definition);
            case SILENT -> java.util.Collections.nCopies(WorkDefinition.MAX_STEPS, WorkInput.IDLE);
        };
    }

    private static int count(FakePlayer p, net.minecraft.world.inventory.AbstractContainerMenu menu, Item item, int fromSlot, int toSlot) {
        int n = p.getInventory().countItem(item);
        for (int i = fromSlot; i < toSlot; i++) if (menu.getSlot(i).getItem().is(item)) n += menu.getSlot(i).getItem().getCount();
        return n;
    }

    // ------------------------------------------------------------------ salvaging

    static void salvage(GameTestHelper h, boolean perfect) {
        FakePlayer p = TestSupport.player(h);
        block(h, "apotheosis:salvaging_table");
        SalvagingMenu menu = new SalvagingMenu(900, p.getInventory(), h.absolutePos(TABLE));
        p.containerMenu = menu;
        int min = 0;
        int max = 0;
        Item material = null;
        for (int i = 0; i < 4; i++) {
            ItemStack sword = TestSupport.reforge(new ItemStack(Items.IRON_SWORD), rarity("rare"), RandomSource.create(i));
            menu.getSlot(i).set(sword);
            SalvagingRecipe recipe = SalvagingMenu.findMatch(h.getLevel(), sword);
            h.assertTrue(recipe != null, "a rare sword salvages");
            for (SalvagingRecipe.OutputData output : recipe.getOutputs()) {
                int[] range = SalvagingMenu.getSalvageCounts(output, sword);
                min += range[0];
                max += range[1];
                material = output.getStack().getItem();
            }
        }
        Item made = material;
        int expected = perfect ? max : min;
        h.assertTrue(menu.clickMenuButton(p, 0), "the salvage button is taken over");
        h.assertTrue(!menu.getSlot(0).getItem().isEmpty(), "nothing is salvaged before the performance");
        if (perfect) {
            perform(h, p, WorkAutoplay::perfect, () -> {
                h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "the swords were salvaged");
                int got = count(p, menu, made, SalvagingStation.OUTPUTS_FROM, SalvagingStation.OUTPUTS_TO);
                h.assertTrue(got == expected, "Perfect yields Apotheosis's maximum " + expected + ", got " + got);
                h.succeed();
            });
        } else {
            StartS2C start = TestSupport.last(p, StartS2C.class);
            h.runAfterDelay(TestSupport.ticks((long) WorkDefinition.COUNT_IN_STEPS * WorkDefinition.STEP_MS) + 4, () -> {
                PerformanceSessions.cancel(p, start.session());
                h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "abandoning still salvages");
                int got = count(p, menu, made, SalvagingStation.OUTPUTS_FROM, SalvagingStation.OUTPUTS_TO);
                h.assertTrue(got == expected, "an abandoned salvage yields Apotheosis's minimum " + expected + ", got " + got);
                h.succeed();
            });
        }
    }

    // ------------------------------------------------------------------ reforging

    private static Map<ResourceLocation, Float> levels(ItemStack stack) {
        Map<ResourceLocation, Float> out = new HashMap<>();
        for (AffixInstance a : AffixHelper.getAffixes(stack).values()) out.put(a.affix().getId(), a.level());
        return out;
    }

    static void reforge(GameTestHelper h, Play play) {
        FakePlayer p = TestSupport.player(h);
        p.experienceLevel = 100;
        block(h, "apotheosis:reforging_table");
        ReforgingMenu menu = new ReforgingMenu(901, p.getInventory(), h.absolutePos(TABLE));
        p.containerMenu = menu;
        menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
        menu.getSlot(1).set(new ItemStack(item("apotheosis:rare_material"), 32));
        menu.getSlot(2).set(new ItemStack(item("apotheosis:gem_dust"), 32));
        int slotId = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.getSlot(i) instanceof ReforgingMenu.ReforgingResultSlot && menu.getSlot(i).hasItem()) {
                slotId = i;
                break;
            }
        }
        h.assertTrue(slotId >= 0, "the table offers reforging choices");
        int choice = slotId;
        ItemStack offered = menu.getSlot(choice).getItem().copy();
        int dust = menu.getSlot(2).getItem().getCount();
        int materials = menu.getSlot(1).getItem().getCount();
        StationSlots.onRequest(p, menu.containerId, choice, 0, ClickType.PICKUP);
        perform(h, p, pattern -> play(pattern, play), () -> {
            ResolvedS2C verdict = TestSupport.last(p, ResolvedS2C.class);
            h.assertTrue(verdict != null && verdict.band() != null, "the server judged the reforge");
            ItemStack received = p.getInventory().items.stream().filter(s -> s.is(Items.IRON_SWORD)).findFirst().orElse(ItemStack.EMPTY);
            if (play == Play.SILENT) {
                h.assertTrue(verdict.band().isFailure(), "silence fails");
                h.assertTrue(menu.getSlot(0).getItem().is(Items.IRON_SWORD) && received.isEmpty(), "a failed reforge leaves the sword on the table");
                h.assertTrue(menu.getSlot(2).getItem().getCount() < dust, "gem dust is charged");
                h.assertTrue(menu.getSlot(1).getItem().getCount() == materials, "rarity materials are kept");
                h.assertTrue(!ItemStack.matches(offered, menu.getSlot(choice).getItem()), "the choices were rolled anew");
            } else {
                h.assertTrue(!received.isEmpty() && menu.getSlot(0).getItem().isEmpty(), "the reforged sword is the player's");
                Map<ResourceLocation, Float> before = levels(offered);
                Map<ResourceLocation, Float> after = levels(received);
                h.assertTrue(before.keySet().equals(after.keySet()), "the same affixes: " + before.keySet() + " / " + after.keySet());
                if (play == Play.PERFECT) {
                    h.assertTrue(before.equals(after), "Perfect keeps the roll exactly");
                } else {
                    h.assertTrue(!verdict.band().grantsFull(), "a partial binding, got " + verdict.band());
                    before.forEach((id, level) -> h.assertTrue(after.get(id) <= level + 1e-6, "never higher: " + id));
                    h.assertTrue(before.entrySet().stream().anyMatch(e -> after.get(e.getKey()) < e.getValue() - 1e-6 || e.getValue() == 0F),
                            "weaker than rolled");
                }
                h.assertTrue(menu.getSlot(2).getItem().getCount() < dust && menu.getSlot(1).getItem().getCount() < materials,
                        "Apotheosis charged its own costs");
            }
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ gem cutting

    private static Gem anyGem() {
        return GemRegistry.INSTANCE.getValues().stream().filter(g -> g.getMinRarity().ordinal() == 0).findFirst().orElseThrow();
    }

    static void cut(GameTestHelper h, boolean perfect) {
        FakePlayer p = TestSupport.player(h);
        block(h, "apotheosis:gem_cutting_table");
        GemCuttingMenu menu = new GemCuttingMenu(902, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(TABLE)));
        p.containerMenu = menu;
        Gem gem = anyGem();
        LootRarity common = rarity("common");
        menu.getSlot(0).set(GemRegistry.createGemStack(gem, common));
        menu.getSlot(1).set(new ItemStack(item("apotheosis:gem_dust"), 64));
        menu.getSlot(2).set(GemRegistry.createGemStack(gem, common));
        menu.getSlot(3).set(new ItemStack(item("apotheosis:common_material"), 16));
        int dustCost = GemCuttingMenu.getDustCost(common);
        h.assertTrue(menu.clickMenuButton(p, 0), "the cut is taken over");
        perform(h, p, pattern -> perfect ? WorkAutoplay.perfect(pattern) : java.util.Collections.nCopies(WorkDefinition.MAX_STEPS, WorkInput.IDLE), () -> {
            ItemStack main = menu.getSlot(0).getItem();
            int dustLeft = menu.getSlot(1).getItem().getCount();
            if (perfect) {
                h.assertTrue(GemInstance.unsocketed(main).rarity().get().ordinal() == 1, "Perfect upgrades to uncommon, got " + main);
                h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Apotheosis consumed the second gem");
                h.assertTrue(dustLeft == 64 - dustCost, "Apotheosis charged its dust");
                h.assertTrue(p.getInventory().countItem(item("apotheosis:gem_dust")) == dustCost / 2, "half the dust comes back");
            } else {
                h.assertTrue(GemInstance.unsocketed(main).rarity().get().ordinal() == 0 && !menu.getSlot(2).getItem().isEmpty(),
                        "both gems survive a failed cut");
                h.assertTrue(dustLeft == 64 - dustCost, "the dust is spent");
                h.assertTrue(menu.getSlot(3).getItem().getCount() == 16 - GemCuttingMenu.STD_MAT_COST, "and Apotheosis's material price");
            }
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ socketing

    static void socket(GameTestHelper h, boolean good) {
        FakePlayer p = TestSupport.player(h);
        h.setBlock(TABLE, Blocks.SMITHING_TABLE);
        SmithingMenu menu = new SmithingMenu(903, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(TABLE)));
        p.containerMenu = menu;
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        SocketHelper.setSockets(sword, 1);
        ItemStack gemStack = ItemStack.EMPTY;
        RandomSource random = RandomSource.create(4);
        for (int i = 0; i < 200 && gemStack.isEmpty(); i++) {
            ItemStack candidate = GemRegistry.createRandomGemStack(random, h.getLevel(), 0F);
            if (GemInstance.socketed(sword, candidate).isValid()) gemStack = candidate;
        }
        h.assertTrue(!gemStack.isEmpty(), "a gem that fits a sword");
        menu.getSlot(1).set(sword);
        menu.getSlot(2).set(gemStack);
        h.assertTrue(menu.getSlot(SmithingMenu.RESULT_SLOT).hasItem(), "the table offers the socketed sword");
        StationSlots.onRequest(p, menu.containerId, SmithingMenu.RESULT_SLOT, 0, ClickType.PICKUP);
        perform(h, p, pattern -> good ? WorkAutoplay.perfect(pattern) : java.util.Collections.nCopies(WorkDefinition.MAX_STEPS, WorkInput.IDLE), () -> {
            ItemStack received = p.getInventory().items.stream().filter(s -> s.is(Items.IRON_SWORD)).findFirst().orElse(ItemStack.EMPTY);
            if (good) {
                h.assertTrue(!received.isEmpty() && SocketHelper.getGems(received).streamValidGems().count() == 1, "the gem is set");
                h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "the gem left the table");
            } else {
                h.assertTrue(received.isEmpty() && menu.getSlot(1).hasItem() && menu.getSlot(2).hasItem(), "a failed setting returns everything");
            }
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ cancel

    static void cancelIsFree(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        block(h, "apotheosis:gem_cutting_table");
        GemCuttingMenu menu = new GemCuttingMenu(904, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(TABLE)));
        p.containerMenu = menu;
        Gem gem = anyGem();
        menu.getSlot(0).set(GemRegistry.createGemStack(gem, rarity("common")));
        menu.getSlot(1).set(new ItemStack(item("apotheosis:gem_dust"), 64));
        menu.getSlot(2).set(GemRegistry.createGemStack(gem, rarity("common")));
        menu.getSlot(3).set(new ItemStack(item("apotheosis:common_material"), 16));
        h.assertTrue(menu.clickMenuButton(p, 0), "the cut is taken over");
        PerformanceSessions.cancel(p, TestSupport.last(p, StartS2C.class).session());
        h.assertTrue(menu.getSlot(1).getItem().getCount() == 64 && menu.getSlot(3).getItem().getCount() == 16, "cancelling in the count-in is free");
        h.assertTrue(PerformanceSessions.get(p).isEmpty(), "the session is over");
        h.succeed();
    }

    private StationChecks() {}
}
