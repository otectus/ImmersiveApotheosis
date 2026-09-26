package com.otectus.immersiveapotheosis.minigame;

/** A point in the station's deterministic 0..4095 work surface. */
public record WorkPoint(int x, int y) {
    public WorkPoint {
        if (x < 0 || x > 4095 || y < 0 || y > 4095) throw new IllegalArgumentException("point outside work surface");
    }
}
