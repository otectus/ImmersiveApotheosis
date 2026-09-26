package com.otectus.immersiveapotheosis.test;

import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.quality.MakersMark;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.registry.ModBlocks;
import dev.shadowsoffire.apotheosis.adventure.AdventureModule;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.util.FakePlayer;

/** Bodies of the Smithing bridge tests; loaded only when Immersive Smithing is present. */
final class SmithingChecks {

    private static ItemStack forged(GameTestHelper h, int forge, int anvil, boolean faulty, boolean marked) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        new QualityData(forge, anvil, faulty).apply(sword);
        if (marked) MakersMark.stamp(sword, TestSupport.player(h));
        return sword;
    }

    /** Socketing, adding sockets, withdrawal and unnaming all run through the smithing table and must stay there. */
    static void apotheosisRecipesSurvive(GameTestHelper h) {
        long apotheosis = h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.SMITHING).stream()
                .filter(r -> r instanceof AdventureModule.ApothSmithingRecipe).count();
        h.assertTrue(apotheosis >= 4, "Apotheosis's socketing, add-socket, withdrawal and unnaming recipes are present, found " + apotheosis);
        h.succeed();
    }

    static void socketFloors(GameTestHelper h) {
        h.assertTrue(SocketHelper.getSockets(forged(h, 95, 95, false, true)) == 2, "marked Masterwork has two sockets");
        h.assertTrue(SocketHelper.getSockets(forged(h, 80, 75, false, true)) == 1, "marked Fine work has one");
        h.assertTrue(SocketHelper.getSockets(forged(h, 60, 60, false, true)) == 0, "Standard work has none");
        h.assertTrue(SocketHelper.getSockets(forged(h, 95, 95, false, false)) == 0, "unmarked work (loot, villagers) has none");
        h.assertTrue(SocketHelper.getSockets(forged(h, 95, 95, true, true)) == 0, "Faulty work has none");
        h.succeed();
    }

    /** Reforging writes max(rolled, getSockets()): a floor must stay 2 where a bonus would climb every time. */
    static void reforgingKeepsTheFloorAndTheSmithsWork(GameTestHelper h) {
        FakePlayer smith = TestSupport.player(h);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        new QualityData(95, 95, false).apply(sword);
        MakersMark.stamp(sword, smith);
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 10; i++) sword = TestSupport.reforge(sword, TestSupport.common(), random);
        h.assertTrue(SocketHelper.getSockets(sword) == 2, "ten common reforges keep exactly the floor, got " + SocketHelper.getSockets(sword));
        h.assertTrue(QualityData.get(sword).map(q -> q.forgeScore() == 95 && q.anvilScore() == 95).orElse(false), "quality survives reforging");
        h.assertTrue(MakersMark.get(sword).map(m -> m.smithId().equals(smith.getUUID())).orElse(false), "so does the Maker's Mark");
        h.succeed();
    }

    static void meltGuard(GameTestHelper h) {
        ItemStack affixed = TestSupport.reforge(new ItemStack(Items.IRON_SWORD), TestSupport.common(), RandomSource.create(3));
        h.assertTrue(ImmersiveSmithingAPI.meltVeto(affixed).isEmpty(), "affixed gear without gems may be melted");
        ItemStack socketed = affixed.copy();
        h.assertTrue(TestSupport.socketAGem(h, socketed), "a gem was socketed for the test");
        h.assertTrue(ImmersiveSmithingAPI.meltVeto(socketed).isPresent(), "socketed gear is vetoed");

        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
        SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
        SmithsForgeBlockEntity.Result[] err = new SmithsForgeBlockEntity.Result[1];
        h.assertTrue(forge.insertMetal(socketed, 1, false, err) == 0 && err[0] == SmithsForgeBlockEntity.Result.VETOED,
                "the forge refuses the socketed sword, got " + err[0]);
        h.assertTrue(forge.insertMetal(affixed, 1, false, err) == 1, "and accepts the one without gems");
        h.succeed();
    }

    private SmithingChecks() {}
}
