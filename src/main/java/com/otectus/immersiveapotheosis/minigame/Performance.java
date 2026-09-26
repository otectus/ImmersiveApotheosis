package com.otectus.immersiveapotheosis.minigame;

/** Economic helpers retained for Apotheosis salvage output ranges. */
public final class Performance {
    /** Salvage yield: the share of an output's [min, max] range a score earns. 65 gives the mean; 100 gives the maximum. */
    public static double yieldShare(double score) {
        return Math.max(0.0, Math.min(1.0, (score - 30.0) / 70.0));
    }

    public static int yieldCount(int min, int max, double score) {
        if (max <= min) return min;
        return min + (int) Math.round((max - min) * yieldShare(score));
    }

    private Performance() {}
}
