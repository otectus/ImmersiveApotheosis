package com.otectus.immersiveapotheosis.test;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The performed stations on real Apotheosis tables and menus, through the real interception (the table's button, or
 * the result-slot request) and the real session manager, with reference inputs. Only the network is replaced.
 */
@GameTestHolder(ImmersiveApotheosis.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationGameTests {
    private static final int RUN = 1400;

    @GameTest(template = "empty", timeoutTicks = RUN)
    public static void perfectSalvageYieldsApotheosisMaximum(GameTestHelper h) {
        StationChecks.salvage(h, true);
    }

    @GameTest(template = "empty", timeoutTicks = RUN)
    public static void abandonedSalvageYieldsApotheosisMinimum(GameTestHelper h) {
        StationChecks.salvage(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = RUN)
    public static void perfectReforgeKeepsTheRoll(GameTestHelper h) {
        StationChecks.reforge(h, StationChecks.Play.PERFECT);
    }

    @GameTest(template = "empty", timeoutTicks = RUN)
    public static void partialReforgeOnlyWeakensTheRoll(GameTestHelper h) {
        StationChecks.reforge(h, StationChecks.Play.PARTIAL);
    }

    @GameTest(template = "empty", timeoutTicks = RUN)
    public static void failedReforgeChargesDustAndRerolls(GameTestHelper h) {
        StationChecks.reforge(h, StationChecks.Play.SILENT);
    }

    @GameTest(template = "empty", timeoutTicks = RUN)
    public static void perfectCutUpgradesAndRefundsDust(GameTestHelper h) {
        StationChecks.cut(h, true);
    }

    @GameTest(template = "empty", timeoutTicks = RUN)
    public static void failedCutKeepsBothGems(GameTestHelper h) {
        StationChecks.cut(h, false);
    }

    @GameTest(template = "empty", timeoutTicks = RUN)
    public static void goodSettingSocketsTheGem(GameTestHelper h) {
        StationChecks.socket(h, true);
    }

    @GameTest(template = "empty", timeoutTicks = RUN)
    public static void failedSettingReturnsTheGem(GameTestHelper h) {
        StationChecks.socket(h, false);
    }

    @GameTest(template = "empty")
    public static void cancellingDuringTheCountInIsFree(GameTestHelper h) {
        StationChecks.cancelIsFree(h);
    }
}
