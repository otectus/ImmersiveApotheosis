package com.otectus.immersiveapotheosis.test;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.api.IPerformanceAssistSource;
import com.otectus.immersiveapotheosis.api.ImmersiveApotheosisAPI;
import com.otectus.immersiveapotheosis.minigame.Station;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The per-player assistance other mods contribute to a performance's window. */
@GameTestHolder(ImmersiveApotheosis.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AssistGameTests {

    @GameTest(template = "empty")
    public static void assistSourcesMultiplyAndClamp(GameTestHelper h) {
        ServerPlayer player = TestSupport.player(h);
        IPerformanceAssistSource mild = (p, s) -> 1.3;
        IPerformanceAssistSource wide = (p, s) -> 1.8;
        IPerformanceAssistSource broken = (p, s) -> { throw new IllegalStateException("broken source"); };
        IPerformanceAssistSource nan = (p, s) -> Double.NaN;
        List<IPerformanceAssistSource> registered = List.of(mild, wide, wide, broken, nan);
        try {
            h.assertTrue(near(ImmersiveApotheosisAPI.assistance(player, Station.SALVAGING), 1.0), "No sources: exactly 1.0");
            ImmersiveApotheosisAPI.registerAssistSource(mild);
            h.assertTrue(near(ImmersiveApotheosisAPI.assistance(player, Station.SALVAGING), 1.3), "One source: its own value");
            ImmersiveApotheosisAPI.registerAssistSource(wide);
            ImmersiveApotheosisAPI.registerAssistSource(wide);
            h.assertTrue(near(ImmersiveApotheosisAPI.assistance(player, Station.REFORGING), 2.0),
                    "1.3 x 1.8 x 1.8 clamps to the widest window WorkDefinition accepts");
            ImmersiveApotheosisAPI.registerAssistSource(broken);
            ImmersiveApotheosisAPI.registerAssistSource(nan);
            h.assertTrue(near(ImmersiveApotheosisAPI.assistance(player, Station.GEM_CUTTING), 2.0),
                    "A throwing or non-finite source counts as 1.0");
        } finally {
            registered.forEach(ImmersiveApotheosisAPI::unregisterAssistSource);
        }
        h.assertTrue(near(ImmersiveApotheosisAPI.assistance(player, Station.SOCKETING), 1.0), "Unregistered sources leave 1.0");
        h.succeed();
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 1e-9;
    }
}
