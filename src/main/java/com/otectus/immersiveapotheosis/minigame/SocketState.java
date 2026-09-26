package com.otectus.immersiveapotheosis.minigame;

import java.util.List;

public record SocketState(Station station, int elapsedSteps, WorkStage stage, String guidance,
                          double progress, double damage, WorkPoint cursor, WorkPoint seat,
                          WorkPoint gemCenter, int gemRotation, int targetRotation, double seating,
                          double tiltX, double tiltY, double pressureLow, double pressureHigh,
                          int activeProng, List<Prong> prongs) implements WorkState {
    public SocketState { prongs = List.copyOf(prongs); }

    public record Prong(int index, WorkPoint hinge, WorkPoint target, double bend, double pressure) {}
}
