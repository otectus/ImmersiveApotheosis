package com.otectus.immersiveapotheosis.minigame;

import java.util.List;

public record CuttingState(Station station, int elapsedSteps, WorkStage stage, String guidance,
                           double progress, double damage, WorkPoint cursor, WorkPoint gemCenter,
                           int gemRotation, int targetRotation, double heat, int activeFacet, List<Facet> facets)
        implements WorkState {
    public CuttingState { facets = List.copyOf(facets); }

    public record Facet(int index, int angle, WorkPoint contact, double targetDepth,
                        double depth, double polish, int polishMask, boolean locked) {}
}
