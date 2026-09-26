package com.otectus.immersiveapotheosis.minigame;

import java.util.Objects;

/** Immutable, reproducible description of one station workpiece. */
public record WorkDefinition(Station station, int tier, long seed, double assistance) {
    public static final int VERSION = 2;
    public static final int STEP_MS = 20;
    public static final int COUNT_IN_STEPS = 150;
    public static final int MAX_STEPS = 3000;

    public WorkDefinition {
        Objects.requireNonNull(station, "station");
        if (tier < 0 || tier > 5) throw new IllegalArgumentException("tier must be 0..5");
        if (!Double.isFinite(assistance) || assistance < 0.5 || assistance > 2.0) {
            throw new IllegalArgumentException("assistance must be 0.5..2.0");
        }
    }
}
