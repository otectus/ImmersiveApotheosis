package com.otectus.immersiveapotheosis.minigame;

/** The family's outcome bands (as in Immersive Enchanting), from a 0-100 score. */
public enum OutcomeBand {
    PERFECT("perfect"), STABLE("stable"), FRAYED("frayed"), WEAK("weak"), FAILED("failed"), SHATTERED("shattered");

    public final String id;

    OutcomeBand(String id) {
        this.id = id;
    }

    public static OutcomeBand of(double score) {
        if (score >= 90) return PERFECT;
        if (score >= 80) return STABLE;
        if (score >= 65) return FRAYED;
        if (score >= 50) return WEAK;
        if (score >= 35) return FAILED;
        return SHATTERED;
    }

    /** Stable or Perfect: the full result. */
    public boolean grantsFull() {
        return this == PERFECT || this == STABLE;
    }

    /** Failed or Shattered: nothing is applied. */
    public boolean isFailure() {
        return this == FAILED || this == SHATTERED;
    }
}
