package com.otectus.immersiveapotheosis.minigame;

/** Authoritative station outcome. Incomplete includes abandonment and timeout. */
public record WorkResult(double score, OutcomeBand band, boolean complete) {
    public WorkResult {
        if (!Double.isFinite(score) || score < 0.0 || score > 100.0) {
            throw new IllegalArgumentException("score must be 0..100");
        }
        if (band == null || band != OutcomeBand.of(score)) {
            throw new IllegalArgumentException("band must match score");
        }
    }

    public static WorkResult of(double score, boolean complete) {
        double bounded = Math.max(0.0, Math.min(100.0, score));
        return new WorkResult(bounded, OutcomeBand.of(bounded), complete);
    }
}
