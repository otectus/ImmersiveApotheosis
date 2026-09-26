package com.otectus.immersiveapotheosis.minigame;

import java.util.List;

public record ReforgeState(Station station, int elapsedSteps, WorkStage stage, String guidance,
                           double progress, double damage, WorkPoint cursor, double heat,
                           double heatLow, double heatHigh, double forceTolerance, double hammerCharge,
                           double traceProgress, int activeAnchor, List<Anchor> anchors,
                           List<WorkPoint> sealPath) implements WorkState {
    public ReforgeState {
        anchors = List.copyOf(anchors);
        sealPath = List.copyOf(sealPath);
    }

    public record Anchor(int index, WorkPoint point, double targetForce, double heat,
                         double alignment, boolean heatLocked, boolean struck) {}
}
