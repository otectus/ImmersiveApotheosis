package com.otectus.immersiveapotheosis.minigame;

import java.util.List;

public record SalvageState(Station station, int elapsedSteps, WorkStage stage, String guidance,
                           double progress, double damage, WorkPoint cursor, int activeComponent,
                           List<Component> components)
        implements WorkState {
    public SalvageState { components = List.copyOf(components); }

    public record Component(int index, WorkPoint center, int radius, List<WorkPoint> seam,
                            WorkPoint extractionTarget, double seamProgress, double extractionProgress,
                            boolean removed) {
        public Component { seam = List.copyOf(seam); }
    }
}
