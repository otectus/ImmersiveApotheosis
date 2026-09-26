package com.otectus.immersiveapotheosis.test;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The sister-mod bridges on a real server with Apotheosis. A test whose sister mod is absent (-PnoSmithing,
 * -PnoTanning) passes at once, and its checks class, which links that mod, is never loaded.
 */
@GameTestHolder(ImmersiveApotheosis.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BridgeGameTests {

    private static boolean absent(GameTestHelper h, String modId) {
        if (ModList.get().isLoaded(modId)) return false;
        h.succeed();
        return true;
    }

    @GameTest(template = "empty")
    public static void apotheosisSmithingTableRecipesSurviveImmersiveSmithing(GameTestHelper h) {
        if (!absent(h, "immersive_smithing")) SmithingChecks.apotheosisRecipesSurvive(h);
    }

    @GameTest(template = "empty")
    public static void masterworkLeavesRoomForGems(GameTestHelper h) {
        if (!absent(h, "immersive_smithing")) SmithingChecks.socketFloors(h);
    }

    @GameTest(template = "empty")
    public static void theFloorNeverCompoundsThroughReforging(GameTestHelper h) {
        if (!absent(h, "immersive_smithing")) SmithingChecks.reforgingKeepsTheFloorAndTheSmithsWork(h);
    }

    @GameTest(template = "empty")
    public static void theForgeRefusesSocketedGear(GameTestHelper h) {
        if (!absent(h, "immersive_smithing")) SmithingChecks.meltGuard(h);
    }

    @GameTest(template = "empty")
    public static void salvagingYieldsBindingLeather(GameTestHelper h) {
        if (!absent(h, "immersive_tanning")) TanningChecks.leatherSalvage(h);
    }

    @GameTest(template = "empty")
    public static void masterworkLeatherworkLeavesRoomForGems(GameTestHelper h) {
        if (!absent(h, "immersive_tanning")) TanningChecks.socketFloors(h);
    }

    @GameTest(template = "empty")
    public static void jewelryHasCategoriesAndRollsJewelryAffixes(GameTestHelper h) {
        if (!absent(h, "immersive_jewelry")) JewelryChecks.categoriesAndAffixes(h);
    }

    @GameTest(template = "empty")
    public static void wornJewelryAffixesFollowTheWearer(GameTestHelper h) {
        if (!absent(h, "immersive_jewelry")) JewelryChecks.wornAffixesFollowTheWearer(h);
    }

    @GameTest(template = "empty")
    public static void jewelryRarityCapAndDormantAffixes(GameTestHelper h) {
        if (!absent(h, "immersive_jewelry")) JewelryChecks.capsAndDormancy(h);
    }

    @GameTest(template = "empty")
    public static void gemsFitJewelryAndSocketsAreCapped(GameTestHelper h) {
        if (!absent(h, "immersive_jewelry")) JewelryChecks.gemsAndSockets(h);
    }

    /** Runs for real with -PapothicCurios (and Curios); otherwise checks only that no exclusion was claimed. */
    @GameTest(template = "empty")
    public static void apothicCuriosLeavesJewelryToImmersiveJewelry(GameTestHelper h) throws ReflectiveOperationException {
        if (absent(h, "immersive_jewelry")) return;
        if (!ModList.get().isLoaded("apothiccurios") || !ModList.get().isLoaded("curios")) {
            h.assertTrue(com.otectus.immersiveapotheosis.category.ApothicCuriosExclusion.status()
                    != com.otectus.immersiveapotheosis.category.ApothicCuriosExclusion.Status.FAILED, "no ApothicCurios, no failure");
            h.succeed();
            return;
        }
        ApothicCuriosChecks.jewelryLeavesApothicCurios(h);
    }
}
