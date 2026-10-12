package com.otectus.immersiveapotheosis.api;

import com.otectus.immersiveapotheosis.minigame.Station;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player crafting assistance for the performed stations. A source answers with a multiplier on the assistance
 * window of the player's next performance ({@code 1.0} = the server's configured window, above 1 widens the
 * spatial, pressure, heat and alignment tolerances). Every source is asked when a performance starts, on the server
 * thread; their answers multiply, each clamped to {@code 0.5..2.0}, and the product is clamped again to the same
 * range, which is the range {@code WorkDefinition} accepts. A source that throws counts as {@code 1.0}.
 *
 * <p>Progression mods register one through {@link ImmersiveApotheosisAPI#registerAssistSource}, or by IMC.
 */
@FunctionalInterface
public interface IPerformanceAssistSource {
    double windowMultiplier(ServerPlayer player, Station station);
}
